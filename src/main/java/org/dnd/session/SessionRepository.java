package org.dnd.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
  List<SessionEntity> findByOwner_Id(UUID userId);

  long countByOwner_Id(UUID userId);

  Optional<SessionEntity> findByIdAndOwner_Id(UUID sessionId, UUID userId);
}
