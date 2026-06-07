package org.dnd.email;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.keycloak.KeycloakAdminClient;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnverifiedCleanupJob {

  private final UserRepository userRepository;
  private final KeycloakAdminClient keycloakAdminClient;

  @Transactional
  @Scheduled(cron = "0 0 * * * ?") // every hour, at minute 0
  public void cleanUpUnverifiedUsers() {
    LocalDateTime threshold = LocalDateTime.now().minusHours(1);

    List<UserEntity> usersToDelete = userRepository
            .findAllByEmailVerifiedFalseAndCreatedAtBefore(threshold);

    int deletedCount = 0;
    int skippedCount = 0;

    for (UserEntity user : usersToDelete) {
      try {
        if (user.getKeycloakId() != null) {
          keycloakAdminClient.deleteUser(user.getKeycloakId());
        }

        userRepository.delete(user);
        deletedCount++;
      } catch (Exception exception) {
        skippedCount++;

        log.error(
                "Failed to clean up unverified user. userId={}, username={}, keycloakId={}",
                user.getId(),
                user.getName(),
                user.getKeycloakId(),
                exception
        );
      }
    }

    log.info(
            "Deleted {} unverified users older than 1 hour. Skipped {} users.",
            deletedCount,
            skippedCount
    );
  }
}