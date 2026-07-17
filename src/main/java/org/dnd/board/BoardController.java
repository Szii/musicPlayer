package org.dnd.board;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dnd.api.MusicBoardsApi;
import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.api.model.SessionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.dnd.configuration.limiting.RateLimitNames.*;

@RequestMapping("/api/v1")
@Tag(name = "Boards", description = "Music boards management endpoints")
@RestController
@Validated
@RequiredArgsConstructor
public class BoardController implements MusicBoardsApi {
  private final BoardService boardService;
  private final BoardSessionApplicationService boardSessionApplicationService;

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
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
          name = CREATE_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> createUserBoard(BoardCreateRequest boardRequest) {
    return ResponseEntity.ok(boardSessionApplicationService.createBoardAndReturnSession(boardRequest));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<SessionResponse> deleteUserBoard(UUID boardId) {
    return ResponseEntity.ok(boardSessionApplicationService.deleteBoardAndReturnSession(boardId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Board> getUserBoard(UUID boardId) throws Exception {
    return ResponseEntity.ok(boardService.getUserBoard(boardId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Board> updateUserBoard(UUID boardId, BoardUpdateRequest boardRequest) {
    return ResponseEntity.ok(boardService.updateUserBoard(boardId, boardRequest));
  }
}

