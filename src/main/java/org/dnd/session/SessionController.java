package org.dnd.session;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dnd.api.SessionsApi;
import org.dnd.api.model.ReorderSessionBoardsRequest;
import org.dnd.api.model.SessionRequest;
import org.dnd.api.model.SessionResponse;
import org.dnd.api.model.SessionsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

import static org.dnd.configuration.limiting.RateLimitNames.*;

@Controller
@RequestMapping("/api/v1")
@Tag(name = "BoardSessions", description = "Operations related to board sessions")
@RequiredArgsConstructor
public class SessionController implements SessionsApi {
  private final SessionService sessionService;

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionsResponse> deleteSession(UUID sessionId) {
    return ResponseEntity.ok(sessionService.deleteSession(sessionId));
  }

  @Override
  public ResponseEntity<SessionResponse> getSessionById(UUID sessionId) {
    return ResponseEntity.ok(sessionService.getSession(sessionId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionsResponse> getSessions() {
    SessionsResponse sessions = sessionService.getSessions();
    if (sessions.getSessions().isEmpty()) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(sessionService.getSessions());
  }

  @Override
  @RateLimiting(
          name = CREATE_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionsResponse> upsertSession(SessionRequest sessionRequest) {
    if (sessionRequest.getSessionId() == null) {
      return ResponseEntity.ok(sessionService.createSession(sessionRequest));
    } else {
      return ResponseEntity.ok(sessionService.updateSession(sessionRequest));
    }
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> addTrackToSession(UUID sessionId, UUID trackId) {
    return ResponseEntity.ok(sessionService.addTrack(sessionId, trackId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> removeTrackFromSession(UUID sessionId, UUID trackId) {
    return ResponseEntity.ok(sessionService.removeTrack(sessionId, trackId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> addGroupToSession(UUID sessionId, UUID groupId) {
    return ResponseEntity.ok(sessionService.addGroup(sessionId, groupId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> removeGroupFromSession(UUID sessionId, UUID groupId) {
    return ResponseEntity.ok(sessionService.removeGroup(sessionId, groupId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> reorderSessionBoards(UUID sessionId, ReorderSessionBoardsRequest reorderSessionBoardsRequest) {
    return ResponseEntity.ok(sessionService.reorderBoards(sessionId, reorderSessionBoardsRequest));
  }
}
