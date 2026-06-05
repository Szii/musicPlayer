package org.dnd.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class JwtConfiguration {
  @Value("${jwt.secret}")
  private String secret;

  @Value("${jwt.expiration}")
  private Long expiration;

  @Value("${jwt.refresh-expiration}")
  private Long refreshExpiration;

  @Value("${jwt.refresh-cookie-secure}")
  private boolean refreshCookieSecure;

  @Value("${jwt.refresh-cookie-same-site}")
  private String refreshCookieSameSite;
}
