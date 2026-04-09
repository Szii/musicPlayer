package org.dnd.controller;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
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
  @RateLimiting(
          name = "default-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<List<Board>> getUserBoards() {
    List<Board> result = boardService.getUserBoards();
    if (result.isEmpty()) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(result);
  }

  @Override
  @RateLimiting(
          name = "create-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<Board> createUserBoard(BoardCreateRequest boardRequest) {
    return ResponseEntity.ok(boardService.createUserBoard(boardRequest));
  }

  @Override
  @RateLimiting(
          name = "default-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<Void> deleteUserBoard(Long boardId) {
    boardService.deleteUserBoard(boardId);
    return ResponseEntity.noContent().build();
  }

  @Override
  @RateLimiting(
          name = "default-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<Board> getUserBoard(Long boardId) throws Exception {
    return ResponseEntity.ok(boardService.getUserBoard(boardId));
  }

  @Override
  @RateLimiting(
          name = "default-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<Board> updateUserBoard(Long boardId, BoardUpdateRequest boardRequest) {
    return ResponseEntity.ok(boardService.updateUserBoard(boardId, boardRequest));
  }
}

