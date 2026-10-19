import { Injectable, UnauthorizedException, ConflictException, Inject } from "@nestjs/common";
import * as bcrypt from "bcrypt";
import * as jwt from "jsonwebtoken";
import { randomBytes, randomUUID, UUID } from "crypto";
import { logAuthEvent } from "./logger";
import { InjectRepository } from "@nestjs/typeorm";
import { Repository } from 'typeorm';
import { RegisterDto } from './register.dto';
import { User } from './user/entities/user.entity';
import { UserService } from "./user/user.service";
import { RefreshTokenService } from "./refresh-token/refresh-token.service";
import { SessionService } from "./session/session.service";
import { Session } from "./session/entities/session.entity";
import { log } from "console";

const SALT_ROUNDS = 10;

// Sprint 6's real auth-stub reads this from process.env.JWT_SECRET, with
// this exact string as its fallback. This is what lets this service's
// tokens be accepted by the SAME mission service, unchanged, without
// either side's code needing to know about the other.
export const JWT_SECRET = process.env.JWT_SECRET || "mission-control-shared-secret-key-32-bytes-minimum";

@Injectable()
export class AuthService {
  
  constructor(
    private readonly userService: UserService,
    private readonly sessionService: SessionService,
    private readonly refreshTokenService: RefreshTokenService,
  ) {}

  async register(
    email: string,
    password: string,
    name: string,
  ): Promise<{ email: string; registered: true }> {
    const userExists = await this.userService.findOneByEmail( email );
    if (userExists) {
      throw new ConflictException(`${email} is already registered`);
    }
    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    await this.userService.create({email, name, passwordHash})
    return { email, registered: true };
  }

  async login(email: string, password: string): Promise<{ 
    accessToken: string; refreshToken: {uuid: string, body: string} }> 
    {
    const user = await this.userService.findOneByEmail(email);
    const passwordMatches = user ? await bcrypt.compare(password, user.passwordHash) : false;
    if (!user || !passwordMatches) {
      throw new UnauthorizedException("invalid username or password");
    }

    let roles: string[] = [];
    if (user.isAdmin){
      roles.push("admin");
    }
    if (user.isAnalyst){
      roles.push("analyst");
    }

    const session = await this.sessionService.create({user});

    const accessToken = this.issueAccessToken(email, roles);

    
    const refreshToken = await this.issueRefreshToken(session);

    logAuthEvent("login_succes", email);
    return { accessToken , refreshToken };
  }

async refresh(refreshToken: {uuid: UUID, body: string}): 
  Promise<{accessToken: string, refreshToken: {uuid: UUID, body: string}}> 
  {
    const entry = await this.refreshTokenService.findOne(refreshToken.uuid);
    if (!entry) {
      throw new UnauthorizedException("invalid or expired refresh token");
    }
    const session = entry.session;
    const user = session.user;

    if (entry.usedAt){
      // to-do: need to revoke session
      throw new UnauthorizedException("invalid or expired refresh token");
    }
    await this.refreshTokenService.markAsUsed(refreshToken.uuid);
    
    const bodyMatches = entry ? await bcrypt.compare(refreshToken.body, entry.tokenHash) : false;
    if (!bodyMatches){
      throw new UnauthorizedException("invalid or expired refresh token");
    }
    
    const newRefreshToken = await this.issueRefreshToken(entry.session);
    let roles: string[] = [];
    if (user.isAdmin){
      roles.push("admin");
    }
    if (user.isAnalyst){
      roles.push("analyst");
    }
    const newAccessToken = this.issueAccessToken(user.email, roles);

    return {accessToken: newAccessToken, refreshToken: newRefreshToken};
  }

  private issueAccessToken(email: string, roles: string[]): string {
    return jwt.sign({ sub: email, roles }, JWT_SECRET, {
      algorithm: "HS256",
      expiresIn: "15m",
    });
  }

  private async issueRefreshToken(session: Session){
    const tokenBody = randomBytes(32).toString("hex");
    const tokenHash = await bcrypt.hash(tokenBody, SALT_ROUNDS);
    const refreshTokenEntity = await this.refreshTokenService.create({tokenHash, session});
    const tokenUuid = refreshTokenEntity.refreshTokenId;
    return {uuid: tokenUuid, body: tokenBody};
  }
  
}
