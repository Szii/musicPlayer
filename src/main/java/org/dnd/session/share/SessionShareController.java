package org.dnd.session.share;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dnd.api.ShareApi;
import org.dnd.api.model.PublishSessionRequest;
import org.dnd.api.model.SessionResponse;
import org.dnd.api.model.SessionShareResponse;
import org.dnd.api.model.SubscribeRequest;
import org.dnd.api.model.UpdateSessionShareRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.dnd.configuration.limiting.RateLimitNames.*;

@RequestMapping("/api/v1")
@Tag(name = "Share", description = "Endpoints for publishing and subscribing to sessions")
@RestController
@RequiredArgsConstructor
@Validated
public class SessionShareController implements ShareApi {

  private final SessionShareService sessionShareService;

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<List<SessionShareResponse>> getPublishedSessions() {
    return ResponseEntity.ok(sessionShareService.getPublishedSessions());
  }

  @Override
  @RateLimiting(
          name = CREATE_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> subscribeToSession(SubscribeRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(sessionShareService.subscribe(request.getShareCode()));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> syncSession(UUID sessionId) {
    return ResponseEntity.ok(sessionShareService.sync(sessionId));
  }

  @Override
  @RateLimiting(
          name = CREATE_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionShareResponse> publishSession(UUID sessionId, PublishSessionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(sessionShareService.publish(sessionId, request));
  }

  @Override
  @RateLimiting(
          name = CREATE_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionShareResponse> publishSessionUpdate(UUID sessionId) {
    return ResponseEntity.ok(sessionShareService.publishUpdate(sessionId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionShareResponse> updateSessionShare(UUID sessionId, UpdateSessionShareRequest request) {
    return ResponseEntity.ok(sessionShareService.updateDescription(sessionId, request));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Void> unpublishSession(UUID sessionId) {
    sessionShareService.unpublish(sessionId);
    return ResponseEntity.noContent().build();
  }
}
