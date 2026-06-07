package org.dnd.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByName(String name);

  Optional<UserEntity> findByEmail(String email);

  boolean existsByName(String name);

  boolean existsByEmail(String email);

  Optional<UserEntity> findByKeycloakId(UUID keycloakId);

  boolean existsByKeycloakId(UUID keycloakId);

  int deleteByEmailVerifiedFalseAndCreatedAtBefore(LocalDateTime threshold);
}
