package org.dnd.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationTokenEntity, Long> {

  Optional<EmailVerificationTokenEntity> findByTokenAndValidTrue(String token);

  Optional<EmailVerificationTokenEntity> findByUserId(Long userId);

  boolean existsByToken(String token);
}
