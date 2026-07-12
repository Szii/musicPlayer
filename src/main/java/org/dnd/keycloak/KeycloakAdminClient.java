package org.dnd.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dnd.configuration.KeycloakProperties;
import org.dnd.exception.BadRequestException;
import org.dnd.exception.ConflictException;
import org.dnd.exception.UnauthorizedException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
  private final ObjectMapper objectMapper;

  public KeycloakAdminClient(
          KeycloakProperties keycloakProperties,
          RestClient.Builder restClientBuilder,
          ObjectMapper objectMapper
  ) {
    this.keycloakProperties = keycloakProperties;
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
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

    try {
      setPassword(adminAccessToken, keycloakUserId, password);
    } catch (RuntimeException exception) {
      deleteUser(keycloakUserId);
      throw exception;
    }

    return keycloakUserId;
  }

  public void logoutUser(UUID keycloakUserId) {
    if (keycloakUserId == null) {
      return;
    }

    String adminAccessToken = getAdminAccessToken();

    try {
      restClient.post()
              .uri(keycloakProperties.getAdminUsersUri() + "/" + keycloakUserId + "/logout")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .retrieve()
              .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      log.error(
              "Keycloak logout of all sessions failed for user {}. status={}, body={}",
              keycloakUserId,
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      throw new UnauthorizedException("Could not revoke Keycloak sessions");
    }
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

  public void deleteUser(UUID keycloakUserId) {
    if (keycloakUserId == null) {
      return;
    }

    String adminAccessToken = getAdminAccessToken();

    try {
      restClient.delete()
              .uri(keycloakProperties.getAdminUsersUri() + "/{userId}", keycloakUserId)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .retrieve()
              .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
        log.warn("Keycloak user {} was already deleted", keycloakUserId);
        return;
      }

      log.error(
              "Failed to delete Keycloak user {}. status={}, body={}",
              keycloakUserId,
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      throw exception;
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
      log.warn(
              "Keycloak set password failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      if (exception.getStatusCode().value() == 400) {
        throw new BadRequestException(extractPasswordPolicyMessage(exception));
      }

      throw new UnauthorizedException("Could not set Keycloak user password");
    }
  }

  private String extractPasswordPolicyMessage(RestClientResponseException exception) {
    try {
      JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
      String description = body.path("error_description").asText(null);

      if (description != null && !description.isBlank()) {
        return description;
      }
    } catch (Exception parseException) {
      log.debug("Could not parse Keycloak password policy error", parseException);
    }

    return "Password does not meet the required policy";
  }

  private UUID extractUserIdFromLocation(URI location) {
    if (location == null) {
      throw new UnauthorizedException("Keycloak did not return created user location");
    }

    String path = location.getPath();
    String id = path.substring(path.lastIndexOf("/") + 1);

    return UUID.fromString(id);
  }

  public void updatePassword(UUID keycloakUserId, String newPassword) {
    String adminAccessToken = getAdminAccessToken();

    setPassword(adminAccessToken, keycloakUserId, newPassword);
  }

  public boolean hasFederatedIdentity(UUID keycloakUserId) {
    if (keycloakUserId == null) {
      return false;
    }

    String adminAccessToken = getAdminAccessToken();

    try {
      List<Map<String, Object>> identities = restClient.get()
              .uri(keycloakProperties.getAdminUsersUri() + "/" + keycloakUserId + "/federated-identity")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .retrieve()
              .body(new ParameterizedTypeReference<>() {
              });

      return identities != null && !identities.isEmpty();
    } catch (RestClientResponseException exception) {
      log.error(
              "Keycloak federated identity lookup failed for user {}. status={}, body={}",
              keycloakUserId,
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      throw new UnauthorizedException("Could not read Keycloak federated identities");
    }
  }

  public boolean usernameExists(String username) {
    String adminAccessToken = getAdminAccessToken();

    try {
      List<Map<String, Object>> users = restClient.get()
              .uri(keycloakProperties.getAdminUsersUri() + "?username={username}&exact=true", username)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .retrieve()
              .body(new ParameterizedTypeReference<>() {
              });

      return users != null && !users.isEmpty();
    } catch (RestClientResponseException exception) {
      log.error(
              "Keycloak username lookup failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      throw new UnauthorizedException("Could not query Keycloak users");
    }
  }

  public void updateUsername(UUID keycloakUserId, String username) {
    String adminAccessToken = getAdminAccessToken();

    try {
      restClient.put()
              .uri(keycloakProperties.getAdminUsersUri() + "/" + keycloakUserId)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("username", username))
              .retrieve()
              .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      log.warn(
              "Keycloak update username failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      if (exception.getStatusCode().value() == 409) {
        throw new ConflictException("Username already exists");
      }

      if (exception.getStatusCode().value() == 400) {
        throw new BadRequestException("Invalid username");
      }

      throw new UnauthorizedException("Could not update Keycloak username");
    }
  }

  public KeycloakBruteForceStatus getBruteForceStatus(UUID keycloakUserId) {
    if (keycloakUserId == null) {
      return null;
    }

    try {
      String adminAccessToken = getAdminAccessToken();

      return restClient.get()
              .uri(keycloakProperties.getAdminBruteForceUri() + "/" + keycloakUserId)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .retrieve()
              .body(KeycloakBruteForceStatus.class);
    } catch (Exception exception) {
      log.warn(
              "Could not read Keycloak brute-force status for user {}",
              keycloakUserId,
              exception
      );

      return null;
    }
  }

  public void updateEmail(
          UUID keycloakUserId,
          String email,
          boolean emailVerified
  ) {
    String adminAccessToken = getAdminAccessToken();

    Map<String, Object> body = Map.of(
            "email", email,
            "emailVerified", emailVerified
    );

    try {
      restClient.put()
              .uri(keycloakProperties.getAdminUsersUri() + "/" + keycloakUserId)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      log.error(
              "Keycloak update email failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      throw new UnauthorizedException("Could not update Keycloak user email");
    }
  }
}