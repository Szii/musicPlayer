package org.dnd.repository;

import jakarta.transaction.Transactional;
import org.dnd.model.BoardEntity;
import org.dnd.model.UserEntity;
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


    @Test
    @Transactional
    void saveAndFindBoardsByOwner() {
        UserEntity owner = new UserEntity();
        owner.setName("boardOwner");
        owner.setPassword("pw");
        owner = userRepository.save(owner);

        BoardEntity board1 = new BoardEntity();
        board1.setOwner(owner);
        board1.setVolume(50);
        board1.setRepeat(false);
        board1.setCurrentPosition(0);
        board1.setOverplay(false);

        BoardEntity board2 = new BoardEntity();
        board2.setOwner(owner);
        board2.setVolume(80);
        board2.setRepeat(true);
        board2.setCurrentPosition(10);
        board2.setOverplay(true);

        boardRepository.save(board1);
        boardRepository.save(board2);

        List<BoardEntity> boards = boardRepository.findByOwner_Id(owner.getId());

        assertThat(boards).hasSize(2);
        assertThat(boardRepository.existsByIdAndOwner_Id(board1.getId(), owner.getId())).isTrue();
    }
}
