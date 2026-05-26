package org.dnd.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByName(String name);

  Optional<UserEntity> findByNameOrEmail(String name, String email);

  boolean existsByEmailAndEmailVerifiedTrue(String email);

  Optional<UserEntity> findByVerificationToken(String token);

  boolean existsByName(String name);

  boolean existsByEmail(String email);
}
