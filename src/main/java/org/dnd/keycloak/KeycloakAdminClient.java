package org.dnd.keycloak;

import lombok.extern.slf4j.Slf4j;
import org.dnd.configuration.KeycloakProperties;
import org.dnd.exception.ConflictException;
import org.dnd.exception.UnauthorizedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class KeycloakAdminClient {

  private final KeycloakProperties keycloakProperties;
  private final RestClient restClient;

  public KeycloakAdminClient(
          KeycloakProperties keycloakProperties,
          RestClient.Builder restClientBuilder
  ) {
    this.keycloakProperties = keycloakProperties;
    this.restClient = restClientBuilder.build();
  }

  public UUID createUser(
          String username,
          String email,
          String password,
          boolean emailVerified
  ) {
    String adminAccessToken = getAdminAccessToken();

    ResponseEntity<Void> createUserResponse = createKeycloakUser(
            adminAccessToken,
            username,
            email,
            emailVerified
    );

    UUID keycloakUserId = extractUserIdFromLocation(createUserResponse.getHeaders().getLocation());

    setPassword(adminAccessToken, keycloakUserId, password);

    return keycloakUserId;
  }

  private String getAdminAccessToken() {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "client_credentials");
    body.add("client_id", keycloakProperties.getAdminClientId());
    body.add("client_secret", keycloakProperties.getAdminClientSecret());

    try {
      KeycloakTokenResponse response = restClient.post()
              .uri(keycloakProperties.getTokenUri())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(body)
              .retrieve()
              .body(KeycloakTokenResponse.class);

      if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
        throw new UnauthorizedException("Could not get Keycloak admin token");
      }

      return response.accessToken();
    } catch (RestClientResponseException exception) {
      log.error(
              "Keycloak admin token request failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      throw new UnauthorizedException("Could not get Keycloak admin token");
    }
  }

  private ResponseEntity<Void> createKeycloakUser(
          String adminAccessToken,
          String username,
          String email,
          boolean emailVerified
  ) {
    Map<String, Object> body = Map.of(
            "username", username,
            "email", email,
            "enabled", true,
            "emailVerified", emailVerified,
            "requiredActions", List.of()
    );

    try {
      return restClient.post()
              .uri(keycloakProperties.getAdminUsersUri())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      log.warn(
              "Keycloak create user failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      if (exception.getStatusCode().value() == 409) {
        throw new ConflictException("User already exists in Keycloak");
      }

      throw new UnauthorizedException("Could not create Keycloak user");
    }
  }

  private void setPassword(
          String adminAccessToken,
          UUID keycloakUserId,
          String password
  ) {
    Map<String, Object> body = Map.of(
            "type", "password",
            "value", password,
            "temporary", false
    );

    try {
      restClient.put()
              .uri(keycloakProperties.getAdminUsersUri() + "/" + keycloakUserId + "/reset-password")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      log.error(
              "Keycloak set password failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      throw new UnauthorizedException("Could not set Keycloak user password");
    }
  }

  private UUID extractUserIdFromLocation(URI location) {
    if (location == null) {
      throw new UnauthorizedException("Keycloak did not return created user location");
    }

    String path = location.getPath();
    String id = path.substring(path.lastIndexOf("/") + 1);

    return UUID.fromString(id);
  }
}