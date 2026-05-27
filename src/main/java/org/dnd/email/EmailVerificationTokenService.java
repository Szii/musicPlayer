package org.dnd.email;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.dnd.security.RegistrationTokenService;
import org.dnd.user.UserEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationTokenService {

  private final EmailVerificationTokenRepository tokenRepository;
  private final RegistrationTokenService registrationTokenService;

  @Transactional
  public EmailVerificationTokenEntity createOrUpdate(
          UserEntity user,
          EmailVerificationTokenType type,
          String targetEmail
  ) {
    EmailVerificationTokenEntity verificationToken = tokenRepository
            .findByUserId(user.getId())
            .orElseGet(() -> EmailVerificationTokenEntity.builder()
                    .user(user)
                    .build());

    verificationToken.setToken(generateUniqueToken());
    verificationToken.setType(type);
    verificationToken.setTargetEmail(targetEmail);
    verificationToken.setCreatedAt(LocalDateTime.now());
    verificationToken.setValid(true);

    return tokenRepository.saveAndFlush(verificationToken);
  }

  private String generateUniqueToken() {
    String token;

    do {
      token = registrationTokenService.generateToken();
    } while (tokenRepository.existsByToken(token));

    return token;
  }
}
