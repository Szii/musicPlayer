package org.dnd.repository;

import org.dnd.model.UserTrackShareEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTrackShareRepository extends JpaRepository<UserTrackShareEntity, Long> {
    List<UserTrackShareEntity> findByTrack_Id(Long trackId);

    List<UserTrackShareEntity> findByUser_Id(Long userId);

    Optional<UserTrackShareEntity> findByUser_IdAndTrack_Id(Long userId, Long trackId);

    boolean existsByUser_IdAndTrack_Id(Long userId, Long trackId);

    void deleteByUser_IdAndTrack_Id(Long userId, Long trackId);
}

