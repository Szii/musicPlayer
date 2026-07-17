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

  Optional<GroupEntity> findByIdAndOwner_Id(UUID groupId, UUID ownerId);

  boolean existsByIdAndOwner_Id(UUID groupId, UUID ownerId);

  @Query("""
          select distinct g
          from GroupEntity g
          where g.owner.id = :userId
          """)
  List<GroupEntity> findAccessibleGroupsForUser(@Param("userId") UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(value = """
          insert into group_tracks (group_id, track_id)
          values (:groupId, :trackId)
          on conflict do nothing
          """, nativeQuery = true)
  void addTrackToGroup(@Param("groupId") UUID groupId,
                       @Param("trackId") UUID trackId);

  @Query("""
          select distinct g
          from GroupEntity g
          join g.tracks t
          where t.id = :trackId
          """)
  List<GroupEntity> findAllContainingTrack(@Param("trackId") UUID trackId);

  @Query("""
          select distinct g
          from GroupEntity g
          join g.tracks t
          where t.id = :trackId
            and g.owner.id = :ownerId
          """)
  List<GroupEntity> findAllContainingTrackOwnedByUser(@Param("trackId") UUID trackId,
                                                      @Param("ownerId") UUID ownerId);

  @Query("""
          select distinct g
          from GroupEntity g
          join g.tracks t
          where t.id = :trackId
            and g.owner.id <> :ownerId
          """)
  List<GroupEntity> findAllContainingTrackNotOwnedByUser(@Param("trackId") UUID trackId,
                                                         @Param("ownerId") UUID ownerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(value = """
          delete from group_tracks gt
          using groups g
          where gt.group_id = g.id
            and gt.track_id = :trackId
            and g.owner_id <> :ownerId
          """, nativeQuery = true)
  int removeFromAllGroupsNotOwnedByUser(@Param("trackId") UUID trackId,
                                        @Param("ownerId") UUID ownerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(value = """
          delete from group_tracks gt
          using groups g
          where gt.group_id = g.id
            and gt.track_id = :trackId
            and g.owner_id = :ownerId
          """, nativeQuery = true)
  int removeTrackFromGroupsOwnedByUser(@Param("trackId") UUID trackId,
                                       @Param("ownerId") UUID ownerId);

}