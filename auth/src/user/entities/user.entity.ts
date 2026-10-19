import { Column, Entity, Index, PrimaryGeneratedColumn } from "typeorm";

@Entity()
@Index(["email"], { unique: true })
export class User {
    @PrimaryGeneratedColumn()
    userId!: number;

    @Column({type: 'text', unique: true})
    email!: string;

    @Column({type: 'text'})
    name!: string;

    @Column({type: 'text'})
    passwordHash!: string;

    @Column({type: 'timestamptz', default: () => 'CURRENT_TIMESTAMP(6)'})
    createdAt!: Date;

    @Column({type: 'boolean', default: false})
    isAdmin!: boolean;

    @Column({type: 'boolean', default: false})
    isAnalyst!: boolean;
}
