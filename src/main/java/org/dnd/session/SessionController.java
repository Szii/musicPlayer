package org.dnd.session;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dnd.api.SessionsApi;
import org.dnd.api.model.SessionRequest;
import org.dnd.api.model.SessionsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/v1")
@Tag(name = "BoardSessions", description = "Operations related to board sessions")
@RequiredArgsConstructor
public class SessionController implements SessionsApi {
  private final SessionService sessionService;

  @Override
  public ResponseEntity<SessionsResponse> deleteSession(Long sessionId) {
    return ResponseEntity.ok(sessionService.deleteSession(sessionId));
  }

  @Override
  public ResponseEntity<SessionsResponse> getSessions() {
    SessionsResponse sessions = sessionService.getSessions();
    if (sessions.getSessions().isEmpty()) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(sessionService.getSessions());
  }

  @Override
  public ResponseEntity<SessionsResponse> upsertSession(SessionRequest sessionRequest) {
    if (sessionRequest.getSessionId() == null) {
      return ResponseEntity.ok(sessionService.createSession(sessionRequest));
    } else {
      return ResponseEntity.ok(sessionService.updateSession(sessionRequest));
    }
  }
}
