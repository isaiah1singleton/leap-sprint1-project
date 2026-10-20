import { Injectable } from '@nestjs/common';
import { CreateRefreshTokenDto } from './dto/create-refresh-token.dto';
import { UpdateRefreshTokenDto } from './dto/update-refresh-token.dto';
import { RefreshToken } from './entities/refresh-token.entity';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { UUID } from 'node:crypto';

@Injectable()
export class RefreshTokenService {
  constructor(
    @InjectRepository(RefreshToken) private readonly refreshTokenRepository: Repository<RefreshToken>
  ){}

  create(createRefreshTokenDto: CreateRefreshTokenDto) {
    const token = new RefreshToken();
    token.tokenHash = createRefreshTokenDto.tokenHash;
    token.session = createRefreshTokenDto.session;
    return this.refreshTokenRepository.save(token);
  }

  findOne(refreshTokenId: UUID): Promise<RefreshToken| null> {
    return this.refreshTokenRepository.findOneBy({refreshTokenId});
  }

  async markAsUsed(refreshTokenId: UUID){
    const usedAt = new Date();
    await this.refreshTokenRepository.update(refreshTokenId, {usedAt});
    return;
  }

}
