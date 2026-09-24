package org.dnd.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
  List<SessionEntity> findByOwner_Id(UUID userId);

  long countByOwner_Id(UUID userId);

  Optional<SessionEntity> findByIdAndOwner_Id(UUID sessionId, UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
          delete from session_tracks st
          using sessions s
          where st.session_id = s.id
            and st.track_id = :trackId
            and s.owner_id = :ownerId
          """, nativeQuery = true)
  int removeTrackFromSessionsOwnedByUser(@Param("trackId") UUID trackId,
                                         @Param("ownerId") UUID ownerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
          delete from session_tracks st
          using sessions s
          where st.session_id = s.id
            and st.track_id = :trackId
            and s.owner_id <> :ownerId
          """, nativeQuery = true)
  int removeTrackFromSessionsNotOwnedByUser(@Param("trackId") UUID trackId,
                                            @Param("ownerId") UUID ownerId);
}
