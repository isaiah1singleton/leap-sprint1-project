# Verifies JWT signature and expiry
# Requires analyst role
# Create a separate superset user for that analyst
# Starts a superset session and redirects to dashboard
# Receives JWT through POST body

import os
import time

import jwt
from flask import abort, make_response, redirect, request, session
from flask_login import login_user, logout_user

from superset.extensions import appbuilder, csrf


def configure_analyst_sso(app):
    # Must match SHARED_SECRET in the existing auth service.
    jwt_secret = os.environ["TRADING_AUTH_SHARED_SECRET"]

    allowed_origins = {
        origin.strip()
        for origin in os.environ["TRADING_FRONTEND_ORIGINS"].split(",")
        if origin.strip()
    }

    if not jwt_secret or not allowed_origins:
        raise RuntimeError("Analyst SSO configuration is incomplete")

    @app.post("/trading-sso/login")
    @csrf.exempt
    def trading_sso_login():
        # This endpoint is exempt from Superset's CSRF-token requirement
        # because the incoming browser has no Superset session yet.
        # Instead, require an exact trusted frontend Origin and a valid JWT.
        if request.headers.get("Origin") not in allowed_origins:
            abort(403, description="Untrusted login origin")

        token = request.form.get("access_token", "")

        if not token or len(token) > 8192:
            abort(401, description="Access token required")

        try:
            claims = jwt.decode(
                token,
                jwt_secret,
                algorithms=["HS256"],
                options={
                    "require": ["sub", "iat", "exp"],
                },
            )
        except jwt.InvalidTokenError:
            abort(401, description="Invalid or expired access token")

        email = claims.get("sub")
        roles = claims.get("roles")

        if not isinstance(email, str) or not email.strip():
            abort(401, description="Invalid token identity")

        if (
            not isinstance(roles, list)
            or not all(isinstance(role, str) for role in roles)
        ):
            abort(401, description="Invalid token roles")

        if "analyst" not in {role.lower() for role in roles}:
            abort(403, description="Analyst access required")

        security_manager = appbuilder.sm
        gamma_role = security_manager.find_role("Gamma")

        if gamma_role is None:
            abort(503, description="Run Superset initialization first")

        # A dedicated namespace avoids confusing these users with
        # manually created Superset administrators.
        username = f"trading:{email}"

        # Flask-AppBuilder's default username and email columns are bounded.
        if len(username) > 64 or len(email) > 64:
            abort(400, description="Identity exceeds Superset field limits")

        user = security_manager.find_user(username=username)

        if user is None:
            # Avoid silently linking an unrelated existing Superset account.
            if security_manager.find_user(email=email):
                abort(
                    409,
                    description="Email already belongs to a Superset account",
                )

            user = security_manager.add_user(
                username=username,
                first_name="Trading",
                last_name="Analyst",
                email=email,
                role=gamma_role,
            )

            if not user:
                abort(503, description="Could not provision analyst account")
        else:
            if not user.is_active:
                abort(403, description="Superset account is disabled")

            if user.email != email:
                abort(403, description="Superset identity mismatch")

            # Manage only the dedicated trading SSO accounts.
            # Never map an application role to Superset Admin.
            user.roles = [gamma_role]

            if not security_manager.update_user(user):
                abort(503, description="Could not synchronize analyst roles")

        session.clear()

        if not login_user(user, remember=False, fresh=True):
            abort(403, description="Superset login denied")

        # Bind this Superset session to the access token's expiry.
        session["trading_token_exp"] = claims["exp"]

        response = make_response(
            redirect("/superset/dashboard/leap-br16/", code=303)
        )
        response.headers["Cache-Control"] = "no-store"
        response.headers["Referrer-Policy"] = "no-referrer"
        return response

    @app.before_request
    def enforce_trading_session_expiry():
        # Allow a new token to replace an expired SSO session.
        if request.endpoint == "trading_sso_login":
            return None

        expires_at = session.get("trading_token_exp")

        if expires_at is not None and time.time() >= expires_at:
            logout_user()
            session.clear()
            abort(
                401,
                description="Analyst session expired. Sign in again.",
            )

        return None



