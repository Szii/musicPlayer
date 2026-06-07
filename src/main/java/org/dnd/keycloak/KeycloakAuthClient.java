package org.dnd.keycloak;

import lombok.extern.slf4j.Slf4j;
import org.dnd.configuration.KeycloakProperties;
import org.dnd.exception.UnauthorizedException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@Slf4j
public class KeycloakAuthClient {

  private final KeycloakProperties keycloakProperties;
  private final RestClient restClient;

  public KeycloakAuthClient(
          KeycloakProperties keycloakProperties,
          RestClient.Builder restClientBuilder
  ) {
    this.keycloakProperties = keycloakProperties;
    this.restClient = restClientBuilder.build();
  }

  public KeycloakTokenResponse login(String username, String password) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "password");
    body.add("client_id", keycloakProperties.getClientId());
    body.add("client_secret", keycloakProperties.getClientSecret());
    body.add("username", username);
    body.add("password", password);

    return requestToken(body, "Invalid username or password");
  }

  public KeycloakTokenResponse refresh(String refreshToken) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "refresh_token");
    body.add("client_id", keycloakProperties.getClientId());
    body.add("client_secret", keycloakProperties.getClientSecret());
    body.add("refresh_token", refreshToken);

    return requestToken(body, "Invalid refresh token");
  }

  public void logout(String refreshToken) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("client_id", keycloakProperties.getClientId());
    body.add("client_secret", keycloakProperties.getClientSecret());
    body.add("refresh_token", refreshToken);

    try {
      restClient.post()
              .uri(keycloakProperties.getLogoutUri())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(body)
              .retrieve()
              .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      log.warn(
              "Keycloak logout failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );
    }
  }

  private KeycloakTokenResponse requestToken(
          MultiValueMap<String, String> body,
          String errorMessage
  ) {
    try {
      KeycloakTokenResponse response = restClient.post()
              .uri(keycloakProperties.getTokenUri())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(body)
              .retrieve()
              .body(KeycloakTokenResponse.class);

      if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
        throw new UnauthorizedException(errorMessage);
      }

      return response;
    } catch (RestClientResponseException exception) {
      log.warn(
              "Keycloak token request failed. status={}, body={}",
              exception.getStatusCode(),
              exception.getResponseBodyAsString()
      );

      throw new UnauthorizedException(errorMessage);
    }
  }
}