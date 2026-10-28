import { UnauthorizedException, ConflictException } from "@nestjs/common";
import * as bcrypt from "bcrypt";
import * as jwt from "jsonwebtoken";
import { AuthService } from "./auth.service";
import { UserService } from "./user/user.service";
import { SessionService } from "./session/session.service";
import { RefreshTokenService } from "./refresh-token/refresh-token.service";
import { Session } from "./session/entities/session.entity";
import { User } from "./user/entities/user.entity";

jest.mock("bcrypt", () => ({ hash: jest.fn(), compare: jest.fn() }));
jest.mock("./logger", () => ({ logAuthEvent: jest.fn() }));

describe("AuthService", () => {
  const user = Object.assign(new User(), {
    email: "alice@example.test",
    name: "Alice",
    passwordHash: "stored-hash",
    isAdmin: true,
    isAnalyst: false,
  });
  let auth: AuthService;
  let users: { findOneByEmail: jest.Mock; create: jest.Mock };
  let sessions: { create: jest.Mock };
  let refresh: { create: jest.Mock };
  let session: Session;

  beforeEach(() => {
    session = Object.assign(new Session(), {
      user,
      idleExpiresAt: new Date(Date.now() + 60_000),
      absoluteExpiresAt: new Date(Date.now() + 120_000),
    });
    users = {
      findOneByEmail: jest.fn().mockResolvedValue(user),
      create: jest.fn().mockResolvedValue(user),
    };
    sessions = { create: jest.fn().mockResolvedValue(session) };
    refresh = { create: jest.fn().mockResolvedValue({ refreshTokenId: "22222222-2222-4222-8222-222222222222" }) };
    jest.mocked(bcrypt.hash).mockResolvedValue("generated-hash" as never);
    jest.mocked(bcrypt.compare).mockResolvedValue(true as never);
    auth = new AuthService(
      users as unknown as UserService,
      sessions as unknown as SessionService,
      refresh as unknown as RefreshTokenService,
    );
  });

  it("hashes registration passwords before persistence", async () => {
    users.findOneByEmail.mockResolvedValue(null);
    expect(await auth.register("new@example.test", "password1", "New")).toEqual(
      { email: "new@example.test", registered: true },
    );
    expect(bcrypt.hash).toHaveBeenCalledWith("password1", 10);
    expect(users.create).toHaveBeenCalledWith({
      email: "new@example.test",
      name: "New",
      passwordHash: "generated-hash",
    });
  });
  it("rejects duplicates without changing credentials", async () => {
    await expect(
      auth.register(user.email, "password1", "Alice"),
    ).rejects.toBeInstanceOf(ConflictException);
    expect(users.create).not.toHaveBeenCalled();
    expect(bcrypt.hash).not.toHaveBeenCalled();
  });
  it.each([false, null])(
    "rejects wrong passwords or nonexistent users without creating sessions",
    async (exists) => {
      if (exists === null) users.findOneByEmail.mockResolvedValue(null);
      else jest.mocked(bcrypt.compare).mockResolvedValue(false as never);
      await expect(auth.login(user.email, "bad")).rejects.toBeInstanceOf(
        UnauthorizedException,
      );
      expect(sessions.create).not.toHaveBeenCalled();
    },
  );
  it("issues signed short-lived access tokens with roles and stores only a refresh hash", async () => {
    const result = await auth.login(user.email, "password1");
    const claims = jwt.verify(result.accessToken, process.env.SHARED_SECRET!, {
      algorithms: ["HS256"],
    }) as jwt.JwtPayload;
    expect(claims).toMatchObject({
      sub: user.email,
      name: "Alice",
      roles: ["admin"],
    });
    expect(claims.exp! - claims.iat!).toBe(900);
    expect(result.refreshToken.body).toMatch(/^[0-9a-f]{64}$/);
    expect(refresh.create.mock.calls[0][0].tokenHash).toBe("generated-hash");
    expect(refresh.create.mock.calls[0][0].tokenHash).not.toBe(
      result.refreshToken.body,
    );
  });
});
