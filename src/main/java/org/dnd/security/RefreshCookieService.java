package org.dnd.security;

import lombok.RequiredArgsConstructor;
import org.dnd.configuration.JwtConfiguration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshCookieService {

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
}
