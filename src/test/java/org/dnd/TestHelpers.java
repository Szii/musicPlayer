package org.dnd;

import org.dnd.user.UserEntity;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public class TestHelpers {

  public static RequestPostProcessor authenticatedAs(UserEntity user) {
    if (user.getKeycloakId() == null) {
      user.setKeycloakId(UUID.randomUUID());
    }

    return jwt().jwt(jwt -> jwt
            .subject(user.getKeycloakId().toString())
            .claim("preferred_username", user.getName())
            .claim("email", user.getEmail())
            .claim("email_verified", user.isEmailVerified())
            .claim("scope", "profile email")
    );
  }

  public static UserEntity withKeycloakId(UserEntity user) {
    if (user.getKeycloakId() == null) {
      user.setKeycloakId(UUID.randomUUID());
    }

    return user;
  }

  public static String getKeycloakAccessTokenForUser(UserEntity user) throws Exception {
    if (user.getKeycloakId() == null) {
      throw new IllegalStateException("Test user must have keycloakId");
    }

    String headerJson = """
            {"alg":"none","typ":"JWT"}
            """;

    String payloadJson = """
            {
              "sub": "%s",
              "preferred_username": "%s",
              "email": "%s",
              "email_verified": %s,
              "scope": "profile email"
            }
            """.formatted(
            user.getKeycloakId(),
            user.getName(),
            user.getEmail(),
            user.isEmailVerified()
    );

    return base64Url(headerJson) + "." + base64Url(payloadJson) + ".test-signature";
  }

  private static String base64Url(String value) {
    return java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
