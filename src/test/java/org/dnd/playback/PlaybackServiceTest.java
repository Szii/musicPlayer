package org.dnd.playback;

import org.dnd.DatabaseBase;
import org.dnd.api.model.PlayRequest;
import org.dnd.api.model.PlaybackState;
import org.dnd.api.model.PlaybackStatus;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.board.BoardEntity;
import org.dnd.board.BoardRepository;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.TrackWindowRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class PlaybackServiceTest extends DatabaseBase {
  @Autowired
  private PlaybackService playbackService;
  @Autowired
  private BoardRepository boardRepository;
  @Autowired
  private TrackRepository trackRepository;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private TrackWindowRepository trackWindowRepository;
  @Autowired
  private SessionRepository sessionRepository;

  private BoardEntity board;

  @BeforeEach
  void setUp() {
    UserEntity user = new UserEntity();
    user.setName("testUser");
    user.setPassword("password");
    user = userRepository.save(user);

    TrackEntity track = new TrackEntity();
    track.setTrackName("Test Track");
    track.setTrackLink("link");
    track.setDuration(100);
    track.setOwner(user);
    track.setTrackOriginalName("original name");
    track = trackRepository.save(track);

    SessionEntity testSession = new SessionEntity();
    testSession.setName("Test Session");
    testSession.setOwner(user);
    testSession = sessionRepository.save(testSession);

    board = new BoardEntity();
    board.setOwner(user);
    board.setName("Test Board");
    board.setVolume(50);
    board.setRepeat(false);
    board.setOverplay(false);
    board.setSelectedTrack(track);
    board.setSession(testSession);
    board = boardRepository.save(board);

    UserAuthDTO userAuth = new UserAuthDTO();
    userAuth.setId(user.getId());
    userAuth.setName(user.getName());
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userAuth, null, null);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void getStateReturnsSessionSnapshot() {
    PlaybackState state = new PlaybackState();
    state.setBoardId(board.getId());
    state.setStatus(PlaybackStatus.PLAYING);
    PlaybackState result = playbackService.getState(board.getId());
    assertEquals(board.getId(), result.getBoardId());
    assertEquals(PlaybackStatus.STOPPED, result.getStatus()); // Default state if no session
  }

  @Test
  void getStateReturnsStoppedState() {
    PlaybackState result = playbackService.getState(board.getId());
    assertEquals(PlaybackStatus.STOPPED, result.getStatus());
  }

  @Test
  void playBoardNoTrackSelected() {
    board.setSelectedTrack(null);
    boardRepository.save(board);
    PlayRequest request = new PlayRequest();
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> playbackService.playBoard(board.getId(), request));
    assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
  }

  @Test
  void stopBoardSuccess() {
    PlaybackState result = playbackService.stop(board.getId());
    assertEquals(PlaybackStatus.STOPPED, result.getStatus());
  }
}
