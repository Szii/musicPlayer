package org.dnd.email;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnverifiedCleanupJob {

  private final UserRepository userRepository;

  @Transactional
  @Scheduled(cron = "0 0 * * * ?") // every hour, at minute 0
  public void cleanUpUnverifiedUsers() {
    LocalDateTime threshold = LocalDateTime.now().minusHours(1);

    int deletedCount = userRepository
            .deleteByEmailVerifiedFalseAndCreatedAtBefore(threshold);

    log.info("Deleted {} unverified users older than 1 hour", deletedCount);
  }
}
