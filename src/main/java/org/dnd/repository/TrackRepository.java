package org.dnd.repository;

import org.dnd.model.TrackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<TrackEntity, Long> {

    List<TrackEntity> findByOwner_Id(Long ownerId);

    List<TrackEntity> findByGroup_Id(Long groupId);

    boolean existsByIdAndOwner_Id(Long trackId, Long ownerId);

    Optional<TrackEntity> findByIdAndOwner_Id(Long trackId, Long ownerId);

    @Query("""
            select distinct t
            from TrackEntity t
            left join t.userAccesses uta
            where t.owner.id = :userId
               or uta.user.id = :userId
            """)
    List<TrackEntity> findAccessibleTracksForUser(@Param("userId") Long userId);
}
