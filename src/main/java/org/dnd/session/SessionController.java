package org.dnd.session;

import org.dnd.api.SessionsApi;
import org.dnd.api.model.SessionRequest;
import org.dnd.api.model.SessionsResponse;
import org.springframework.http.ResponseEntity;

public class SessionController implements SessionsApi {

  @Override
  public ResponseEntity<SessionsResponse> deleteSession(Long sessionId) throws Exception {
    return null;
  }

  @Override
  public ResponseEntity<SessionsResponse> getSessions() throws Exception {
    return null;
  }

  @Override
  public ResponseEntity<SessionsResponse> upsertSession(SessionRequest sessionRequest) throws Exception {
    return null;
  }
}
