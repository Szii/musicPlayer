package org.dnd.session.share;

import lombok.RequiredArgsConstructor;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SessionShareLifecycle {

  private final SessionShareRepository sessionShareRepository;
  private final SessionRepository sessionRepository;
  private final SessionMaterializer sessionMaterializer;

  public void beforeSessionDelete(SessionEntity session) {
    if (session.isSubscribed()) {
      SessionShareEntity share = session.getSourceShare();
      sessionMaterializer.clear(session);
      session.setSourceShare(null);
      sessionRepository.flush();
      if (share != null) {
        deleteIfAbandoned(share);
      }
      return;
    }

    for (SessionShareEntity share : sessionShareRepository.findBySession_Id(session.getId())) {
      share.setSession(null);
      if (share.isPublished()) {
        unpublish(share);
      }
    }
  }

  public void unpublish(SessionShareEntity share) {
    share.setUnpublishedAt(LocalDateTime.now());
    deleteIfAbandoned(share);
  }

  private void deleteIfAbandoned(SessionShareEntity share) {
    if (!share.isPublished() && sessionRepository.countBySourceShare_Id(share.getId()) == 0) {
      sessionShareRepository.delete(share);
    }
  }
}
