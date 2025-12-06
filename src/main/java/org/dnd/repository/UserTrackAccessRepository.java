package org.dnd.repository;

import org.dnd.model.UserTrackAccessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserTrackAccessRepository extends JpaRepository<UserTrackAccessEntity, Long> {

    List<UserTrackAccessEntity> findByUser_Id(Long userId);

    List<UserTrackAccessEntity> findByTrack_Id(Long trackId);

    List<UserTrackAccessEntity> findByGroup_Id(Long groupId);

    Optional<UserTrackAccessEntity> findByUser_IdAndTrack_Id(Long userId, Long trackId);

    Optional<UserTrackAccessEntity> findByUser_IdAndGroup_Id(Long userId, Long trackId);

    void deleteByUser_IdAndTrack_Id(Long userId, Long trackId);

    void deleteByTrack_Id(Long trackId);

    void deleteByGroup_Id(Long groupId);
}
