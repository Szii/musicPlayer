package org.dnd.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.dnd.exception.LoginThrottledException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class LoginThrottleService {

  private static final int FREE_ATTEMPTS = 3;

  private final Cache<String, AttemptState> attempts = Caffeine.newBuilder()
          .expireAfterAccess(Duration.ofHours(1))
          .build();

  public void checkAllowed(String username) {
    String key = normalize(username);
    AttemptState state = attempts.getIfPresent(key);

    if (state == null || state.blockedUntil == null) {
      return;
    }

    Instant now = Instant.now();
    if (state.blockedUntil.isAfter(now)) {
      long retryAfterSeconds = Duration.between(now, state.blockedUntil).toSeconds();
      throw new LoginThrottledException(
              "Too many failed login attempts. Please try again later (" + retryAfterSeconds + " seconds).",
              Math.max(1, retryAfterSeconds)
      );
    }
  }

  public void recordFailure(String username) {
    String key = normalize(username);
    AttemptState state = attempts.get(key, k -> new AttemptState());

    state.failedAttempts++;

    if (state.failedAttempts > FREE_ATTEMPTS) {
      Duration delay = calculateDelay(state.failedAttempts);
      state.blockedUntil = Instant.now().plus(delay);
    }
  }

  public void recordSuccess(String username) {
    attempts.invalidate(normalize(username));
  }

  private Duration calculateDelay(int failedAttempts) {
    int extraFailures = failedAttempts - FREE_ATTEMPTS;

    return switch (extraFailures) {
      case 1 -> Duration.ofSeconds(30);
      case 2 -> Duration.ofMinutes(2);
      case 3 -> Duration.ofMinutes(5);
      default -> Duration.ofMinutes(15);
    };
  }

  private String normalize(String username) {
    return username == null ? "" : username.trim().toLowerCase();
  }

  private static final class AttemptState {
    private int failedAttempts;
    private Instant blockedUntil;
  }
}
