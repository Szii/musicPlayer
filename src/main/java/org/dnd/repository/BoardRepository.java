package org.dnd.repository;

import org.dnd.model.BoardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<BoardEntity, Long> {

    List<BoardEntity> findByOwner_Id(Long ownerId);

    Optional<BoardEntity> findByIdAndOwner_Id(Long boardId, Long ownerId);

    boolean existsByIdAndOwner_Id(Long boardId, Long ownerId);

    @Modifying
    @Query("update BoardEntity b set b.selectedGroup = null where b.selectedGroup.id = :groupId")
    void clearSelectedGroupFromBoards(@Param("groupId") Long groupId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BoardEntity b
            set b.selectedTrack = null
            where b.selectedTrack.id = :trackId
            """)
    void clearSelectedTrackFromAllBoards(@Param("trackId") Long trackId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BoardEntity b
            set b.selectedTrack = null
            where b.selectedTrack.id = :trackId
              and b.owner.id = :ownerId
            """)
    void clearSelectedTrackFromBoardsOwnedByUser(@Param("trackId") Long trackId,
                                                 @Param("ownerId") Long ownerId);

}
