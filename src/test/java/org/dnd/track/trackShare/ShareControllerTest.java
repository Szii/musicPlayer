package org.dnd.track.trackShare;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.TestHelpers;
import org.dnd.api.model.PublishTrackRequest;
import org.dnd.api.model.SubscribeRequest;
import org.dnd.board.BoardEntity;
import org.dnd.board.BoardRepository;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.TrackWindowEntity;
import org.dnd.track.TrackWindowRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShareControllerTest extends DatabaseBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TrackRepository trackRepository;

  @Autowired
  private TrackShareRepository trackShareRepository;

  @Autowired
  private TrackWindowRepository trackWindowRepository;

  @Autowired
  private BoardRepository boardRepository;

  @Autowired
  private GroupRepository groupRepository;

  @Autowired
  private SessionRepository sessionRepository;

  private UserEntity testUser;
  private UserEntity otherUser;

  @BeforeEach
  void setUp() {
    testUser = createUser("testUser_" + UUID.randomUUID());
    otherUser = createUser("otherUser_" + UUID.randomUUID());
  }

  @Test
  void publishTrack_Owner_Success() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser);

    PublishTrackRequest request = new PublishTrackRequest();
    request.setDescription("Great track for studying");

    mockMvc.perform(post("/api/v1/share/tracks/{trackId}/publish", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.description").value("Great track for studying"))
            .andExpect(jsonPath("$.shareCode").exists());
  }

  @Test
  void publishTrack_NotOwner_Forbidden() throws Exception {
    TrackEntity track = createTrackEntity("Other Track", otherUser);

    PublishTrackRequest request = new PublishTrackRequest();
    request.setDescription("Try to publish");

    mockMvc.perform(post("/api/v1/share/tracks/{trackId}/publish", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
  }

  @Test
  void publishTrack_TrackNotFound() throws Exception {
    PublishTrackRequest request = new PublishTrackRequest();
    request.setDescription("Non-existent track");

    mockMvc.perform(post("/api/v1/share/tracks/{trackId}/publish", UUID.randomUUID())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
  }

  @Test
  void unpublishTrack_Owner_Success() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser);
    createTrackShare(track, "Published track");

    mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    assertNull(trackRepository.findById(track.getId()).orElseThrow().getTrackShare());
  }

  @Test
  void unpublishTrack_RemovesTrackFromSubscriberBoardsAndGroupsButKeepsOwnerStateAndUnrelatedBoards() throws Exception {
    TrackEntity track = createTrackEntity("Shared Track", testUser);
    TrackShareEntity share = createTrackShare(track, "Published track");

    TrackEntity unrelatedTrack = createTrackEntity("Unrelated Subscriber Track", otherUser);

    TrackWindowEntity ownerWindow = createTrackWindow(track, "Owner Window");
    TrackWindowEntity subscriberWindow = createTrackWindow(track, "Subscriber Window");
    TrackWindowEntity unrelatedSubscriberWindow = createTrackWindow(unrelatedTrack, "Unrelated Subscriber Window");

    SessionEntity ownerSession = createSession("Owner Session", testUser);
    SessionEntity subscriberSession = createSession("Subscriber Session", otherUser);

    BoardEntity ownerBoard = createBoard(
            "Owner Board",
            testUser,
            ownerSession,
            track,
            ownerWindow
    );

    BoardEntity subscriberBoard = createBoard(
            "Subscriber Board",
            otherUser,
            subscriberSession,
            track,
            subscriberWindow
    );

    BoardEntity unrelatedSubscriberBoard = createBoard(
            "Unrelated Subscriber Board",
            otherUser,
            subscriberSession,
            unrelatedTrack,
            unrelatedSubscriberWindow
    );

    GroupEntity ownerGroup = createGroup("Owner Group", testUser);
    GroupEntity subscriberGroup = createGroup("Subscriber Group", otherUser);

    groupRepository.addTrackToGroup(ownerGroup.getId(), track.getId());
    groupRepository.addTrackToGroup(subscriberGroup.getId(), track.getId());

    UserEntity subscriber = userRepository.findById(otherUser.getId()).orElseThrow();
    subscriber.getShares().add(share);
    share.getUsers().add(subscriber);
    userRepository.saveAndFlush(subscriber);

    mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    BoardEntity updatedSubscriberBoard = boardRepository.findById(subscriberBoard.getId()).orElseThrow();

    assertNull(updatedSubscriberBoard.getSelectedTrack());
    assertNull(updatedSubscriberBoard.getSelectedWindow());

    BoardEntity updatedUnrelatedSubscriberBoard = boardRepository.findById(unrelatedSubscriberBoard.getId()).orElseThrow();

    assertNotNull(updatedUnrelatedSubscriberBoard.getSelectedTrack());
    assertEquals(unrelatedTrack.getId(), updatedUnrelatedSubscriberBoard.getSelectedTrack().getId());

    assertNotNull(updatedUnrelatedSubscriberBoard.getSelectedWindow());
    assertEquals(unrelatedSubscriberWindow.getId(), updatedUnrelatedSubscriberBoard.getSelectedWindow().getId());

    assertTrue(groupRepository.findAllContainingTrackOwnedByUser(track.getId(), otherUser.getId())
            .stream()
            .noneMatch(group -> group.getId().equals(subscriberGroup.getId())));

    BoardEntity updatedOwnerBoard = boardRepository.findById(ownerBoard.getId()).orElseThrow();

    assertNotNull(updatedOwnerBoard.getSelectedTrack());
    assertEquals(track.getId(), updatedOwnerBoard.getSelectedTrack().getId());

    assertNotNull(updatedOwnerBoard.getSelectedWindow());
    assertEquals(ownerWindow.getId(), updatedOwnerBoard.getSelectedWindow().getId());

    assertTrue(groupRepository.findAllContainingTrackOwnedByUser(track.getId(), testUser.getId())
            .stream()
            .anyMatch(group -> group.getId().equals(ownerGroup.getId())));

    assertNull(trackRepository.findById(track.getId()).orElseThrow().getTrackShare());
  }

  @Test
  void unpublishTrack_NotOwner_Forbidden() throws Exception {
    TrackEntity track = createTrackEntity("Other Track", otherUser);
    createTrackShare(track, "Published track");

    mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isForbidden());
  }

  @Test
  void unpublishTrack_NotPublished_Conflict() throws Exception {
    TrackEntity track = createTrackEntity("My Track", testUser);

    mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNotFound());
  }

  @Test
  void subscribeToTrack_isForbidden_whenShareLimitIsReached() throws Exception {
    TrackEntity track = createTrackEntity("Shared Track", otherUser);
    TrackShareEntity share = createTrackShare(track, "Popular track");

    for (int i = 0; i < UserRankLimits.normal().maxShares(); i++) {
      TrackShareEntity tempShare = new TrackShareEntity();
      tempShare.setDescription("Temp share " + i);
      tempShare.setShareCode(UUID.randomUUID().toString());
      tempShare.setTrack(track);
      TrackShareEntity savedShare = trackShareRepository.save(tempShare);

      testUser.getShares().add(savedShare);
    }
    userRepository.save(testUser);

    SubscribeRequest request = new SubscribeRequest();
    request.setShareCode(share.getShareCode());

    mockMvc.perform(post("/api/v1/share/subscribe")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
  }

  @Test
  void unpublishTrack_TrackNotFound() throws Exception {
    mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", UUID.randomUUID())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNotFound());
  }

  @Test
  void subscribeToTrack_Success() throws Exception {
    TrackEntity track = createTrackEntity("Shared Track", otherUser);
    TrackShareEntity share = createTrackShare(track, "Popular track");

    SubscribeRequest request = new SubscribeRequest();
    request.setShareCode(share.getShareCode());

    mockMvc.perform(post("/api/v1/share/subscribe")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

    UserEntity user = userRepository.findById(testUser.getId()).orElseThrow();
    assertTrue(user.getShares().stream()
            .anyMatch(t -> t.getTrack().getId().equals(track.getId())));


  }

  @Test
  void subscriberCount_updatesAfterUnsubscribe() throws Exception {
    TrackEntity track = createTrackEntity("Shared Track", otherUser);
    TrackShareEntity share = createTrackShare(track, "Popular track");

    UserEntity subscriber2 = createUser("subscriber2_" + UUID.randomUUID());
    UserEntity subscriber3 = createUser("subscriber3_" + UUID.randomUUID());

    subscriber2.getShares().add(share);
    subscriber3.getShares().add(share);

    userRepository.saveAndFlush(subscriber2);
    userRepository.saveAndFlush(subscriber3);

    mockMvc.perform(get("/api/v1/tracks/published")
                    .with(TestHelpers.authenticatedAs(otherUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath(
                    "$[?(@.id == '%s')].trackShare.subscriberCount",
                    track.getId()
            ).value(Matchers.contains(2)));

    mockMvc.perform(delete("/api/v1/share/unsubscribe/{trackId}", track.getId().toString())
                    .with(TestHelpers.authenticatedAs(subscriber2)))
            .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/tracks/published")
                    .with(TestHelpers.authenticatedAs(otherUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')].trackShare.subscriberCount", track.getId().toString())
                    .value(1));
  }

  @Test
  void subscribeToTrack_InvalidShareCode() throws Exception {
    SubscribeRequest request = new SubscribeRequest();
    request.setShareCode("invalid-code-12345");

    mockMvc.perform(post("/api/v1/share/subscribe")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
  }

  @Test
  void unsubscribeFromTrack_Success() throws Exception {
    TrackEntity track = createTrackEntity("Shared Track", otherUser);
    TrackShareEntity share = createTrackShare(track, "Popular track");

    UserEntity user = userRepository.findById(testUser.getId()).orElseThrow();
    user.getShares().add(share);
    share.getUsers().add(user);

    userRepository.save(user);

    mockMvc.perform(delete("/api/v1/share/unsubscribe/{trackId}", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    UserEntity updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
    assertFalse(updatedUser.getShares().stream()
            .anyMatch(s -> s.getId().equals(share.getId())));
  }

  @Test
  void unsubscribeFromTrack_RemovesTrackFromUserBoardsAndGroupsButKeepsOwnerStateAndUnrelatedBoards() throws Exception {
    TrackEntity track = createTrackEntity("Shared Track", otherUser);
    TrackShareEntity share = createTrackShare(track, "Popular track");

    TrackEntity unrelatedTrack = createTrackEntity("Unrelated Track", testUser);

    TrackWindowEntity userWindow = createTrackWindow(track, "User Window");
    TrackWindowEntity ownerWindow = createTrackWindow(track, "Owner Window");
    TrackWindowEntity unrelatedWindow = createTrackWindow(unrelatedTrack, "Unrelated Window");

    SessionEntity userSession = createSession("User Session", testUser);
    SessionEntity ownerSession = createSession("Owner Session", otherUser);

    BoardEntity userBoard = createBoard(
            "User Board",
            testUser,
            userSession,
            track,
            userWindow
    );

    BoardEntity unrelatedUserBoard = createBoard(
            "Unrelated User Board",
            testUser,
            userSession,
            unrelatedTrack,
            unrelatedWindow
    );

    BoardEntity ownerBoard = createBoard(
            "Owner Board",
            otherUser,
            ownerSession,
            track,
            ownerWindow
    );

    GroupEntity userGroup = createGroup("User Group", testUser);
    GroupEntity ownerGroup = createGroup("Owner Group", otherUser);

    groupRepository.addTrackToGroup(userGroup.getId(), track.getId());
    groupRepository.addTrackToGroup(ownerGroup.getId(), track.getId());

    UserEntity user = userRepository.findById(testUser.getId()).orElseThrow();
    user.getShares().add(share);
    share.getUsers().add(user);
    userRepository.saveAndFlush(user);

    mockMvc.perform(delete("/api/v1/share/unsubscribe/{trackId}", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    BoardEntity updatedUserBoard = boardRepository.findById(userBoard.getId()).orElseThrow();

    assertNull(updatedUserBoard.getSelectedTrack());
    assertNull(updatedUserBoard.getSelectedWindow());

    BoardEntity updatedUnrelatedUserBoard = boardRepository.findById(unrelatedUserBoard.getId()).orElseThrow();

    assertNotNull(updatedUnrelatedUserBoard.getSelectedTrack());
    assertEquals(unrelatedTrack.getId(), updatedUnrelatedUserBoard.getSelectedTrack().getId());

    assertNotNull(updatedUnrelatedUserBoard.getSelectedWindow());
    assertEquals(unrelatedWindow.getId(), updatedUnrelatedUserBoard.getSelectedWindow().getId());

    assertTrue(groupRepository.findAllContainingTrackOwnedByUser(track.getId(), testUser.getId())
            .stream()
            .noneMatch(group -> group.getId().equals(userGroup.getId())));

    BoardEntity updatedOwnerBoard = boardRepository.findById(ownerBoard.getId()).orElseThrow();

    assertNotNull(updatedOwnerBoard.getSelectedTrack());
    assertEquals(track.getId(), updatedOwnerBoard.getSelectedTrack().getId());

    assertNotNull(updatedOwnerBoard.getSelectedWindow());
    assertEquals(ownerWindow.getId(), updatedOwnerBoard.getSelectedWindow().getId());

    assertTrue(groupRepository.findAllContainingTrackOwnedByUser(track.getId(), otherUser.getId())
            .stream()
            .anyMatch(group -> group.getId().equals(ownerGroup.getId())));
  }

  @Test
  void unsubscribeFromTrack_NotSubscribed() throws Exception {
    TrackEntity track = createTrackEntity("Shared Track", otherUser);

    mockMvc.perform(delete("/api/v1/share/unsubscribe/{trackId}", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNotFound());
  }

  @Test
  void unsubscribeFromTrack_TrackNotFound() throws Exception {
    mockMvc.perform(delete("/api/v1/share/unsubscribe/{trackId}", UUID.randomUUID())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNotFound());
  }

  private UserEntity createUser(String name) {
    UserEntity u = UserHelper.createValidatedUser(name, "password", name + "user@email.com");
    return userRepository.saveAndFlush(TestHelpers.withKeycloakId(u));
  }

  private TrackEntity createTrackEntity(String name, UserEntity owner) {
    TrackEntity t = new TrackEntity();
    t.setTrackName(name);
    t.setTrackOriginalName(name);
    t.setTrackLink("https://www.youtube.com/watch?v=gbFGnw2JYe0&list=PLDtPBNsaMdk-M7oRThTgSQm--LuxMUW4S");
    t.setDuration(120);
    t.setOwner(owner);
    return trackRepository.save(t);
  }

  private TrackShareEntity createTrackShare(TrackEntity track, String description) {
    TrackShareEntity share = new TrackShareEntity();
    share.setDescription(description);
    share.setShareCode(UUID.randomUUID().toString());
    share.setTrack(track);
    track.setTrackShare(share);

    TrackEntity savedTrack = trackRepository.saveAndFlush(track);
    return savedTrack.getTrackShare();
  }

  private TrackWindowEntity createTrackWindow(TrackEntity track, String name) {
    TrackWindowEntity window = new TrackWindowEntity();
    window.setTrack(track);
    window.setName(name);
    window.setPositionFrom(0L);
    window.setPositionTo(10L);
    window.setFadeInDurationMs(1000);
    window.setFadeOutDurationMs(1000);
    window.setPositionWithinTrack(nextWindowPosition(track));
    return trackWindowRepository.saveAndFlush(window);
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

  private GroupEntity createGroup(String name, UserEntity owner) {
    GroupEntity group = new GroupEntity();
    group.setListName(name);
    group.setOwner(owner);
    return groupRepository.saveAndFlush(group);
  }

  private int nextWindowPosition(TrackEntity track) {
    return trackWindowRepository
            .findMaxPositionWithinTrack(track.getId())
            .orElse(0) + 1;
  }
}