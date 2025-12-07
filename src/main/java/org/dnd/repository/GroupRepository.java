package org.dnd.repository;

import org.dnd.model.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
            left join g.shares gs
            where g.owner.id = :userId
               or gs.user.id = :userId
            """)
    List<GroupEntity> findAccessibleGroupsForUser(@Param("userId") Long userId);
}
