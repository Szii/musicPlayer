package org.dnd.board;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.SessionResponse;
import org.dnd.session.SessionService;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class BoardSessionApplicationService {

  private final BoardService boardService;
  private final SessionService sessionService;

  public SessionResponse deleteBoardAndReturnSession(Long boardId) {
    Long sessionId = boardService.deleteUserBoard(boardId);
    return sessionService.getSession(sessionId);
  }

  public SessionResponse createBoardAndReturnSession(BoardCreateRequest boardRequest) {
    Long sessionId = boardService.createUserBoard(boardRequest);
    return sessionService.getSession(sessionId);
  }
}
