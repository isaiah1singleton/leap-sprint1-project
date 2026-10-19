import { Column, Entity, JoinColumn, ManyToOne, PrimaryGeneratedColumn} from "typeorm";
import { Session } from "../../session/entities/session.entity";
import { UUID } from "node:crypto";

@Entity()
export class RefreshToken {
    @PrimaryGeneratedColumn('uuid')
    refreshTokenId!: UUID;

    @Column({type: 'text'})
    tokenHash!: string;

    @ManyToOne(() => Session, {eager: true})
    @JoinColumn({name: "sessionId"})
    session!: Session;

    @Column({type: 'timestamptz', default: () => 'CURRENT_TIMESTAMP(6)'})
    createdAt!: Date;

    @Column({type: 'timestamptz', nullable: true})
    usedAt?: Date;
}
