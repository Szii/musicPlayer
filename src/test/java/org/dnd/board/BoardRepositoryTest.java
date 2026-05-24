package org.dnd.board;

import org.dnd.DatabaseBase;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class BoardRepositoryTest extends DatabaseBase {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private BoardRepository boardRepository;

  @Autowired
  private SessionRepository sessionRepository;


  @Test
  void saveAndFindBoardsByOwner() {
    UserEntity owner = new UserEntity();
    owner.setName("boardOwner");
    owner.setPassword("pw");
    owner = userRepository.save(owner);


    SessionEntity session = new SessionEntity();
    session.setOwner(owner);
    session.setName("Session 1");
    sessionRepository.save(session);

    BoardEntity board1 = new BoardEntity();
    board1.setOwner(owner);
    board1.setVolume(50);
    board1.setRepeat(false);
    board1.setName("Board 1");
    board1.setSession(session);
    board1.setOverplay(false);

    BoardEntity board2 = new BoardEntity();
    board2.setOwner(owner);
    board2.setName("Board 2");
    board2.setVolume(80);
    board2.setRepeat(true);
    board2.setSession(session);
    board2.setOverplay(true);

    boardRepository.save(board1);
    boardRepository.save(board2);

    List<BoardEntity> boards = boardRepository.findByOwner_Id(owner.getId());

    assertThat(boards).hasSize(2);
    assertThat(boardRepository.existsByIdAndOwner_Id(board1.getId(), owner.getId())).isTrue();

    BoardEntity savedBoard1 = boards.stream()
            .filter(b -> b.getVolume() == 50)
            .findFirst()
            .orElseThrow();
  }
}
