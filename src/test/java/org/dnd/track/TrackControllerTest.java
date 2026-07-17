package org.dnd.track;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.TestHelpers;
import org.dnd.api.model.ReorderTrackWindowsRequest;
import org.dnd.api.model.TrackRequest;
import org.dnd.api.model.TrackWindowRequest;
import org.dnd.api.model.UpdateTrackRequestV2;
import org.dnd.board.BoardEntity;
import org.dnd.board.BoardRepository;
import org.dnd.exception.ErrorCode;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserHelper;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrackControllerTest extends DatabaseBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TrackRepository trackRepository;

  @Autowired
  private GroupRepository groupRepository;

  @Autowired
  private TrackWindowRepository trackWindowRepository;

  @Autowired
  private BoardRepository boardRepository;

  @Autowired
  private SessionRepository sessionRepository;

  private UserEntity testUser;

  @BeforeEach
  void setUp() {
    testUser = createUser("testUser");
  }

  @Test
  void getUserTracks_OwnTracks_Success() throws Exception {
    TrackEntity t1 = createTrackEntity("T1", testUser, null);
    TrackEntity t2 = createTrackEntity("T2", testUser, null);

    mockMvc.perform(get("/api/v1/tracks")
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].trackName").value(containsInAnyOrder("T1", "T2")))
            .andExpect(jsonPath("$[*].ownerId").value(everyItem(is(testUser.getId()))));
  }

  @Test
  void createTrack_Success() throws Exception {
    TrackRequest req = new TrackRequest()
            .trackName("New Track")
            .trackLink("https://www.youtube.com/watch?v=gbFGnw2JYe0&list=PLDtPBNsaMdk-M7oRThTgSQm--LuxMUW4S");

    mockMvc.perform(post("/api/v1/tracks")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackName").value("New Track"));
  }

  @Test
  void updateTrack_Success_RemovesTrackWindowsFromAllBoardsButKeepsTrackSelected() throws Exception {
    UserEntity otherUser = createUser("otherUserUpdateCleanup");

    TrackEntity track = createTrackEntity("OldName", testUser, null);
    TrackWindowEntity window1 = createTrackWindow(track, "Intro", 0L, 10L, false, false);
    TrackWindowEntity window2 = createTrackWindow(track, "Loop", 10L, 20L, false, false);

    TrackEntity unrelatedTrack = createTrackEntity("Unrelated Track", testUser, null);
    TrackWindowEntity unrelatedWindow = createTrackWindow(unrelatedTrack, "Unrelated Window", 0L, 10L, false, false);

    SessionEntity ownerSession = createSession("Owner Session Update", testUser);
    SessionEntity otherUserSession = createSession("Other User Session Update", otherUser);

    BoardEntity ownerBoardWithWindow1 = createBoard(
            "Owner Board Window 1",
            testUser,
            ownerSession,
            track,
            window1
    );

    BoardEntity ownerBoardWithWindow2 = createBoard(
            "Owner Board Window 2",
            testUser,
            ownerSession,
            track,
            window2
    );

    BoardEntity otherUserBoard = createBoard(
            "Other User Board",
            otherUser,
            otherUserSession,
            track,
            window1
    );

    BoardEntity unrelatedBoard = createBoard(
            "Unrelated Board",
            testUser,
            ownerSession,
            unrelatedTrack,
            unrelatedWindow
    );

    UpdateTrackRequestV2 req = new UpdateTrackRequestV2()
            .trackName("UpdatedName")
            .trackOriginalName("aa")
            .duration(20)
            .fadeInDurationMs(5000)
            .trackLink("https://example-updated.com/x.mp3");

    mockMvc.perform(patch("/api/v1/tracks/{trackId}", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackName").value("UpdatedName"))
            .andExpect(jsonPath("$.duration").value(20))
            .andExpect(jsonPath("$.trackLink").value("https://example-updated.com/x.mp3"))
            .andExpect(jsonPath("$.fadeInDurationMs").value(5000))
            .andExpect(jsonPath("$.fadeOutDurationMs").value(1000))
            .andExpect(jsonPath("$.trackWindows").isArray())
            .andExpect(jsonPath("$.trackWindows", is(empty())));

    TrackEntity updatedTrack = trackRepository.findById(track.getId()).orElseThrow();
    assertEquals("UpdatedName", updatedTrack.getTrackName());

    assertFalse(trackWindowRepository.existsById(window1.getId()));
    assertFalse(trackWindowRepository.existsById(window2.getId()));

    BoardEntity updatedOwnerBoardWithWindow1 = boardRepository.findById(ownerBoardWithWindow1.getId()).orElseThrow();
    assertNotNull(updatedOwnerBoardWithWindow1.getSelectedTrack());
    assertEquals(track.getId(), updatedOwnerBoardWithWindow1.getSelectedTrack().getId());
    assertNull(updatedOwnerBoardWithWindow1.getSelectedWindow());

    BoardEntity updatedOwnerBoardWithWindow2 = boardRepository.findById(ownerBoardWithWindow2.getId()).orElseThrow();
    assertNotNull(updatedOwnerBoardWithWindow2.getSelectedTrack());
    assertEquals(track.getId(), updatedOwnerBoardWithWindow2.getSelectedTrack().getId());
    assertNull(updatedOwnerBoardWithWindow2.getSelectedWindow());

    BoardEntity updatedOtherUserBoard = boardRepository.findById(otherUserBoard.getId()).orElseThrow();
    assertNotNull(updatedOtherUserBoard.getSelectedTrack());
    assertEquals(track.getId(), updatedOtherUserBoard.getSelectedTrack().getId());
    assertNull(updatedOtherUserBoard.getSelectedWindow());

    BoardEntity updatedUnrelatedBoard = boardRepository.findById(unrelatedBoard.getId()).orElseThrow();
    assertNotNull(updatedUnrelatedBoard.getSelectedTrack());
    assertEquals(unrelatedTrack.getId(), updatedUnrelatedBoard.getSelectedTrack().getId());

    assertNotNull(updatedUnrelatedBoard.getSelectedWindow());
    assertEquals(unrelatedWindow.getId(), updatedUnrelatedBoard.getSelectedWindow().getId());
  }

  @Test
  void updateTrack_Success_DoNotRemoveTrackWindows_WhenUpdateDoesNotChangeLink() throws Exception {
    TrackEntity track = createTrackEntity("OldName", testUser, null);
    TrackWindowEntity window1 = createTrackWindow(track, "Intro", 0L, 10L, false, false);
    TrackWindowEntity window2 = createTrackWindow(track, "Loop", 10L, 20L, false, false);

    SessionEntity ownerSession = createSession("Owner Session Update", testUser);

    BoardEntity ownerBoardWithWindow1 = createBoard(
            "Owner Board Window 1",
            testUser,
            ownerSession,
            track,
            window1
    );

    BoardEntity ownerBoardWithWindow2 = createBoard(
            "Owner Board Window 2",
            testUser,
            ownerSession,
            track,
            window2
    );


    UpdateTrackRequestV2 req = new UpdateTrackRequestV2()
            .trackName("UpdatedName")
            .trackOriginalName("aa")
            .duration(20)
            .fadeInDurationMs(5000)
            .trackLink(track.getTrackLink());

    mockMvc.perform(patch("/api/v1/tracks/{trackId}", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackName").value("UpdatedName"))
            .andExpect(jsonPath("$.duration").value(20))
            .andExpect(jsonPath("$.trackLink").value(track.getTrackLink()))
            .andExpect(jsonPath("$.fadeInDurationMs").value(5000))
            .andExpect(jsonPath("$.fadeOutDurationMs").value(1000))
            .andExpect(jsonPath("$.trackWindows").isArray())
            .andExpect(jsonPath("$.trackWindows", hasSize(2)));

    TrackEntity updatedTrack = trackRepository.findById(track.getId()).orElseThrow();
    assertEquals("UpdatedName", updatedTrack.getTrackName());

    BoardEntity updatedOwnerBoardWithWindow1 = boardRepository.findById(ownerBoardWithWindow1.getId()).orElseThrow();
    assertNotNull(updatedOwnerBoardWithWindow1.getSelectedTrack());
    assertEquals(track.getId(), updatedOwnerBoardWithWindow1.getSelectedTrack().getId());
    assertEquals(window1.getId(), updatedOwnerBoardWithWindow1.getSelectedWindow().getId());

    BoardEntity updatedOwnerBoardWithWindow2 = boardRepository.findById(ownerBoardWithWindow2.getId()).orElseThrow();
    assertNotNull(updatedOwnerBoardWithWindow2.getSelectedTrack());
    assertEquals(track.getId(), updatedOwnerBoardWithWindow2.getSelectedTrack().getId());
    assertEquals(window2.getId(), updatedOwnerBoardWithWindow2.getSelectedWindow().getId());
  }

  @Test
  void updateTrack_Forbidden_WhenNotOwner() throws Exception {
    UserEntity owner = createUser("owner3");
    TrackEntity t = createTrackEntity("O", owner, null);

    UpdateTrackRequestV2 req = new UpdateTrackRequestV2()
            .trackName("Hack")
            .trackOriginalName("aa")
            .duration(20)
            .trackLink("https://example.com/x.mp3");

    mockMvc.perform(patch("/api/v1/tracks/{trackId}", t.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden());
  }

  @Test
  void deleteTrack_Success() throws Exception {
    TrackEntity t = createTrackEntity("Del", testUser, null);

    mockMvc.perform(delete("/api/v1/tracks/{trackId}", t.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    assertFalse(trackRepository.existsById(t.getId()));
  }

  @Test
  void deleteTrack_RemovesTrackAndSelectedWindowFromAllBoardsAndGroupsButKeepsUnrelatedBoards() throws Exception {
    UserEntity otherUser = createUser("otherUserDeleteCleanup");

    TrackEntity track = createTrackEntity("Track To Delete", testUser, null);
    TrackWindowEntity window = createTrackWindow(track, "Deleted Track Window", 0L, 10L, false, false);

    TrackEntity unrelatedTrack = createTrackEntity("Unrelated Track", testUser, null);
    TrackWindowEntity unrelatedWindow = createTrackWindow(unrelatedTrack, "Unrelated Window", 0L, 10L, false, false);

    SessionEntity ownerSession = createSession("Owner Session", testUser);
    SessionEntity otherUserSession = createSession("Other User Session", otherUser);

    BoardEntity ownerBoard = createBoard(
            "Owner Board",
            testUser,
            ownerSession,
            track,
            window
    );

    BoardEntity otherUserBoard = createBoard(
            "Other User Board",
            otherUser,
            otherUserSession,
            track,
            window
    );

    BoardEntity unrelatedBoard = createBoard(
            "Unrelated Board",
            testUser,
            ownerSession,
            unrelatedTrack,
            unrelatedWindow
    );

    GroupEntity ownerGroup = createGroup("Owner Group", testUser);
    GroupEntity otherUserGroup = createGroup("Other User Group", otherUser);
    GroupEntity unrelatedGroup = createGroup("Unrelated Group", testUser);

    groupRepository.addTrackToGroup(ownerGroup.getId(), track.getId());
    groupRepository.addTrackToGroup(otherUserGroup.getId(), track.getId());
    groupRepository.addTrackToGroup(unrelatedGroup.getId(), unrelatedTrack.getId());

    mockMvc.perform(delete("/api/v1/tracks/{trackId}", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    assertFalse(trackRepository.existsById(track.getId()));

    BoardEntity updatedOwnerBoard = boardRepository.findById(ownerBoard.getId()).orElseThrow();
    assertNull(updatedOwnerBoard.getSelectedTrack());
    assertNull(updatedOwnerBoard.getSelectedWindow());

    BoardEntity updatedOtherUserBoard = boardRepository.findById(otherUserBoard.getId()).orElseThrow();
    assertNull(updatedOtherUserBoard.getSelectedTrack());
    assertNull(updatedOtherUserBoard.getSelectedWindow());

    BoardEntity updatedUnrelatedBoard = boardRepository.findById(unrelatedBoard.getId()).orElseThrow();
    assertNotNull(updatedUnrelatedBoard.getSelectedTrack());
    assertEquals(unrelatedTrack.getId(), updatedUnrelatedBoard.getSelectedTrack().getId());

    assertNotNull(updatedUnrelatedBoard.getSelectedWindow());
    assertEquals(unrelatedWindow.getId(), updatedUnrelatedBoard.getSelectedWindow().getId());

    assertTrue(groupRepository.findAllContainingTrack(track.getId()).isEmpty());

    assertTrue(groupRepository.findAllContainingTrack(unrelatedTrack.getId())
            .stream()
            .anyMatch(group -> group.getId().equals(unrelatedGroup.getId())));
  }

  @Test
  void deleteTrack_NotFound() throws Exception {
    mockMvc.perform(delete("/api/v1/tracks/{trackId}", UUID.randomUUID())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNotFound());
  }

  @Test
  void deleteTrack_Forbidden_WhenNotOwner() throws Exception {
    UserEntity owner = createUser("owner4");
    TrackEntity t = createTrackEntity("Del2", owner, null);

    mockMvc.perform(delete("/api/v1/tracks/{trackId}", t.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isForbidden());
  }

  @Test
  void createTrackWindow_Owner_Success() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser, null);

    TrackWindowRequest req = new TrackWindowRequest()
            .name("Intro")
            .positionFrom(10)
            .positionTo(20)
            .fadeOutDurationMs(1000)
            .fadeInDurationMs(1000);

    mockMvc.perform(post("/api/v1/tracks/{trackId}/windows", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(track.getId().toString()))
            .andExpect(jsonPath("$.trackWindows").isArray())
            .andExpect(jsonPath("$.trackWindows", hasSize(1)))
            .andExpect(jsonPath("$.trackWindows[0].name").value("Intro"))
            .andExpect(jsonPath("$.trackWindows[0].positionFrom").value(10))
            .andExpect(jsonPath("$.trackWindows[0].positionWithinTrack").value(1))
            .andExpect(jsonPath("$.trackWindows[0].positionTo").value(20))
            .andExpect(jsonPath("$.trackWindows[0].fadeInDurationMs").value(1000))
            .andExpect(jsonPath("$.trackWindows[0].fadeOutDurationMs").value(1000));
  }

  @Test
  void createTrackWindow_Owner_Validation_Fail() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser, null);

    TrackWindowRequest req = new TrackWindowRequest()
            .name("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .positionFrom(10)
            .positionTo(20)
            .fadeOutDurationMs(1000)
            .fadeInDurationMs(1000);

    mockMvc.perform(post("/api/v1/tracks/{trackId}/windows", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());

  }

  @Test
  void createTrackWindow_NonOwner_Forbidden() throws Exception {
    UserEntity other = createUser("otherUser");
    TrackEntity track = createTrackEntity("Other Track", other, null);

    TrackWindowRequest req = new TrackWindowRequest()
            .name("Nope")
            .positionFrom(10)
            .positionTo(20)
            .fadeOutDurationMs(1000)
            .fadeInDurationMs(1000);

    mockMvc.perform(post("/api/v1/tracks/{trackId}/windows", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden());
  }

  @Test
  void createTrackWindow_PositionOutsideDuration_BadRequest() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser, null);

    TrackWindowRequest req = new TrackWindowRequest()
            .name("Too far")
            .positionFrom(999)
            .positionTo(500)
            .fadeOutDurationMs(1000)
            .fadeInDurationMs(1000);

    mockMvc.perform(post("/api/v1/tracks/{trackId}/windows", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
  }

  @Test
  void createTrackWindow_isForbidden_whenWindowLimitReached() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser, null);

    for (int i = 0; i < UserRankLimits.normal().maxWindows(); i++) {
      createTrackWindow(track, "Window " + i, 10L * i, 10L * (i + 1), false, false);
    }

    TrackWindowRequest req = new TrackWindowRequest()
            .name("One Too Many")
            .positionFrom(50)
            .positionTo(60)
            .fadeOutDurationMs(1000)
            .fadeInDurationMs(1000);

    mockMvc.perform(post("/api/v1/tracks/{trackId}/windows", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.LIMIT_EXCEEDED.getCode()));
  }

  @Test
  void createTrack_IsForbidden_WhenTrackLimitReached() throws Exception {
    for (int i = 0; i < UserRankLimits.normal().maxTracks(); i++) {
      createTrackEntity("Track " + i, testUser, null);
    }

    TrackRequest req = new TrackRequest()
            .trackName("One Too Many")
            .trackLink("https://example.com/too.mp3");

    mockMvc.perform(post("/api/v1/tracks")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.LIMIT_EXCEEDED.getCode()));
  }


  @Test
  void getUserTracks_TrackWindowsAreOrderedByPositionAscending() throws Exception {
    TrackEntity track = createTrackEntity("Ordered Track", testUser, null);

    createTrackWindow(track, "B", 50L, 100L, false, false);
    createTrackWindow(track, "A", 10L, 100L, false, false);
    createTrackWindow(track, "C", 90L, 100L, false, false);

    mockMvc.perform(get("/api/v1/tracks")
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].trackWindows[*].name", contains("B", "A", "C")));
  }

  @Test
  void updateTrackWindow_Owner_Success() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser, null);
    TrackWindowEntity point = createTrackWindow(track, "Intro", 10L, 20L, true, false);

    TrackWindowRequest update = new TrackWindowRequest()
            .name("Intro Updated")
            .positionFrom(20)
            .positionTo(45)
            .fadeOutDurationMs(1000)
            .fadeInDurationMs(1000);

    mockMvc.perform(patch("/api/v1/tracks/{trackId}/windows/{windowId}", track.getId(), point.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackWindows[*].id", hasItem(point.getId().toString())))
            .andExpect(jsonPath("$.trackWindows[0].name").value("Intro Updated"))
            .andExpect(jsonPath("$.trackWindows[0].positionFrom").value(20))
            .andExpect(jsonPath("$.trackWindows[0].positionTo").value(45))
            .andExpect(jsonPath("$.trackWindows[0].fadeInDurationMs").value(1000))
            .andExpect(jsonPath("$.trackWindows[0].fadeOutDurationMs").value(1000));
  }

  @Test
  void deleteTrackWindow_Owner_Success() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser, null);
    TrackWindowEntity window = createTrackWindow(track, "Intro", 10L, 20L, true, false);

    mockMvc.perform(delete("/api/v1/tracks/{trackId}/windows/{windowId}", track.getId(), window.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackWindows").isArray())
            .andExpect(jsonPath("$.trackWindows", is(empty())));
  }

  @Test
  void deleteTrackWindow_Deletes_FromAllBoards() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser, null);
    TrackWindowEntity window = createTrackWindow(track, "Intro", 10L, 20L, true, false);

    UserEntity otherUser = createUser("otherUserDeleteCleanup");

    SessionEntity ownerSession = createSession("Owner Session", testUser);
    SessionEntity otherUserSession = createSession("Other User Session", otherUser);

    BoardEntity ownerBoard = createBoard(
            "Owner Board",
            testUser,
            ownerSession,
            track,
            window
    );

    BoardEntity otherUserBoard = createBoard(
            "Other User Board",
            otherUser,
            otherUserSession,
            track,
            window
    );

    mockMvc.perform(delete("/api/v1/tracks/{trackId}/windows/{windowId}", track.getId(), window.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackWindows").isArray())
            .andExpect(jsonPath("$.trackWindows", is(empty())));

    BoardEntity updatedOwnerBoard = boardRepository.findById(ownerBoard.getId()).orElseThrow();
    assertNotNull(updatedOwnerBoard.getSelectedTrack());
    assertNull(updatedOwnerBoard.getSelectedWindow());

    BoardEntity updatedOtherUserBoard = boardRepository.findById(otherUserBoard.getId()).orElseThrow();
    assertNotNull(updatedOtherUserBoard.getSelectedTrack());
    assertNull(updatedOtherUserBoard.getSelectedWindow());
  }

  @Test
  void reorderTrackWindows_Owner_Success() throws Exception {
    TrackEntity track = createTrackEntity("Reorder Track", testUser, null);

    TrackWindowEntity first = createTrackWindow(track, "First", 10L, 20L, false, false);
    TrackWindowEntity second = createTrackWindow(track, "Second", 30L, 40L, false, false);
    TrackWindowEntity third = createTrackWindow(track, "Third", 50L, 60L, false, false);

    ReorderTrackWindowsRequest req = new ReorderTrackWindowsRequest()
            .windowIds(List.of(third.getId(), first.getId(), second.getId()));

    mockMvc.perform(patch("/api/v1/tracks/{trackId}/windows/reorder", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackWindows").isArray())
            .andExpect(jsonPath("$.trackWindows", hasSize(3)))
            .andExpect(jsonPath("$.trackWindows[*].id", contains(
                    third.getId().toString(),
                    first.getId().toString(),
                    second.getId().toString()
            )))
            .andExpect(jsonPath("$.trackWindows[*].positionWithinTrack", contains(1, 2, 3)));

    TrackWindowEntity updatedFirst = trackWindowRepository.findById(first.getId()).orElseThrow();
    TrackWindowEntity updatedSecond = trackWindowRepository.findById(second.getId()).orElseThrow();
    TrackWindowEntity updatedThird = trackWindowRepository.findById(third.getId()).orElseThrow();

    assertEquals(2, updatedFirst.getPositionWithinTrack());
    assertEquals(3, updatedSecond.getPositionWithinTrack());
    assertEquals(1, updatedThird.getPositionWithinTrack());
  }

  private UserEntity createUser(String name) {
    UserEntity u = UserHelper.createValidatedUser(name, "password", name + "@email.com");
    return userRepository.saveAndFlush(TestHelpers.withKeycloakId(u));
  }

  @Test
  void reorderTrackWindows_Forbidden_WhenNotOwner() throws Exception {
    UserEntity owner = createUser("windowOwner");
    TrackEntity track = createTrackEntity("Other User Track", owner, null);

    TrackWindowEntity first = createTrackWindow(track, "First", 10L, 20L, false, false);
    TrackWindowEntity second = createTrackWindow(track, "Second", 30L, 40L, false, false);

    ReorderTrackWindowsRequest req = new ReorderTrackWindowsRequest()
            .windowIds(List.of(second.getId(), first.getId()));

    mockMvc.perform(patch("/api/v1/tracks/{trackId}/windows/reorder", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden());
  }

  @Test
  void reorderTrackWindows_BadRequest_WhenDuplicateWindowIds() throws Exception {
    TrackEntity track = createTrackEntity("Duplicate Reorder Track", testUser, null);

    TrackWindowEntity first = createTrackWindow(track, "First", 10L, 20L, false, false);
    createTrackWindow(track, "Second", 30L, 40L, false, false);

    ReorderTrackWindowsRequest req = new ReorderTrackWindowsRequest()
            .windowIds(List.of(first.getId(), first.getId()));

    mockMvc.perform(patch("/api/v1/tracks/{trackId}/windows/reorder", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
  }

  @Test
  void deleteTrackWindow_RecalculatesWindowPositions() throws Exception {
    TrackEntity track = createTrackEntity("Delete Recalculate Track", testUser, null);

    TrackWindowEntity first = createTrackWindow(track, "First", 10L, 20L, false, false);
    TrackWindowEntity second = createTrackWindow(track, "Second", 30L, 40L, false, false);
    TrackWindowEntity third = createTrackWindow(track, "Third", 50L, 60L, false, false);

    mockMvc.perform(delete("/api/v1/tracks/{trackId}/windows/{windowId}", track.getId(), second.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackWindows").isArray())
            .andExpect(jsonPath("$.trackWindows", hasSize(2)))
            .andExpect(jsonPath("$.trackWindows[*].id", contains(
                    first.getId().toString(),
                    third.getId().toString()
            )))
            .andExpect(jsonPath("$.trackWindows[*].positionWithinTrack", contains(1, 2)));

    TrackWindowEntity updatedFirst = trackWindowRepository.findById(first.getId()).orElseThrow();
    TrackWindowEntity updatedThird = trackWindowRepository.findById(third.getId()).orElseThrow();

    assertFalse(trackWindowRepository.existsById(second.getId()));

    assertEquals(1, updatedFirst.getPositionWithinTrack());
    assertEquals(2, updatedThird.getPositionWithinTrack());
  }

  @Test
  void reorderTrackWindows_BadRequest_WhenMissingWindowId() throws Exception {
    TrackEntity track = createTrackEntity("Missing Window Reorder Track", testUser, null);

    TrackWindowEntity first = createTrackWindow(track, "First", 10L, 20L, false, false);
    createTrackWindow(track, "Second", 30L, 40L, false, false);

    ReorderTrackWindowsRequest req = new ReorderTrackWindowsRequest()
            .windowIds(List.of(first.getId()));

    mockMvc.perform(patch("/api/v1/tracks/{trackId}/windows/reorder", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
  }

  @Test
  void reorderTrackWindows_BadRequest_WhenWindowDoesNotBelongToTrack() throws Exception {
    TrackEntity track = createTrackEntity("Main Reorder Track", testUser, null);
    TrackEntity otherTrack = createTrackEntity("Other Reorder Track", testUser, null);

    TrackWindowEntity first = createTrackWindow(track, "First", 10L, 20L, false, false);
    TrackWindowEntity second = createTrackWindow(track, "Second", 30L, 40L, false, false);
    TrackWindowEntity otherWindow = createTrackWindow(otherTrack, "Other", 50L, 60L, false, false);

    ReorderTrackWindowsRequest req = new ReorderTrackWindowsRequest()
            .windowIds(List.of(first.getId(), otherWindow.getId()));

    mockMvc.perform(patch("/api/v1/tracks/{trackId}/windows/reorder", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());

    TrackWindowEntity unchangedFirst = trackWindowRepository.findById(first.getId()).orElseThrow();
    TrackWindowEntity unchangedSecond = trackWindowRepository.findById(second.getId()).orElseThrow();

    assertEquals(1, unchangedFirst.getPositionWithinTrack());
    assertEquals(2, unchangedSecond.getPositionWithinTrack());
  }

  @Test
  void reorderTrackWindows_NotFound_WhenTrackDoesNotExist() throws Exception {
    ReorderTrackWindowsRequest req = new ReorderTrackWindowsRequest()
            .windowIds(List.of(UUID.randomUUID(), UUID.randomUUID()));

    mockMvc.perform(patch("/api/v1/tracks/{trackId}/windows/reorder", UUID.randomUUID())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
  }

  private GroupEntity createGroup(String name, UserEntity owner) {
    GroupEntity g = new GroupEntity();
    g.setListName(name);
    g.setOwner(owner);
    return groupRepository.saveAndFlush(g);
  }

  private TrackEntity createTrackEntity(String name, UserEntity owner, GroupEntity group) {
    TrackEntity t = new TrackEntity();
    t.setTrackName(name);
    t.setTrackOriginalName(name);
    t.setTrackLink("https://example.com/" + name + ".mp3");
    t.setDuration(120);
    t.setOwner(owner);
    t.setFadeInDurationMs(1000);
    t.setFadeOutDurationMs(1000);
    TrackEntity saved = trackRepository.saveAndFlush(t);

    if (group != null) {
      groupRepository.addTrackToGroup(group.getId(), saved.getId());
    }

    return saved;
  }

  private TrackWindowEntity createTrackWindow(TrackEntity track,
                                              String name,
                                              Long positionFrom,
                                              Long positionTo,
                                              boolean fadeIn,
                                              boolean fadeOut) {
    TrackWindowEntity p = new TrackWindowEntity();
    p.setTrack(track);
    p.setName(name);
    p.setPositionFrom(positionFrom);
    p.setPositionTo(positionTo);
    p.setFadeOutDurationMs(1000);
    p.setFadeInDurationMs(1000);
    p.setPositionWithinTrack(nextWindowPosition(track));
    return trackWindowRepository.saveAndFlush(p);
  }

  private SessionEntity createSession(String name, UserEntity owner) {
    SessionEntity session = new SessionEntity();
    session.setName(name);
    session.setDescription(name + " description");
    session.setOwner(owner);
    return sessionRepository.saveAndFlush(session);
  }

  private BoardEntity createBoard(String name,
                                  UserEntity owner,
                                  SessionEntity session,
                                  TrackEntity selectedTrack,
                                  TrackWindowEntity selectedWindow) {
    BoardEntity board = new BoardEntity();
    board.setName(name);
    board.setOwner(owner);
    board.setSession(session);
    board.setVolume(50);
    board.setRepeat(false);
    board.setOverplay(false);
    board.setSelectedTrack(selectedTrack);
    board.setSelectedWindow(selectedWindow);
    return boardRepository.saveAndFlush(board);
  }

  private int nextWindowPosition(TrackEntity track) {
    return trackWindowRepository
            .findMaxPositionWithinTrack(track.getId())
            .orElse(0) + 1;
  }
}