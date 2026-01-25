package org.dnd.repository;

import org.dnd.model.TrackShareEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrackShareRepository extends JpaRepository<TrackShareEntity, Long> {
    Optional<TrackShareEntity> findByShareCode(String shareCode);
}
