package org.dnd.board;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<BoardEntity, UUID> {

  List<BoardEntity> findByOwner_Id(UUID ownerId);

  Optional<BoardEntity> findByIdAndOwner_Id(UUID boardId, UUID ownerId);

  boolean existsByIdAndOwner_Id(UUID boardId, UUID ownerId);

  @Modifying
  @Query("update BoardEntity b set b.selectedGroup = null where b.selectedGroup.id = :groupId")
  void clearSelectedGroupFromBoards(@Param("groupId") UUID groupId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
          update BoardEntity b
          set b.selectedTrack = null, b.selectedWindow = null
          where b.selectedTrack.id = :trackId
          """)
  void clearSelectedTrackFromAllBoards(@Param("trackId") UUID trackId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
          update BoardEntity b
          set b.selectedTrack = null, b.selectedWindow = null
          where b.selectedTrack.id = :trackId
            and b.owner.id = :ownerId
          """)
  void clearSelectedTrackFromBoardsOwnedByUser(@Param("trackId") UUID trackId,
                                               @Param("ownerId") UUID ownerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
          update BoardEntity b
          set b.selectedTrack = null, b.selectedWindow = null
          where b.selectedTrack.id = :trackId
            and b.owner.id != :ownerId
          """)
  void clearSelectedTrackFromAllBoardsNotOwnedByUser(
          @Param("trackId") UUID trackId,
          @Param("ownerId") UUID ownerId
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
          update BoardEntity b
          set b.selectedWindow = null
          where b.selectedWindow.id in (
            select tw.id
            from TrackWindowEntity tw
            where tw.track.id = :trackId
          )
          """)
  int clearSelectedWindowForTrack(@Param("trackId") UUID trackId);

}
