package org.dnd.repository;

import org.dnd.model.TrackPointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackPointRepository extends JpaRepository<TrackPointEntity, Long> {

    List<TrackPointEntity> findByTrack_Id(Long trackId);

    Optional<TrackPointEntity> findByIdAndTrack_Id(Long pointId, Long trackId);

    void deleteByIdAndTrack_Id(Long pointId, Long trackId);
}
