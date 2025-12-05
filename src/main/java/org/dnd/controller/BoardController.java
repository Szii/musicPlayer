package org.dnd.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.dnd.api.MusicBoardsApi;
import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/api/v1")
@Tag(name = "Boards", description = "Music boards management endpoints")
@RestController
@Validated
public class BoardController implements MusicBoardsApi {

    @Override
    public ResponseEntity<List<Board>> getUserBoards(Long userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Board> createUserBoard(Long userId, BoardCreateRequest boardRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> deleteUserBoard(Long userId, Long boardId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Board> updateUserBoard(Long userId, Long boardId, BoardUpdateRequest boardRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}

