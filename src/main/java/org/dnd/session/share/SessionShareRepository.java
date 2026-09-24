package org.dnd.session.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionShareRepository extends JpaRepository<SessionShareEntity, UUID> {

  boolean existsByShareCode(String shareCode);

  Optional<SessionShareEntity> findByShareCodeAndUnpublishedAtIsNull(String shareCode);

  Optional<SessionShareEntity> findBySession_IdAndUnpublishedAtIsNull(UUID sessionId);

  List<SessionShareEntity> findBySession_Id(UUID sessionId);

  List<SessionShareEntity> findByUnpublishedAtIsNullOrderByUpdatedAtDesc();
}
