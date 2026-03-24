package org.dnd.repository;

import jakarta.transaction.Transactional;
import org.dnd.model.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<GroupEntity, Long> {

    List<GroupEntity> findByOwner_Id(Long ownerId);

    Optional<GroupEntity> findByIdAndOwner_Id(Long groupId, Long ownerId);

    boolean existsByIdAndOwner_Id(Long groupId, Long ownerId);

    @Query("""
            select distinct g
            from GroupEntity g
            where g.owner.id = :userId
            """)
    List<GroupEntity> findAccessibleGroupsForUser(@Param("userId") Long userId);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            insert into group_tracks (group_id, track_id)
            values (:groupId, :trackId)
            on conflict do nothing
            """, nativeQuery = true)
    void addTrackToGroup(@Param("groupId") Long groupId,
                         @Param("trackId") Long trackId);

    @Query("""
            select distinct g
            from GroupEntity g
            join g.tracks t
            where t.id = :trackId
            """)
    List<GroupEntity> findAllContainingTrack(@Param("trackId") Long trackId);

    @Query("""
            select distinct g
            from GroupEntity g
            join g.tracks t
            where t.id = :trackId
              and g.owner.id = :ownerId
            """)
    List<GroupEntity> findAllContainingTrackOwnedByUser(@Param("trackId") Long trackId,
                                                        @Param("ownerId") Long ownerId);
    
}
