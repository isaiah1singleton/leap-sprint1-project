import { Session } from "../../session/entities/session.entity"

export class CreateRefreshTokenDto {
    session!: Session;
    tokenHash!: string;
}
