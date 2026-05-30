package org.dnd.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.exception.ErrorCode;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.security.JwtService;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.TrackWindowEntity;
import org.dnd.track.trackShare.TrackShareEntity;
import org.dnd.user.UserEntity;
import org.dnd.user.UserHelper;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankLimits;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoardControllerTest extends DatabaseBase {
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private BoardRepository boardRepository;
  @Autowired
  private JwtService jwtService;
  @Autowired
  private TrackRepository trackRepository;
  @Autowired
  private GroupRepository groupRepository;
  @Autowired
  private SessionRepository sessionRepository;

  private SessionEntity testSession;

  private UserEntity testUser;
  private UserEntity anotherUser;
  private String authToken;

  @BeforeEach
  void setUp() {
    testUser = UserHelper.createValidatedUser("testUser", "password", "email@email.cz");
    testUser = userRepository.save(testUser);

    anotherUser = UserHelper.createValidatedUser("anotherUser", "password", "anotheremail@email.cz");
    anotherUser = userRepository.save(anotherUser);

    testSession = new SessionEntity();
    testSession.setName("Test Session");
    testSession.setDescription("Test session description");
    testSession.setOwner(testUser);
    testSession = sessionRepository.save(testSession);

    UserAuthDTO userAuth = new UserAuthDTO();
    userAuth.setId(testUser.getId());
    userAuth.setName(testUser.getName());
    userAuth.setName(testUser.getEmail());
    authToken = jwtService.generateToken(userAuth);
  }

  @Test
  void getUserBoards_Success() throws Exception {
    BoardEntity board = new BoardEntity();
    board.setOwner(testUser);
    board.setName("Test name");
    board.setVolume(50);
    board.setRepeat(false);
    board.setOverplay(false);
    board.setSession(testSession);
    boardRepository.save(board);

    mockMvc.perform(get("/api/v1/boards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].volume").value(50))
            .andExpect(jsonPath("$[0].name").value("Test name"))
            .andExpect(jsonPath("$[0].repeat").value(false))
            .andExpect(jsonPath("$[0].overplay").value(false));
  }

  @Test
  void createUserBoard_Success() throws Exception {
    BoardCreateRequest request = new BoardCreateRequest()
            .volume(75)
            .repeat(true)
            .overplay(false)
            .sessionId(testSession.getId())
            .name("Test Board");

    mockMvc.perform(post("/api/v1/boards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(testSession.getId()))
            .andExpect(jsonPath("$.boards").isArray())
            .andExpect(jsonPath("$.boards.length()").value(1))
            .andExpect(jsonPath("$.boards[0].name").value("Test Board"))
            .andExpect(jsonPath("$.boards[0].volume").value(75))
            .andExpect(jsonPath("$.boards[0].repeat").value(true))
            .andExpect(jsonPath("$.boards[0].overplay").value(false));

    assertFalse(boardRepository.findByOwner_Id(testUser.getId()).isEmpty());
  }

  @Test
  void updateUserBoard_Success() throws Exception {
    BoardEntity board = new BoardEntity();
    board.setName("Original Board");
    board.setOwner(testUser);
    board.setVolume(50);
    board.setRepeat(false);
    board.setOverplay(false);
    board.setSession(testSession);
    board = boardRepository.save(board);

    BoardUpdateRequest updateRequest = new BoardUpdateRequest()
            .volume(100)
            .repeat(true)
            .overplay(true);

    mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Original Board"))
            .andExpect(jsonPath("$.volume").value(100))
            .andExpect(jsonPath("$.repeat").value(true))
            .andExpect(jsonPath("$.overplay").value(true));
  }

  @Test
  void deleteUserBoard_Success() throws Exception {
    BoardEntity board = new BoardEntity();
    board.setName("Board to Delete");
    board.setOwner(testUser);
    board.setVolume(50);
    board.setSession(testSession);
    board = boardRepository.save(board);

    mockMvc.perform(delete("/api/v1/boards/{boardId}", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.boards").isEmpty());

    assertFalse(boardRepository.existsById(board.getId()));
  }

  @Test
  void getUserBoards_EmptyList() throws Exception {
    mockMvc.perform(get("/api/v1/boards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isNoContent());
  }

  @Test
  void getUserBoards_NoAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/boards"))
            .andExpect(status().isUnauthorized());
  }

  @Test
  void getUserBoards_InvalidToken() throws Exception {
    mockMvc.perform(get("/api/v1/boards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here"))
            .andExpect(status().isForbidden());
  }

  @Test
  void getUserBoards_WrongUser() throws Exception {
    UserEntity otherUser = UserHelper.createValidatedUser("otherUser", "password", "otheruser@email.com");
    otherUser = userRepository.save(otherUser);

    BoardEntity board = new BoardEntity();
    board.setName("Other User's Board");
    board.setOwner(otherUser);
    board.setVolume(50);
    board.setSession(testSession);
    boardRepository.save(board);


    mockMvc.perform(get("/api/v1/boards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + "wrongUserToken"))
            .andExpect(status().isForbidden());
  }


  @Test
  void updateUserBoard_NotFound() throws Exception {
    BoardUpdateRequest updateRequest = new BoardUpdateRequest()
            .volume(100)
            .repeat(true);

    mockMvc.perform(put("/api/v1/boards/{boardId}", 999L)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isNotFound());
  }

  @Test
  void createUserBoard_isForbidden_WhenBoardLimitReached() throws Exception {
    for (int i = 0; i < UserRankLimits.normal().maxGroups(); i++) {
      BoardEntity board = new BoardEntity();
      board.setName("Board " + i);
      board.setOwner(testUser);
      board.setVolume(50);
      board.setSession(testSession);
      boardRepository.save(board);
    }

    BoardCreateRequest request = new BoardCreateRequest()
            .volume(75)
            .repeat(true)
            .overplay(false)
            .sessionId(testSession.getId())
            .name("Excess Board");

    mockMvc.perform(post("/api/v1/boards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.LIMIT_EXCEEDED.getCode()));
  }

  @Test
  void getUserBoards_NoBoards() throws Exception {
    mockMvc.perform(get("/api/v1/boards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isNoContent());
  }

  @Test
  void deleteUserBoard_NotFound() throws Exception {
    mockMvc.perform(delete("/api/v1/boards/{boardId}", 999L)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isNotFound());
  }

  @Test
  void createUserBoard_InvalidRequest() throws Exception {
    BoardCreateRequest request = new BoardCreateRequest()
            .volume(-10) // Invalid volume
            .repeat(true)
            .overplay(false)
            .name("Invalid Board");

    mockMvc.perform(post("/api/v1/boards")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
  }

  @Test
  void updateUserBoard_InvalidRequest() throws Exception {
    BoardEntity board = new BoardEntity();
    board.setName("Original Board");
    board.setOwner(testUser);
    board.setVolume(50);
    board.setRepeat(false);
    board.setOverplay(false);
    board.setSession(testSession);
    board = boardRepository.save(board);

    BoardUpdateRequest updateRequest = new BoardUpdateRequest()
            .volume(150); // Invalid volume

    mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isBadRequest());
  }

  @Test
  void getTracksForBoard_SuccessForSelectedGroup() throws Exception {
    BoardEntity board = new BoardEntity();
    board.setOwner(testUser);
    board.setName("Test Board");
    board.setVolume(50);
    board.setRepeat(false);
    board.setSession(testSession);
    board.setOverplay(false);


    TrackEntity trackInGroup = new TrackEntity();
    trackInGroup.setTrackName("Track In Group");
    trackInGroup.setTrackLink("https://example.com/test.mp3");
    trackInGroup.setDuration(180);
    trackInGroup.setTrackOriginalName("original name");
    trackInGroup.setOwner(testUser);

    TrackEntity trackWhichIsNotInGroup = new TrackEntity();
    trackWhichIsNotInGroup.setTrackName("Track not In Group");
    trackWhichIsNotInGroup.setTrackLink("https://example.com/test.mp3");
    trackWhichIsNotInGroup.setDuration(180);
    trackWhichIsNotInGroup.setTrackOriginalName("original name");
    trackWhichIsNotInGroup.setOwner(testUser);

    GroupEntity group = new GroupEntity();
    group.setListName("Test Group");
    group.setOwner(testUser);
    group.setTracks(Set.of(trackInGroup));
    group = groupRepository.save(group);
    board.setSelectedGroup(group);

    boardRepository.save(board);

    BoardUpdateRequest updateRequest = new BoardUpdateRequest()
            .volume(100)
            .selectedGroupId(group.getId())
            .repeat(true)
            .overplay(true);

    mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableTracks.length()").value(1))
            .andExpect(jsonPath("$.availableTracks[0].id").value(trackInGroup.getId()));

  }

  @Test
  void updateBoard_SuccessForSharedTrack() throws Exception {
    BoardEntity board = new BoardEntity();
    board.setOwner(testUser);
    board.setName("Test Board");
    board.setVolume(50);
    board.setRepeat(false);
    board.setSession(testSession);
    board.setOverplay(false);

    TrackShareEntity share = new TrackShareEntity();
    share.setShareCode("abcd");
    share.getUsers().add(testUser);

    TrackEntity trackInGroup = new TrackEntity();
    trackInGroup.setTrackName("Track In Group");
    trackInGroup.setTrackLink("https://example.com/test.mp3");
    trackInGroup.setDuration(180);
    trackInGroup.setTrackOriginalName("original name");
    trackInGroup.setOwner(anotherUser);
    trackInGroup.setTrackShare(share);

    TrackWindowEntity trackWindow = new TrackWindowEntity();
    trackWindow.setPositionFrom(0L);
    trackWindow.setPositionTo(20L);
    trackWindow.setFadeIn(false);
    trackWindow.setFadeOut(false);
    trackWindow.setName("trackWindow");

    trackInGroup.addTrackWindow(trackWindow);

    GroupEntity group = new GroupEntity();
    group.setListName("Test Group");
    group.setOwner(testUser);
    group.setTracks(Set.of(trackInGroup));
    group = groupRepository.save(group);
    board.setSelectedGroup(group);

    boardRepository.save(board);
    
    BoardUpdateRequest updateRequest = new BoardUpdateRequest()
            .volume(100)
            .selectedGroupId(group.getId())
            .selectedTrackId(trackInGroup.getId())
            .selectedWindowId(trackWindow.getId())
            .repeat(true)
            .overplay(true);

    mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableTracks.length()").value(1))
            .andExpect(jsonPath("$.availableTracks[0].id").value(trackInGroup.getId()))
            .andExpect(jsonPath("$.selectedTrack.trackName").value(trackInGroup.getTrackName()))
            .andExpect(jsonPath("$.selectedWindow.name").value(trackWindow.getName()));

  }

  @Test
  void updateBoard_FailForNotExistingWindowForTrack() throws Exception {
    BoardEntity board = new BoardEntity();
    board.setOwner(testUser);
    board.setName("Test Board");
    board.setVolume(50);
    board.setRepeat(false);
    board.setSession(testSession);
    board.setOverplay(false);

    TrackEntity trackWithWindow = new TrackEntity();
    trackWithWindow.setTrackName("Track1");
    trackWithWindow.setTrackLink("https://example.com/test.mp3");
    trackWithWindow.setDuration(180);
    trackWithWindow.setTrackOriginalName("original name");
    trackWithWindow.setOwner(testUser);

    TrackEntity trackWithoutWindow = new TrackEntity();
    trackWithoutWindow.setTrackName("Track2");
    trackWithoutWindow.setTrackLink("https://example.com/test.mp3");
    trackWithoutWindow.setDuration(180);
    trackWithoutWindow.setTrackOriginalName("original name");
    trackWithoutWindow.setOwner(testUser);

    TrackWindowEntity trackWindow = new TrackWindowEntity();
    trackWindow.setPositionFrom(0L);
    trackWindow.setPositionTo(20L);
    trackWindow.setFadeIn(false);
    trackWindow.setFadeOut(false);
    trackWindow.setName("trackWindow");

    trackWithWindow.addTrackWindow(trackWindow);

    trackRepository.saveAll(List.of(trackWithWindow, trackWithoutWindow));

    boardRepository.save(board);


    BoardUpdateRequest updateRequest = new BoardUpdateRequest()
            .volume(100)
            .selectedTrackId(trackWithoutWindow.getId())
            .selectedWindowId(trackWindow.getId())
            .repeat(true)
            .overplay(true);

    mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isNotFound());
  }

  @Test
  void getTracksForBoard_SuccessWhenGroupIsNotSelected() throws Exception {
    BoardEntity board = new BoardEntity();
    board.setOwner(testUser);
    board.setName("Test Board");
    board.setVolume(50);
    board.setRepeat(false);
    board.setSession(testSession);
    board.setOverplay(false);


    TrackEntity trackInGroup = new TrackEntity();
    trackInGroup.setTrackName("Track In Group");
    trackInGroup.setTrackLink("https://example1.com/test.mp3");
    trackInGroup.setDuration(180);
    trackInGroup.setTrackOriginalName("original name in group");
    trackInGroup.setOwner(testUser);

    TrackEntity trackWhichIsNotInGroup = new TrackEntity();
    trackWhichIsNotInGroup.setTrackName("Track not In Group");
    trackWhichIsNotInGroup.setTrackLink("https://example2.com/test.mp3");
    trackWhichIsNotInGroup.setDuration(50);
    trackWhichIsNotInGroup.setTrackOriginalName("original name not in group");
    trackWhichIsNotInGroup.setOwner(testUser);

    trackWhichIsNotInGroup = trackRepository.save(trackWhichIsNotInGroup);

    GroupEntity group = new GroupEntity();
    group.setListName("Test Group");
    group.setOwner(testUser);
    group.setTracks(Set.of(trackInGroup));
    board.setSelectedGroup(null);
    groupRepository.save(group);
    boardRepository.save(board);

    BoardUpdateRequest updateRequest = new BoardUpdateRequest()
            .volume(100)
            .repeat(true)
            .overplay(true);

    mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableTracks.length()").value(2))
            .andExpect(jsonPath("$.availableTracks[*].id").value(
                    Matchers.containsInAnyOrder(
                            trackInGroup.getId().intValue(),
                            trackWhichIsNotInGroup.getId().intValue()
                    )
            ));


  }


}