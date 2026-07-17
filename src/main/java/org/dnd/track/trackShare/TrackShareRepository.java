package org.dnd.track.trackShare;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackShareRepository extends JpaRepository<TrackShareEntity, UUID> {
  Optional<TrackShareEntity> findByShareCode(String shareCode);

  Boolean existsByShareCode(String shareCode);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
          delete from TrackWindowEntity tw
          where tw.track.id = :trackId
          """)
  int deleteAllByTrackId(@Param("trackId") UUID trackId);
}
