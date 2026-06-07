package org.dnd.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@TestConfiguration
class TestJwtDecoderConfig {

  @Bean
  @Primary
  JwtDecoder jwtDecoder(ObjectMapper objectMapper) {
    return token -> {
      try {
        String[] parts = token.split("\\.");

        if (parts.length < 2) {
          throw new JwtException("Invalid test JWT");
        }

        Map<String, Object> headers = parseBase64Json(objectMapper, parts[0]);
        Map<String, Object> claims = parseBase64Json(objectMapper, parts[1]);

        if (!claims.containsKey("sub")) {
          throw new JwtException("Test JWT does not contain subject");
        }

        Instant issuedAt = getInstantClaim(claims, "iat", Instant.now());
        Instant expiresAt = getInstantClaim(claims, "exp", Instant.now().plusSeconds(300));

        return Jwt.withTokenValue(token)
                .headers(jwtHeaders -> jwtHeaders.putAll(headers))
                .claims(jwtClaims -> jwtClaims.putAll(claims))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
      } catch (JwtException exception) {
        throw exception;
      } catch (Exception exception) {
        throw new JwtException("Invalid test JWT", exception);
      }
    };
  }

  private static Map<String, Object> parseBase64Json(
          ObjectMapper objectMapper,
          String value
  ) throws Exception {
    String json = new String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8
    );

    return objectMapper.readValue(json, new TypeReference<>() {
    });
  }

  private static Instant getInstantClaim(
          Map<String, Object> claims,
          String claimName,
          Instant fallback
  ) {
    Object value = claims.get(claimName);

    if (value instanceof Number number) {
      return Instant.ofEpochSecond(number.longValue());
    }

    if (value instanceof String stringValue) {
      return Instant.ofEpochSecond(Long.parseLong(stringValue));
    }

    return fallback;
  }
}
