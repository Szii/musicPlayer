package org.dnd.utils;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

  private final UserRepository userRepository;

  public UUID getCurrentUserId() {
    return getCurrentUserEntity().getId();
  }

  public UserEntity getCurrentUserEntity() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || authentication.getPrincipal() == null) {
      throw new ForbiddenException("Not authenticated");
    }

    Object principal = authentication.getPrincipal();

    if (principal instanceof Jwt jwt) {
      UUID keycloakId = parseKeycloakId(jwt.getSubject());

      return userRepository.findByKeycloakId(keycloakId)
              .orElseThrow(() -> new NotFoundException("User not found"));
    }

    // Temporary compatibility with your old custom JWT auth
    if (principal instanceof UserAuthDTO userAuthDTO) {
      return userRepository.findById(userAuthDTO.getId())
              .orElseThrow(() -> new NotFoundException("User not found"));
    }

    throw new ForbiddenException("Not authenticated");
  }

  public UUID getCurrentKeycloakId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || authentication.getPrincipal() == null) {
      throw new ForbiddenException("Not authenticated");
    }

    Object principal = authentication.getPrincipal();

    if (principal instanceof Jwt jwt) {
      return parseKeycloakId(jwt.getSubject());
    }

    UserEntity user = getCurrentUserEntity();

    if (user.getKeycloakId() == null) {
      throw new ForbiddenException("User is not linked to Keycloak");
    }

    return user.getKeycloakId();
  }

  private UUID parseKeycloakId(String subject) {
    try {
      return UUID.fromString(subject);
    } catch (Exception exception) {
      throw new ForbiddenException("Invalid Keycloak user id");
    }
  }
}
