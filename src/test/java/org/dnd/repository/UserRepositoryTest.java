package org.dnd.repository;

import jakarta.transaction.Transactional;
import org.dnd.model.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class UserRepositoryTest extends DatabaseBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    void saveAndFindByName() {
        UserEntity user = new UserEntity();
        user.setName("testuser");
        user.setPassword("secret");
        userRepository.save(user);

        Optional<UserEntity> found = userRepository.findByName("testuser");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("testuser");
        assertThat(userRepository.existsByName("testuser")).isTrue();
        assertThat(userRepository.existsByName("unknown")).isFalse();
    }
}
