package org.dnd.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByName(String name);

  Optional<UserEntity> findByEmail(String email);

  boolean existsByName(String name);

  boolean existsByEmail(String email);

  int deleteByEmailVerifiedFalseAndCreatedAtBefore(LocalDateTime threshold);
}
