import { Column, Entity, JoinColumn, ManyToOne, PrimaryGeneratedColumn } from "typeorm";
import { User } from "../../user/entities/user.entity";

@Entity()
export class Session {

    @PrimaryGeneratedColumn()
    sessionId!: number;

    @ManyToOne(() => User, {eager: true})
    @JoinColumn({name: "userId"})
    user!: User;

    @Column({type: 'timestamptz', default: () => 'CURRENT_TIMESTAMP(6)'})
    createdAt!: Date;

    @Column({type: 'timestamptz', default: () => "CURRENT_TIMESTAMP(6) + INTERVAL '15 minutes'"})
    idleExpiresAt!: Date;

    @Column({type: 'timestamptz', default: () => "CURRENT_TIMESTAMP(6) + INTERVAL '12 hours'"})
    absoluteExpiresAt!: Date;
}
