import { Module } from "@nestjs/common";
import { AuthController } from "./auth.controller";
import { AuthService } from "./auth.service";
import { HealthController } from "./health.controller";
import { TypeOrmModule } from '@nestjs/typeorm';
import { UserModule } from './user/user.module';
import { User } from './user/entities/user.entity';
import { UserService } from "./user/user.service";
import { SessionModule } from './session/session.module';
import { RefreshTokenModule } from './refresh-token/refresh-token.module';
import { Session } from "./session/entities/session.entity";
import { RefreshToken } from "./refresh-token/entities/refresh-token.entity";
import { SessionService } from "./session/session.service";
import { RefreshTokenService } from "./refresh-token/refresh-token.service";

@Module({
  imports: [
    TypeOrmModule.forRoot({
      type: 'postgres',
      host: 'localhost',
      port: 5432,
      password: 'n3u3d4!',
      username: 'postgres',
      entities: [User, Session, RefreshToken],
      database: 'testing',
      synchronize: true,
      logging: true,
    }),
    UserModule,
    SessionModule,
    RefreshTokenModule,
  ],
  controllers: [AuthController, HealthController],
  providers: [AuthService, UserService, SessionService, RefreshTokenService],
})
export class AppModule {}
