import { Body, Controller, Post, HttpCode } from "@nestjs/common";
import { ApiTags, ApiOperation, ApiResponse } from "@nestjs/swagger";
import { AuthService } from "./auth.service";
import { LoginDto } from "./login.dto";
import { RegisterDto } from "./register.dto";
import { UUID } from "node:crypto";

@ApiTags("auth")
@Controller("auth")
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post("register")
  @ApiOperation({ summary: "Register a new user" })
  @ApiResponse({ status: 201, description: "User registered" })
  @ApiResponse({ status: 409, description: "Email already registered" })
  register(@Body() body: RegisterDto) {
    return this.authService.register(body.email, body.password, body.name);
  }

  @Post("login")
  @HttpCode(200)
  @ApiOperation({ summary: "Log in and receive an access token and a refresh token" })
  @ApiResponse({ status: 200, description: "Login succeeded, tokens issued" })
  @ApiResponse({ status: 401, description: "Invalid email or password" })
  login(@Body() body: LoginDto) {
    return this.authService.login(body.email, body.password);
  }

  @Post("refresh")
  @HttpCode(200)
  @ApiOperation({ summary: "Use refresh token to receive new access token and refresh token" })
  @ApiResponse({ status: 200, description: "Refresh succeeded, tokens issued" })
  @ApiResponse({ status: 401, description: "Invalid refresh token"})
  refresh(@Body() body: {body: string, uuid: UUID}){
    return this.authService.refresh(body)
  }
}
