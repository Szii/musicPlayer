package org.dnd.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dnd.api.MusicBoardsApi;
import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.service.BoardService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/api/v1")
@Tag(name = "Boards", description = "Music boards management endpoints")
@RestController
@Validated
@RequiredArgsConstructor
public class BoardController implements MusicBoardsApi {
    private final BoardService boardService;

    @Override
    public ResponseEntity<List<Board>> getUserBoards(Long userId) {
        return ResponseEntity.ok(boardService.getUserBoards(userId));
    }

    @Override
    public ResponseEntity<Board> createUserBoard(Long userId, BoardCreateRequest boardRequest) {
        return ResponseEntity.ok(boardService.createUserBoard(userId, boardRequest));
    }

    @Override
    public ResponseEntity<Void> deleteUserBoard(Long userId, Long boardId) {
        boardService.deleteUserBoard(userId, boardId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Board> updateUserBoard(Long userId, Long boardId, BoardUpdateRequest boardRequest) {
        return ResponseEntity.ok(boardService.updateUserBoard(userId, boardId, boardRequest));
    }
}

