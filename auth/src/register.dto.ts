import { IsString, IsNotEmpty, MinLength, IsEmail } from "class-validator";
import { ApiProperty } from "@nestjs/swagger";

export class RegisterDto {
  @ApiProperty({ example: "alice@example.com", description: "Users email, must not already be registered" })
  @IsEmail()
  @IsNotEmpty()
  email!: string;

  @ApiProperty({ example: "mission123", minLength: 8, description: "Plain-text password - hashed with bcrypt before storage" })
  @IsString()
  @MinLength(8)
  password!: string;

  @ApiProperty({ example: "Alice", minLength: 3, description: "Name of new user"})
  @IsString()
  @IsNotEmpty()
  name!: string;
}
