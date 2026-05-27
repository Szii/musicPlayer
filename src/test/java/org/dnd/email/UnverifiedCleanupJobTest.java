package org.dnd.email;

import org.dnd.DatabaseBase;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class UnverifiedCleanupJobTest extends DatabaseBase {

  @Autowired
  private UnverifiedCleanupJob unverifiedCleanupJob;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EmailVerificationTokenRepository tokenRepository;

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void cleanUpUnverifiedUsers_deletesOnlyOldUnverifiedUsers() {
    LocalDateTime now = LocalDateTime.now();

    UserEntity oldUnverifiedUser = saveUser(
            "oldUnverified",
            "old-unverified@email.com",
            false,
            null,
            now.minusHours(2)
    );

    saveToken(
            oldUnverifiedUser,
            "old-unverified-token",
            EmailVerificationTokenType.REGISTRATION,
            oldUnverifiedUser.getEmail(),
            now.minusHours(2),
            true
    );

    UserEntity recentUnverifiedUser = saveUser(
            "recentUnverified",
            "recent-unverified@email.com",
            false,
            null,
            now.minusMinutes(20)
    );

    saveToken(
            recentUnverifiedUser,
            "recent-unverified-token",
            EmailVerificationTokenType.REGISTRATION,
            recentUnverifiedUser.getEmail(),
            now.minusMinutes(20),
            true
    );

    UserEntity oldVerifiedUser = saveUser(
            "oldVerified",
            "old-verified@email.com",
            true,
            null,
            now.minusHours(2)
    );

    saveToken(
            oldVerifiedUser,
            "old-verified-token",
            EmailVerificationTokenType.REGISTRATION,
            oldVerifiedUser.getEmail(),
            now.minusHours(2),
            false
    );

    UserEntity verifiedUserChangingEmail = saveUser(
            "verifiedChangingEmail",
            "verified-changing@email.com",
            true,
            "new-email@email.com",
            now.minusHours(2)
    );

    saveToken(
            verifiedUserChangingEmail,
            "email-change-token",
            EmailVerificationTokenType.EMAIL_CHANGE,
            verifiedUserChangingEmail.getPendingEmail(),
            now.minusHours(2),
            true
    );

    unverifiedCleanupJob.cleanUpUnverifiedUsers();

    assertTrue(userRepository.findById(oldUnverifiedUser.getId()).isEmpty());
    assertTrue(tokenRepository.findByUserId(oldUnverifiedUser.getId()).isEmpty());

    assertTrue(userRepository.findById(recentUnverifiedUser.getId()).isPresent());
    assertTrue(tokenRepository.findByUserId(recentUnverifiedUser.getId()).isPresent());

    assertTrue(userRepository.findById(oldVerifiedUser.getId()).isPresent());
    assertTrue(tokenRepository.findByUserId(oldVerifiedUser.getId()).isPresent());

    UserEntity remainingChangingEmailUser = userRepository
            .findById(verifiedUserChangingEmail.getId())
            .orElseThrow();

    assertTrue(remainingChangingEmailUser.isEmailVerified());
    assertEquals("verified-changing@email.com", remainingChangingEmailUser.getEmail());
    assertEquals("new-email@email.com", remainingChangingEmailUser.getPendingEmail());
    assertTrue(tokenRepository.findByUserId(verifiedUserChangingEmail.getId()).isPresent());
  }

  private UserEntity saveUser(
          String name,
          String email,
          boolean emailVerified,
          String pendingEmail,
          LocalDateTime createdAt
  ) {
    UserEntity user = UserEntity.builder()
            .name(name)
            .password("encoded-password")
            .email(email)
            .emailVerified(emailVerified)
            .pendingEmail(pendingEmail)
            .createdAt(createdAt)
            .build();

    return userRepository.save(user);
  }

  private EmailVerificationTokenEntity saveToken(
          UserEntity user,
          String tokenValue,
          EmailVerificationTokenType type,
          String targetEmail,
          LocalDateTime createdAt,
          boolean valid
  ) {
    EmailVerificationTokenEntity token = EmailVerificationTokenEntity.builder()
            .user(user)
            .token(tokenValue)
            .type(type)
            .targetEmail(targetEmail)
            .createdAt(createdAt)
            .valid(valid)
            .build();

    return tokenRepository.save(token);
  }
}
