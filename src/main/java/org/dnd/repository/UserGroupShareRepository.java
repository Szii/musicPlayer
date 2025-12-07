package org.dnd.repository;

import org.dnd.model.UserGroupShareEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGroupShareRepository extends JpaRepository<UserGroupShareEntity, Long> {
    List<UserGroupShareEntity> findByGroup_Id(Long groupId);

    List<UserGroupShareEntity> findByUser_Id(Long userId);

    Optional<UserGroupShareEntity> findByUser_IdAndGroup_Id(Long userId, Long groupId);

    boolean existsByUser_IdAndGroup_Id(Long userId, Long groupId);

    void deleteByUser_IdAndGroup_Id(Long userId, Long groupId);
}
