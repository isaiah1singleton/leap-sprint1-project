import { Injectable } from '@nestjs/common';
import { CreateUserDto } from './dto/create-user.dto';
// import { UpdateUserDto } from './dto/update-user.dto';
import { User } from './entities/user.entity';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

@Injectable()
export class UserService {

  constructor(
    @InjectRepository(User) private readonly userRepository: Repository<User>,
  ){}


  create(createUserDto: CreateUserDto) {
    const user: User = new User();
    user.name = createUserDto.name;
    user.email = createUserDto.email;
    user.passwordHash = createUserDto.passwordHash;
    return this.userRepository.save(user);
  }

  findAllUser() {
    return this.userRepository.find();
  }

  viewUser(id: number): Promise<User | null> {
    return this.userRepository.findOneBy({id});
  }

  findOneByEmail(email: string): Promise<User | null> {
    return this.userRepository.findOneBy({email});
  }

  // update(id: number, updateUserDto: UpdateUserDto) {
  //   return `This action updates a #${id} user`;
  // }

  // remove(id: number) {
  //   return `This action removes a #${id} user`;
  // }
}
