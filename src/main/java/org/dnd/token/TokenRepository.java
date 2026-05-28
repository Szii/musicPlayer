package org.dnd.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<TokenEntity, Long> {

  Optional<TokenEntity> findByTokenAndValidTrue(String token);

  Optional<TokenEntity> findByUserIdAndType(Long userId, TokenType type);

  Optional<TokenEntity> findByUserIdAndTypeAndValidTrue(Long userId, TokenType type);

  List<TokenEntity> findAllByUserId(Long userId);

  boolean existsByToken(String token);
}