package org.dnd.group;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

  List<GroupEntity> findByOwner_Id(UUID ownerId);

  Optional<GroupEntity> findByIdAndOwner_IdAndManagedSessionIsNull(UUID groupId, UUID ownerId);

  boolean existsByIdAndOwner_IdAndManagedSessionIsNull(UUID groupId, UUID ownerId);

  List<GroupEntity> findByManagedSession_Id(UUID sessionId);

  @Query("""
          select distinct g
          from GroupEntity g
          where g.owner.id = :userId
            and g.managedSession is null
          """)
  List<GroupEntity> findAccessibleGroupsForUser(@Param("userId") UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(value = """
          insert into group_tracks (group_id, track_id, position_within_group)
          values (:groupId, :trackId,
                  (select coalesce(max(position_within_group), 0) + 1
                   from group_tracks where group_id = :groupId))
          on conflict do nothing
          """, nativeQuery = true)
  void addTrackToGroup(@Param("groupId") UUID groupId,
                       @Param("trackId") UUID trackId);

  @Query("""
          select distinct g
          from GroupEntity g
          join g.groupTracks gt
          join gt.track t
          where t.id = :trackId
          """)
  List<GroupEntity> findAllContainingTrack(@Param("trackId") UUID trackId);

}