package org.dnd.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<TrackEntity, UUID> {

  List<TrackEntity> findByOwner_Id(UUID ownerId);

  List<TrackEntity> findByGroupTracks_Group_Id(UUID groupId);

  boolean existsByIdAndOwner_IdAndManagedSessionIsNull(UUID trackId, UUID ownerId);

  List<TrackEntity> findByManagedSession_Id(UUID sessionId);

  Optional<TrackEntity> findByIdAndOwner_Id(UUID trackId, UUID ownerId);

  @Query("""
          select distinct t
          from TrackEntity t
          where t.owner.id = :userId
            and t.managedSession is null
          """)
  List<TrackEntity> findAccessibleTracksForUser(@Param("userId") UUID userId);

  @Query("""
          select t
          from TrackEntity t
          where t.id = :trackId
            and t.owner.id = :userId
            and t.managedSession is null
          """)
  Optional<TrackEntity> findAccessibleByIdAndUserId(
          @Param("trackId") UUID trackId,
          @Param("userId") UUID userId
  );

  @Query("""
          select count(t.id)
          from TrackEntity t
          where t.id in :trackIds
            and t.owner.id = :userId
            and t.managedSession is null
          """)
  long countAccessibleByIdsAndUserId(
          @Param("trackIds") Collection<UUID> trackIds,
          @Param("userId") UUID userId
  );

}
