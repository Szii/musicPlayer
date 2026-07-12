package org.dnd.security;

import lombok.RequiredArgsConstructor;
import org.dnd.exception.LoginThrottledException;
import org.dnd.keycloak.KeycloakAdminClient;
import org.dnd.keycloak.KeycloakBruteForceStatus;
import org.dnd.user.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginLockoutService {

  private final KeycloakAdminClient keycloakAdminClient;

  public void throwIfLockedOut(UserEntity user) {
    if (user == null || user.getKeycloakId() == null) {
      return;
    }

    KeycloakBruteForceStatus status = keycloakAdminClient.getBruteForceStatus(user.getKeycloakId());

    if (status == null || !status.disabled()) {
      return;
    }

    long retryAfterSeconds = Math.max(
            1,
            status.failedLoginNotBefore() - Instant.now().getEpochSecond()
    );

    throw new LoginThrottledException(
            "Too many failed login attempts. Please try again later ("
                    + retryAfterSeconds + " seconds).",
            retryAfterSeconds
    );
  }
}
