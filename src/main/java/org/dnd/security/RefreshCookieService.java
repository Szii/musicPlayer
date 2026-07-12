package org.dnd.security;

import lombok.RequiredArgsConstructor;
import org.dnd.configuration.JwtConfiguration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshCookieService {

  public static final String OAUTH_STATE_COOKIE = "oauthState";

  private final JwtConfiguration jwtConfiguration;

  public ResponseCookie createRefreshCookie(String refreshToken) {
    return ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(jwtConfiguration.isRefreshCookieSecure())
            .sameSite(jwtConfiguration.getRefreshCookieSameSite())
            .path("/api/v1/auth")
            .maxAge(Duration.ofMillis(jwtConfiguration.getRefreshExpiration()))
            .build();
  }

  public ResponseCookie clearRefreshCookie() {
    return ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(jwtConfiguration.isRefreshCookieSecure())
            .sameSite(jwtConfiguration.getRefreshCookieSameSite())
            .path("/api/v1/auth")
            .maxAge(0)
            .build();
  }

  public ResponseCookie createOauthStateCookie(String state) {
    return ResponseCookie.from(OAUTH_STATE_COOKIE, state)
            .httpOnly(true)
            .secure(jwtConfiguration.isRefreshCookieSecure())
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(Duration.ofMinutes(5))
            .build();
  }

  public ResponseCookie clearOauthStateCookie() {
    return ResponseCookie.from(OAUTH_STATE_COOKIE, "")
            .httpOnly(true)
            .secure(jwtConfiguration.isRefreshCookieSecure())
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(0)
            .build();
  }
}
