package org.dnd.playback;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.api.model.*;
import org.dnd.board.BoardEntity;
import org.dnd.board.BoardRepository;
import org.dnd.security.JwtService;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserHelper;
import org.dnd.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("PlaybackController Tests")
class PlaybackControllerTest extends DatabaseBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private BoardRepository boardRepository;

  @Autowired
  private TrackRepository trackRepository;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private PlaybackService playbackService;

  @Autowired
  private SessionRepository sessionRepository;

  private String authToken;
  private BoardEntity board;

  private static final String BASE_URL = "/api/v1/boards/{boardId}";

  @BeforeEach
  void setUp() {
    UserEntity testUser = UserHelper.createValidatedUser(
            "testUser",
            "password",
            "email@email.com");
    testUser = userRepository.save(testUser);

    SessionEntity testSession = new SessionEntity();
    testSession.setName("Test Session");
    testSession.setOwner(testUser);
    testSession = sessionRepository.save(testSession);


    var userAuth = new UserAuthDTO();
    userAuth.setId(testUser.getId());
    userAuth.setName(testUser.getName());
    userAuth.setEmail(testUser.getEmail());

    authToken = jwtService.generateToken(userAuth);

    TrackEntity track = new TrackEntity();
    track.setTrackName("Selected Track");
    track.setTrackLink("someLink");
    track.setTrackOriginalName("Selected Track");
    track.setOwner(testUser);
    track = trackRepository.save(track);

    board = new BoardEntity();
    board.setOwner(testUser);
    board.setName("Test Board");
    board.setVolume(50);
    board.setRepeat(false);
    board.setOverplay(false);
    board.setSelectedTrack(track);
    board.setSession(testSession);
    board = boardRepository.save(board);
  }

  @Test
  @DisplayName("getBoardPlaybackState - should return playback state")
  void getBoardPlaybackState_Success() throws Exception {
    PlaybackState state = new PlaybackState();
    state.setBoardId(board.getId());
    state.setStatus(PlaybackStatus.STOPPED);

    when(playbackService.getState(board.getId())).thenReturn(state);

    mockMvc.perform(get(BASE_URL + "/playback/state", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.boardId").value(board.getId().intValue()))
            .andExpect(jsonPath("$.status").value("STOPPED"));
  }

  @Test
  @DisplayName("getBoardPlaybackState - should return 403 when forbidden")
  void getBoardPlaybackState_Forbidden() throws Exception {
    when(playbackService.getState(board.getId()))
            .thenThrow(new ResponseStatusException(FORBIDDEN));

    mockMvc.perform(get(BASE_URL + "/playback/state", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("getBoardPlaybackState - should return 404 when board not found")
  void getBoardPlaybackState_NotFound() throws Exception {
    when(playbackService.getState(board.getId()))
            .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

    mockMvc.perform(get(BASE_URL + "/playback/state", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("playBoard - should start playback")
  void playBoard_Success() throws Exception {
    PlaybackState state = new PlaybackState();
    state.setBoardId(board.getId());
    state.setStatus(PlaybackStatus.BUFFERING);

    PlayRequest request = new PlayRequest();
    request.setWindowId(null);

    when(playbackService.playBoard(board.getId(), request)).thenReturn(state);

    mockMvc.perform(post(BASE_URL + "/playback/play", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BUFFERING"));
  }

  @Test
  @DisplayName("playBoard - should return 409 when no track selected")
  void playBoard_NoTrack() throws Exception {
    PlayRequest request = new PlayRequest();

    when(playbackService.playBoard(board.getId(), request))
            .thenThrow(new ResponseStatusException(CONFLICT, "No track selected"));

    mockMvc.perform(post(BASE_URL + "/playback/play", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("playBoard - should return 403 when track not accessible")
  void playBoard_TrackNotAccessible() throws Exception {
    PlayRequest request = new PlayRequest();

    when(playbackService.playBoard(board.getId(), request))
            .thenThrow(new ResponseStatusException(FORBIDDEN, "Track not accessible"));

    mockMvc.perform(post(BASE_URL + "/playback/play", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("stopBoard - should stop playback")
  void stopBoard_Success() throws Exception {
    PlaybackState state = new PlaybackState();
    state.setBoardId(board.getId());
    state.setStatus(PlaybackStatus.STOPPED);

    when(playbackService.stop(board.getId())).thenReturn(state);

    mockMvc.perform(post(BASE_URL + "/playback/stop", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("STOPPED"));
  }

  @Test
  @DisplayName("stopBoard - should return 409 when already stopped")
  void stopBoard_AlreadyStopped() throws Exception {
    when(playbackService.stop(board.getId()))
            .thenThrow(new ResponseStatusException(CONFLICT, "Already stopped"));

    mockMvc.perform(post(BASE_URL + "/playback/stop", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("pauseBoard - should pause playback")
  void pauseBoard_Success() throws Exception {
    PlaybackState state = new PlaybackState();
    state.setBoardId(board.getId());
    state.setStatus(PlaybackStatus.PAUSED);

    when(playbackService.pause(board.getId())).thenReturn(state);

    mockMvc.perform(post(BASE_URL + "/playback/pause", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED"));
  }

  @Test
  @DisplayName("pauseBoard - should return 409 when not playing")
  void pauseBoard_NotPlaying() throws Exception {
    when(playbackService.pause(board.getId()))
            .thenThrow(new ResponseStatusException(CONFLICT, "Not playing"));

    mockMvc.perform(post(BASE_URL + "/playback/pause", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("resumeBoard - should resume playback")
  void resumeBoard_Success() throws Exception {
    PlaybackState state = new PlaybackState();
    state.setBoardId(board.getId());
    state.setStatus(PlaybackStatus.PLAYING);

    when(playbackService.resume(board.getId())).thenReturn(state);

    mockMvc.perform(post(BASE_URL + "/playback/resume", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PLAYING"));
  }

  @Test
  @DisplayName("resumeBoard - should return 409 when not paused")
  void resumeBoard_NotPaused() throws Exception {
    when(playbackService.resume(board.getId()))
            .thenThrow(new ResponseStatusException(CONFLICT, "Not paused"));

    mockMvc.perform(post(BASE_URL + "/playback/resume", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("seekBoard - should seek to position")
  void seekBoard_Success() throws Exception {
    PlaybackState state = new PlaybackState();
    state.setBoardId(board.getId());
    state.setStatus(PlaybackStatus.PLAYING);

    SeekRequest request = new SeekRequest();
    request.setPositionS(30L);

    when(playbackService.seek(board.getId(), request)).thenReturn(state);

    mockMvc.perform(post(BASE_URL + "/playback/seek", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PLAYING"));
  }

  @Test
  @DisplayName("seekBoard - should return 409 when no track playing")
  void seekBoard_NoTrackPlaying() throws Exception {
    SeekRequest request = new SeekRequest();
    request.setPositionS(30L);

    when(playbackService.seek(board.getId(), request))
            .thenThrow(new ResponseStatusException(CONFLICT, "No track playing"));

    mockMvc.perform(post(BASE_URL + "/playback/seek", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
  }
}
