package org.dnd.repository;

import org.dnd.model.TrackWindowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackWindowRepository extends JpaRepository<TrackWindowEntity, Long> {

    List<TrackWindowEntity> findByTrack_Id(Long trackId);

    Optional<TrackWindowEntity> findByIdAndTrack_Id(Long windowId, Long trackId);

    void deleteByIdAndTrack_Id(Long windowId, Long trackId);
}
