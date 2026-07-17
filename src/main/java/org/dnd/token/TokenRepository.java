package org.dnd.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TokenRepository extends JpaRepository<TokenEntity, UUID> {

  Optional<TokenEntity> findByTokenHash(String tokenHash);

  Optional<TokenEntity> findByUserIdAndType(UUID userId, TokenType type);

  List<TokenEntity> findAllByUserId(UUID userId);

  boolean existsByTokenHash(String tokenHash);
}
