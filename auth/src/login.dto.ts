import { IsString, IsNotEmpty } from "class-validator";
import { ApiProperty } from "@nestjs/swagger";

export class LoginDto {
  @ApiProperty({ example: "alice@example.com", description: "Registered email" })
  @IsString()
  @IsNotEmpty()
  email!: string;

  @ApiProperty({ example: "mission123", description: "Plain-text password, checked against the stored bcrypt hash" })
  @IsString()
  @IsNotEmpty()
  password!: string;
}
