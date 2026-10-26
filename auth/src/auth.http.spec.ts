import {
  INestApplication,
  Module,
  UnauthorizedException,
  ValidationPipe,
} from "@nestjs/common";
import { NestFactory } from "@nestjs/core";
import { AddressInfo } from "node:net";
import { AuthController } from "./auth.controller";
import { AuthService } from "./auth.service";

describe("auth HTTP contract with mocked service boundary", () => {
  const service = { register: jest.fn(), login: jest.fn(), refresh: jest.fn() };
  let app: INestApplication;
  let url: string;
  beforeAll(async () => {
    @Module({
      controllers: [AuthController],
      providers: [{ provide: AuthService, useValue: service }],
    })
    class TestModule {}
    app = await NestFactory.create(TestModule, { logger: false });
    app.useGlobalPipes(
      new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true }),
    );
    await app.listen(0, "127.0.0.1");
    url = `http://127.0.0.1:${(app.getHttpServer().address() as AddressInfo).port}`;
  });
  afterAll(async () => {
    await app?.close();
  });
  async function post(path: string, body: object) {
    return fetch(url + path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  }
  it("validates registration and rejects injected role flags before the service", async () => {
    const response = await post("/auth/register", {
      email: "bad",
      password: "short",
      name: "Alice",
      isAdmin: true,
    });
    expect(response.status).toBe(400);
    expect(service.register).not.toHaveBeenCalled();
  });
  it("returns 201 for valid registration without exposing credentials", async () => {
    service.register.mockResolvedValue({
      email: "alice@example.test",
      registered: true,
    });
    const response = await post("/auth/register", {
      email: "alice@example.test",
      name: "Alice",
      password: "password1",
    });
    expect(response.status).toBe(201);
    expect(await response.json()).toEqual({
      email: "alice@example.test",
      registered: true,
    });
    expect(service.register).toHaveBeenCalledWith(
      "alice@example.test",
      "password1",
      "Alice",
    );
  });
  it("uses 401 for invalid login credentials", async () => {
    service.login.mockRejectedValue(
      new UnauthorizedException("invalid username or password"),
    );
    const response = await post("/auth/login", {
      email: "alice@example.test",
      password: "wrong",
    });
    expect(response.status).toBe(401);
  });
  it("returns tokens with 200 on login", async () => {
    service.login.mockResolvedValue({
      accessToken: "signed-token",
      refreshToken: { uuid: "test-id", body: "secret" },
    });
    const response = await post("/auth/login", {
      email: "alice@example.test",
      password: "password1",
    });
    expect(response.status).toBe(200);
    expect(await response.json()).toHaveProperty("accessToken", "signed-token");
  });
});
