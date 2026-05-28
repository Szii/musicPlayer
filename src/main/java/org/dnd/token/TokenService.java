package org.dnd.token;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.dnd.security.RegistrationTokenService;
import org.dnd.user.UserEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {

  private final TokenRepository tokenRepository;
  private final RegistrationTokenService registrationTokenService;


  @Transactional
  public TokenEntity create(
          UserEntity user,
          TokenType type,
          String targetEmail
  ) {

    Optional<TokenEntity> token = tokenRepository
            .findByUserIdAndType(user.getId(), type);

    token.ifPresent(tokenRepository::delete);

    TokenEntity newToken = new TokenEntity();
    newToken.setUser(user);

    newToken.setToken(generateUniqueToken());
    newToken.setType(type);
    newToken.setTargetEmail(targetEmail);
    newToken.setCreatedAt(LocalDateTime.now());
    newToken.setValid(true);

    return tokenRepository.saveAndFlush(newToken);
  }

  private String generateUniqueToken() {
    String token;

    do {
      token = registrationTokenService.generateToken();
    } while (tokenRepository.existsByToken(token));

    return token;
  }
}
