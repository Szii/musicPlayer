package org.dnd.repository;

import org.dnd.model.BoardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<BoardEntity, Long> {

    List<BoardEntity> findByOwner_Id(Long ownerId);

    Optional<BoardEntity> findByIdAndOwner_Id(Long boardId, Long ownerId);

    boolean existsByIdAndOwner_Id(Long boardId, Long ownerId);
}
