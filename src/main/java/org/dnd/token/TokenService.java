package org.dnd.token;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.dnd.security.RegistrationTokenService;
import org.dnd.user.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {

  private final TokenRepository tokenRepository;
  private final RegistrationTokenService registrationTokenService;

  @Value("${app.token.registration-ttl-minutes}")
  private long registrationTtlMinutes;

  @Value("${app.token.email-change-ttl-minutes}")
  private long emailChangeTtlMinutes;

  @Value("${app.token.forgot-password-ttl-minutes}")
  private long forgotPasswordTtlMinutes;

  @Transactional
  public String create(
          UserEntity user,
          TokenType type,
          String targetEmail
  ) {
    tokenRepository
            .findByUserIdAndType(user.getId(), type)
            .ifPresent(tokenRepository::delete);

    String rawToken = generateUniqueToken();

    TokenEntity newToken = new TokenEntity();
    newToken.setUser(user);
    newToken.setTokenHash(hash(rawToken));
    newToken.setType(type);
    newToken.setTargetEmail(targetEmail);
    newToken.setCreatedAt(LocalDateTime.now());
    newToken.setExpiresAt(LocalDateTime.now().plus(ttlFor(type)));

    tokenRepository.saveAndFlush(newToken);

    return rawToken;
  }

  public Optional<TokenEntity> findValid(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }

    return tokenRepository
            .findByTokenHash(hash(rawToken))
            .filter(token -> !token.isExpired());
  }

  private Duration ttlFor(TokenType type) {
    return switch (type) {
      case REGISTRATION -> Duration.ofMinutes(registrationTtlMinutes);
      case EMAIL_CHANGE -> Duration.ofMinutes(emailChangeTtlMinutes);
      case FORGOT_PASSWORD -> Duration.ofMinutes(forgotPasswordTtlMinutes);
    };
  }

  private String generateUniqueToken() {
    String token;

    do {
      token = registrationTokenService.generateToken();
    } while (tokenRepository.existsByTokenHash(hash(token)));

    return token;
  }

  private String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");

      return HexFormat.of().formatHex(
              digest.digest(rawToken.getBytes(StandardCharsets.UTF_8))
      );
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
