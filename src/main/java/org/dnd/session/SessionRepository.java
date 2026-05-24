package org.dnd.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {
  List<SessionEntity> findByOwner_Id(Long userId);

  long countByOwner_Id(Long userId);

  Optional<SessionEntity> findByIdAndOwner_Id(Long sessionId, Long userId);
}
