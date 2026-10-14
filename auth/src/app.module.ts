import { Module } from "@nestjs/common";
import { AuthController } from "./auth.controller";
import { AuthService } from "./auth.service";
import { HealthController } from "./health.controller";
import { TypeOrmModule } from '@nestjs/typeorm';
import { UserModule } from './user/user.module';
import { User } from './user/entities/user.entity';
import { UserService } from "./user/user.service";

@Module({
  imports: [
    TypeOrmModule.forRoot({
      type: 'postgres',
      host: 'localhost',
      port: 5432,
      password: 'n3u3d4!',
      username: 'postgres',
      entities: [User],
      database: 'testing',
      synchronize: true,
      logging: true,
    }),
    UserModule,
  ],
  controllers: [AuthController, HealthController],
  providers: [AuthService, UserService],
})
export class AppModule {}
