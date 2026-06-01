package org.dnd.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackWindowRepository extends JpaRepository<TrackWindowEntity, Long> {

  List<TrackWindowEntity> findByTrack_Id(Long trackId);

  Optional<TrackWindowEntity> findByIdAndTrack_Id(Long windowId, Long trackId);

  void deleteByIdAndTrack_Id(Long windowId, Long trackId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
          update BoardEntity b
          set b.selectedWindow = null
          where b.selectedWindow.id = :windowId
          """)
  void clearSelectedWindowFromAllBoards(@Param("windowId") Long windowId);

}
