import { Injectable } from '@nestjs/common';
import { CreateSessionDto } from './dto/create-session.dto';
import { UpdateSessionDto } from './dto/update-session.dto';
import { User } from '../user/entities/user.entity';
import { InjectRepository } from '@nestjs/typeorm';
import { Session } from './entities/session.entity';
import { MoreThan, Repository } from 'typeorm';

@Injectable()
export class SessionService {
  constructor(
      @InjectRepository(Session) private readonly sessionRepository: Repository<Session>,
    ){}

  create(createSessionDto: CreateSessionDto) {
    const session = new Session();
    session.user = createSessionDto.user;
    return this.sessionRepository.save(session);
  }

  findOneActiveByUser(user: User){
    const now = new Date();
    return this.sessionRepository.findOneBy(
      {
        user,
        idleExpiresAt: MoreThan(now),
        absoluteExpiresAt: MoreThan(now)
      }
    );
  }
}
