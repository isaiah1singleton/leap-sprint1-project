import json
import os
from pathlib import Path
import subprocess
import tempfile

directory = Path(__file__).resolve().parent

def read_secret(arn):
    result = subprocess.run([
        "aws", "secretsmanager", "get-secret-value",
        "--region", "us-east-1",
        "--secret-id", arn,
        "--version-stage", "AWSCURRENT",
        "--output", "json",
        "--no-cli-pager"
    ], capture_output=True, text=True)
    if result.returncode:
        raise SystemExit(result.stderr)
    return json.loads(json.loads(result.stdout)["SecretString"])

def container_environment(name):
    result = subprocess.run([
        "sudo", "-n", "docker", "inspect", name
    ], capture_output=True, text=True)
    if result.returncode:
        raise SystemExit(result.stderr)
    entries = json.loads(result.stdout)[0]["Config"]["Env"]
    return dict(entry.split("=", 1) for entry in entries if "=" in entry)

metadata = read_secret(
    "arn:aws:secretsmanager:us-east-1:314662805698:"
    "secret:team8/superset/metadata-S7WjBM"
)
runtime = read_secret(
    "arn:aws:secretsmanager:us-east-1:314662805698:"
    "secret:team8/superset/runtime-dvxGzI"
)

if metadata.get("username") != "superset":
    raise SystemExit("Expected metadata username superset.")
if len(runtime.get("SUPERSET_SECRET_KEY", "")) < 32:
    raise SystemExit("Preserved Superset encryption key is missing or invalid.")

# Match SSO to the migrated authentication deployment.
auth = container_environment("backend-auth-1")
backend = container_environment("backend-backend-1")
signing_key = auth.get("SHARED_SECRET")

if not signing_key or signing_key != backend.get("SHARED_SECRET"):
    raise SystemExit("Backend and auth signing secrets are missing or differ.")

environment = {
    "SUPERSET_DB_HOST":
        "team8-superset-db.c8xuasgw2vz5.us-east-1.rds.amazonaws.com",
    "SUPERSET_DB_PASSWORD": metadata["password"],
    "SUPERSET_SECRET_KEY": runtime["SUPERSET_SECRET_KEY"],
    "SNAPSHOT_ID": runtime["SNAPSHOT_ID"],
    "TRADING_AUTH_SHARED_SECRET": signing_key,
    "TRADING_FRONTEND_ORIGINS":
        backend.get("APP_CORS_ALLOWED_ORIGINS")
        or "https://dl7bvz3k1d67g.cloudfront.net"
}

override = {
    "services": {
        "superset": {
            "image": "leap-analytics-superset:rds-migration",
            "environment": {
                key: value.replace("$", "$$")
                for key, value in environment.items()
            }
        }
    }
}

descriptor, filename = tempfile.mkstemp(
    prefix="team8-superset-", suffix=".json", dir="/dev/shm"
)

try:
    with os.fdopen(descriptor, "w") as temporary:
        json.dump(override, temporary)

    subprocess.run([
        "sudo", "-n", "docker", "compose",
        "--project-name", "leap-analytics",
        "--project-directory", str(directory),
        "--env-file", "/dev/null",
        "-f", str(directory / "compose.yml"),
        "-f", filename,
        "--profile", "bi",
        "up", "-d", "--no-deps", "superset"
    ], check=True)
finally:
    os.unlink(filename)

print("Superset container launched. Check its logs and health next.")
