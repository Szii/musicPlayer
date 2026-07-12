package org.dnd.user;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.configuration.KeycloakProperties;
import org.dnd.exception.UnauthorizedException;
import org.dnd.keycloak.KeycloakAuthClient;
import org.dnd.keycloak.KeycloakTokenResponse;
import org.dnd.security.RefreshCookieService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static org.dnd.configuration.limiting.RateLimitNames.GOOGLE_AUTH;
import static org.dnd.configuration.limiting.RateLimitNames.GOOGLE_AUTH_KEY;

@RequestMapping("/api/v1/auth/google")
@Tag(name = "Users", description = "Google sign-in")
@RestController
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthController {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final KeycloakProperties keycloakProperties;
  private final KeycloakAuthClient keycloakAuthClient;
  private final RefreshCookieService refreshCookieService;
  private final UserAuthService userAuthService;

  @Value("${app.backend-url}")
  private String backendUrl;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  @Value("${app.frontend-path-auth-callback}")
  private String frontendPathAuthCallback;

  @GetMapping
  @RateLimiting(name = GOOGLE_AUTH, cacheKey = GOOGLE_AUTH_KEY, ratePerMethod = true)
  public ResponseEntity<Void> startGoogleLogin() {
    String state = randomState();

    String authorizationUrl = UriComponentsBuilder
            .fromUriString(keycloakProperties.getAuthorizationUri())
            .queryParam("client_id", keycloakProperties.getClientId())
            .queryParam("response_type", "code")
            .queryParam("scope", "openid profile email")
            .queryParam("redirect_uri", redirectUri())
            .queryParam("kc_idp_hint", keycloakProperties.getGoogleIdpAlias())
            .queryParam("state", state)
            .encode()
            .build()
            .toUriString();

    return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.SET_COOKIE, refreshCookieService.createOauthStateCookie(state).toString())
            .location(URI.create(authorizationUrl))
            .build();
  }

  @GetMapping("/callback")
  @RateLimiting(name = GOOGLE_AUTH, cacheKey = GOOGLE_AUTH_KEY, ratePerMethod = true)
  public ResponseEntity<Void> googleCallback(
          @RequestParam(required = false) String code,
          @RequestParam(required = false) String state,
          @CookieValue(name = RefreshCookieService.OAUTH_STATE_COOKIE, required = false) String stateCookie,
          HttpServletRequest request
  ) {
    ResponseCookie clearState = refreshCookieService.clearOauthStateCookie();

    if (code == null || !statesMatch(state, stateCookie)) {
      log.warn("Rejected Google callback. hasCode={}, stateMatched={}", code != null, statesMatch(state, stateCookie));

      return ResponseEntity.status(HttpStatus.FOUND)
              .header(HttpHeaders.SET_COOKIE, clearState.toString())
              .location(frontendCallback("login_failed"))
              .build();
    }

    try {
      KeycloakTokenResponse keycloakToken =
              keycloakAuthClient.exchangeAuthorizationCode(code, redirectUri());

      userAuthService.provisionBrokeredUser(keycloakToken.accessToken());

      ResponseCookie refreshCookie =
              refreshCookieService.createRefreshCookie(keycloakToken.refreshToken());

      return ResponseEntity.status(HttpStatus.FOUND)
              .header(HttpHeaders.SET_COOKIE, clearState.toString())
              .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
              .location(frontendCallback(null))
              .build();
    } catch (UnauthorizedException exception) {
      log.warn("Google code exchange failed", exception);

      return ResponseEntity.status(HttpStatus.FOUND)
              .header(HttpHeaders.SET_COOKIE, clearState.toString())
              .location(frontendCallback("login_failed"))
              .build();
    }
  }

  private String redirectUri() {
    return backendUrl + "/api/v1/auth/google/callback";
  }

  private URI frontendCallback(String error) {
    UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(frontendUrl)
            .path(frontendPathAuthCallback);

    if (error != null) {
      builder.queryParam("error", error);
    }

    return URI.create(builder.toUriString());
  }

  private boolean statesMatch(String state, String stateCookie) {
    if (state == null || stateCookie == null || state.isBlank() || stateCookie.isBlank()) {
      return false;
    }

    return MessageDigest.isEqual(
            state.getBytes(StandardCharsets.UTF_8),
            stateCookie.getBytes(StandardCharsets.UTF_8)
    );
  }

  private String randomState() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);

    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
