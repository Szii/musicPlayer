package org.dnd.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<TrackEntity, UUID> {

  List<TrackEntity> findByOwner_Id(UUID ownerId);

  @Query("""
          select distinct t
          from TrackEntity t
          left join t.trackShare ts
          left join ts.users u
          where t.owner.id = :userId
             or u.id = :userId
          """)
  List<TrackEntity> findAllAccessibleByUserId(@Param("userId") UUID userId);

  List<TrackEntity> findByGroups_Id(UUID groupId);

  boolean existsByIdAndOwner_Id(UUID trackId, UUID ownerId);

  Optional<TrackEntity> findByIdAndOwner_Id(UUID trackId, UUID ownerId);

  @Query("""
          select distinct t
          from TrackEntity t
          where t.owner.id = :userId
          """)
  List<TrackEntity> findAccessibleTracksForUser(@Param("userId") UUID userId);

  @Query("""
          select distinct t
          from TrackEntity t
          left join t.trackShare ts
          left join ts.users u
          where t.id = :trackId
            and (
              t.owner.id = :userId
              or u.id = :userId
            )
          """)
  Optional<TrackEntity> findAccessibleByIdAndUserId(
          @Param("trackId") UUID trackId,
          @Param("userId") UUID userId
  );

}
