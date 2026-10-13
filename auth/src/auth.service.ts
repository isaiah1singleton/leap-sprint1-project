import { Injectable, UnauthorizedException, ConflictException } from "@nestjs/common";
import * as bcrypt from "bcrypt";
import * as jwt from "jsonwebtoken";
import { randomBytes } from "crypto";
import { logAuthEvent } from "./logger";
import { InjectRepository } from "@nestjs/typeorm";
import { Repository } from 'typeorm';
import { RegisterDto } from './register.dto';
import { User } from './user/entities/user.entity';

interface StoredUser {
  passwordHash: string;
  roles: string[];
  refreshToken: string | null;
}

const SALT_ROUNDS = 10;

// Sprint 6's real auth-stub reads this from process.env.JWT_SECRET, with
// this exact string as its fallback. This is what lets this service's
// tokens be accepted by the SAME mission service, unchanged, without
// either side's code needing to know about the other.
export const JWT_SECRET = process.env.JWT_SECRET || "mission-control-shared-secret-key-32-bytes-minimum";

@Injectable()
export class AuthService {
  // private readonly users = new Map<string, StoredUser>();

  constructor(
    @InjectRepository(User) private readonly userRepository: Repository<User>
  ) {}

  async register(
    email: string,
    password: string,
    name: string,
    roles: string[] = ["MISSION_OPERATOR"],
  ): Promise<{ email: string; registered: true }> {
    const userExists = await this.userRepository.findOneBy( { email } );
    if (userExists) {
      throw new ConflictException(`${email} is already registered`);
    }
    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    // this.users.set(username, { passwordHash, roles, refreshToken: null });
    const user: User = new User();
    user.email = email;
    user.passwordHash = passwordHash;
    user.name = name;
    this.userRepository.save(user);
    return { email, registered: true };
  }

  async login(username: string, password: string): Promise<{ accessToken: string; refreshToken: string }> {
    const user = this.users.get(username);
    const passwordMatches = user ? await bcrypt.compare(password, user.passwordHash) : false;
    if (!user || !passwordMatches) {
      throw new UnauthorizedException("invalid username or password");
    }
    const accessToken = this.issueAccessToken(username, user.roles);
    const refreshToken = randomBytes(32).toString("hex");
    user.refreshToken = refreshToken;
    logAuthEvent("login_success", username);
    return { accessToken, refreshToken };
  }

  refresh(refreshToken: string): { accessToken: string } {
    const entry = this.findByRefreshToken(refreshToken);
    if (!entry) {
      throw new UnauthorizedException("invalid or expired refresh token");
    }
    const [username, user] = entry;
    return { accessToken: this.issueAccessToken(username, user.roles) };
  }

  private issueAccessToken(username: string, roles: string[]): string {
    return jwt.sign({ sub: username, roles }, JWT_SECRET, {
      algorithm: "HS256",
      expiresIn: "15m",
    });
  }

  private findByRefreshToken(refreshToken: string): [string, StoredUser] | undefined {
    return [...this.users.entries()].find(([, u]) => u.refreshToken === refreshToken);
  }
}
