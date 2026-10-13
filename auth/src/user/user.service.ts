import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { RegisterDto } from '../register.dto';
import { User } from './entities/user.entity';

@Injectable()
export class UserService {
  /**
   * Here, we have used data mapper approch for this tutorial that is why we
   * injecting repository here. Another approch can be Active records.
   */
  constructor(
    @InjectRepository(User) private readonly userRepository: Repository<User>,
  ) {}

  /**
   * this is function is used to create User in User Entity.
   * @param RegisterDto this will type of createUserDto in which
   * we have defined what are the keys we are expecting from body
   * @returns promise of user
   */
  createUser(registerDto: RegisterDto): Promise<User> {
    const user: User = new User();
    user.name = registerDto.name;
    user.email = registerDto.email;
    user.password = registerDto.password;
    return this.userRepository.save(user);
  }

  /**
   * this function is used to get all the user's list
   * @returns promise of array of users
   */
  findAllUser(): Promise<User[]> {
    return this.userRepository.find();
  }

  // /**
  //  * this function used to get data of use whose id is passed in parameter
  //  * @param id is type of number, which represent the id of user.
  //  * @returns promise of user
  //  */
  // viewUser(id: number): Promise<User> {
  //   return this.userRepository.findOneBy({ id });
  // }


  // /**
  //  * this function is used to remove or delete user from database.
  //  * @param id is the type of number, which represent id of user
  //  * @returns nuber of rows deleted or affected
  //  */
  // removeUser(id: number): Promise<{ affected?: number }> {
  //   return this.userRepository.delete(id);
  // }
}