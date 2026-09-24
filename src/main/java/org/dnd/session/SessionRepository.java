package org.dnd.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
  List<SessionEntity> findByOwner_Id(UUID userId);

  long countByOwner_Id(UUID userId);

  long countByOwner_IdAndSubscribed(UUID userId, boolean subscribed);

  List<SessionEntity> findByOwner_IdAndSubscribed(UUID userId, boolean subscribed);

  boolean existsByOwner_IdAndSourceShare_Id(UUID userId, UUID shareId);

  long countBySourceShare_Id(UUID shareId);

  Optional<SessionEntity> findByIdAndOwner_Id(UUID sessionId, UUID userId);
}
