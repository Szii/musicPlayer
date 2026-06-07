package org.dnd.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.configuration.JwtConfiguration;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwtService {

  private static final String CLAIM_TYPE = "typ";
  private static final String CLAIM_BOARD_ID = "boardId";
  private static final String CLAIM_TRACK_ID = "trackId";

  private static final String TYPE_STREAM = "stream";
  private static final String TYPE_TRACK_STREAM = "track_stream";

  private static final long STREAM_EXPIRATION_MS = 360_000;

  private final JwtConfiguration jwtConfiguration;

  public String generateStreamToken(Long userId, Long boardId) {
    return Jwts.builder()
            .setSubject(userId.toString())
            .claim(CLAIM_TYPE, TYPE_STREAM)
            .claim(CLAIM_BOARD_ID, boardId)
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + STREAM_EXPIRATION_MS))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
  }

  public String generateTrackStreamToken(Long userId, Long trackId) {
    return Jwts.builder()
            .setSubject(userId.toString())
            .claim(CLAIM_TYPE, TYPE_TRACK_STREAM)
            .claim(CLAIM_TRACK_ID, trackId)
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + STREAM_EXPIRATION_MS))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
  }

  public Long getBoardIdFromStreamToken(String token) {
    Claims claims = getAllClaimsFromToken(token);
    return extractLongClaim(claims, CLAIM_BOARD_ID);
  }

  public Long getTrackIdFromStreamToken(String token) {
    Claims claims = getAllClaimsFromToken(token);
    return extractLongClaim(claims, CLAIM_TRACK_ID);
  }

  public boolean validateStreamToken(String token, Long expectedBoardId) {
    try {
      Claims claims = getAllClaimsFromToken(token);

      String tokenType = claims.get(CLAIM_TYPE, String.class);
      if (!TYPE_STREAM.equals(tokenType)) {
        return false;
      }

      Long tokenBoardId = getBoardIdFromStreamToken(token);
      return expectedBoardId.equals(tokenBoardId);
    } catch (JwtException | IllegalArgumentException e) {
      log.error("Invalid stream JWT token: {}", e.getMessage());
      return false;
    }
  }

  public boolean validateTrackStreamToken(String token, Long expectedTrackId) {
    try {
      Claims claims = getAllClaimsFromToken(token);

      String tokenType = claims.get(CLAIM_TYPE, String.class);
      if (!TYPE_TRACK_STREAM.equals(tokenType)) {
        return false;
      }

      Long tokenTrackId = getTrackIdFromStreamToken(token);
      return expectedTrackId.equals(tokenTrackId);
    } catch (JwtException | IllegalArgumentException e) {
      log.error("Invalid track stream JWT token: {}", e.getMessage());
      return false;
    }
  }

  public void validateStreamTokenOrThrow(String token, Long expectedBoardId) {
    if (!validateStreamToken(token, expectedBoardId)) {
      throw new JwtException("Invalid stream token");
    }
  }

  public void validateTrackStreamTokenOrThrow(String token, Long expectedTrackId) {
    if (!validateTrackStreamToken(token, expectedTrackId)) {
      throw new JwtException("Invalid track stream token");
    }
  }

  private Long extractLongClaim(Claims claims, String claimName) {
    Object raw = claims.get(claimName);

    if (raw instanceof Integer i) {
      return i.longValue();
    }

    if (raw instanceof Long l) {
      return l;
    }

    if (raw instanceof String s) {
      return Long.parseLong(s);
    }

    throw new IllegalArgumentException("Missing or invalid " + claimName + " claim");
  }

  private Claims getAllClaimsFromToken(String token) {
    return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
  }

  private Key getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtConfiguration.getSecret());
    return Keys.hmacShaKeyFor(keyBytes);
  }
}