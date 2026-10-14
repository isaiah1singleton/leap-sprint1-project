import { Column, Entity, PrimaryGeneratedColumn } from "typeorm";

@Entity()
export class User {
    @PrimaryGeneratedColumn()
    id!: number;

    @Column({type: 'text'})
    email!: string;

    @Column({type: 'text'})
    name!: string;

    @Column({type: 'text'})
    passwordHash!: string;
}
