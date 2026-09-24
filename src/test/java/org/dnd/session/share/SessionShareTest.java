package org.dnd.session.share;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.TestHelpers;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.api.model.PublishSessionRequest;
import org.dnd.api.model.SubscribeRequest;
import org.dnd.api.model.UpdateSessionShareRequest;
import org.dnd.api.model.UserLimits;
import org.dnd.board.BoardEntity;
import org.dnd.board.BoardRepository;
import org.dnd.board.LinkedBoard;
import org.dnd.board.LinkedBoardMode;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.TrackWindowEntity;
import org.dnd.user.UserEntity;
import org.dnd.user.UserHelper;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionShareTest extends DatabaseBase {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private SessionRepository sessionRepository;
  @Autowired
  private SessionShareRepository sessionShareRepository;
  @Autowired
  private TrackRepository trackRepository;
  @Autowired
  private GroupRepository groupRepository;
  @Autowired
  private BoardRepository boardRepository;
  @Autowired
  private TransactionTemplate transactionTemplate;
  @Autowired
  private UserRankEvaluatorService userRankEvaluatorService;

  private UserEntity owner;
  private UserEntity subscriber;
  private SessionEntity ownerSession;
  private TrackEntity directTrack;
  private TrackEntity groupTrack;
  private TrackWindowEntity window;
  private GroupEntity group;
  private BoardEntity groupBoard;

  @BeforeEach
  void setUp() {
    owner = userRepository.save(TestHelpers.withKeycloakId(
            UserHelper.createValidatedUser("owner", "password", "owner@email.cz")));
    subscriber = userRepository.save(TestHelpers.withKeycloakId(
            UserHelper.createValidatedUser("subscriber", "password", "subscriber@email.cz")));

    directTrack = createTrack("Direct", owner);
    groupTrack = createTrack("In group", owner);
    window = TrackWindowEntity.builder()
            .name("Chorus")
            .positionFrom(10L)
            .positionTo(20L)
            .positionWithinTrack(1)
            .build();
    groupTrack.addTrackWindow(window);
    groupTrack = trackRepository.save(groupTrack);
    window = groupTrack.getTrackWindows().getFirst();

    group = new GroupEntity();
    group.setListName("Battle");
    group.setOwner(owner);
    group.addTrack(groupTrack).setPositionWithinGroup(1);
    group.addTrack(directTrack, "Renamed").setPositionWithinGroup(2);
    group.addTrack(groupTrack, window, "Chorus only").setPositionWithinGroup(3);
    group = groupRepository.save(group);

    ownerSession = new SessionEntity();
    ownerSession.setName("Campaign");
    ownerSession.setDescription("Dungeon crawl");
    ownerSession.setOwner(owner);
    ownerSession.getTracks().add(directTrack);
    ownerSession.getGroups().add(group);
    ownerSession = sessionRepository.save(ownerSession);

    groupBoard = new BoardEntity();
    groupBoard.setName("Fight");
    groupBoard.setOwner(owner);
    groupBoard.setSession(ownerSession);
    groupBoard.setSelectedGroup(group);
    groupBoard.setSelectedTrack(groupTrack);
    groupBoard.setSelectedWindow(window);
    groupBoard.setVolume(70);
    groupBoard = boardRepository.save(groupBoard);

    BoardEntity ambientBoard = new BoardEntity();
    ambientBoard.setName("Ambient");
    ambientBoard.setOwner(owner);
    ambientBoard.setSession(ownerSession);
    ambientBoard.setLinkedBoard(new LinkedBoard(groupBoard.getId(), LinkedBoardMode.START));
    boardRepository.save(ambientBoard);

    BoardEntity exploreBoard = new BoardEntity();
    exploreBoard.setName("Explore");
    exploreBoard.setOwner(owner);
    exploreBoard.setSession(ownerSession);
    exploreBoard.setSelectedGroup(group);
    boardRepository.save(exploreBoard);
  }

  @Test
  void publish_listsSessionInCatalog() throws Exception {
    publish("Great for dungeons")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.name").value("Campaign"))
            .andExpect(jsonPath("$.description").value("Great for dungeons"))
            .andExpect(jsonPath("$.boardCount").value(3))
            .andExpect(jsonPath("$.trackCount").value(2))
            .andExpect(jsonPath("$.groupCount").value(1));

    mockMvc.perform(get("/api/v1/share/sessions").with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].owned").value(false))
            .andExpect(jsonPath("$[0].subscribed").value(false))
            .andExpect(jsonPath("$[0].owner.name").value("owner"));

    mockMvc.perform(get("/api/v1/sessions/{id}", ownerSession.getId()).with(TestHelpers.authenticatedAs(owner)))
            .andExpect(jsonPath("$.readOnly").value(false))
            .andExpect(jsonPath("$.trackCount").value(2))
            .andExpect(jsonPath("$.publication.version").value(1))
            .andExpect(jsonPath("$.publication.subscriberCount").value(0));
  }

  @Test
  void publish_isConflict_whenAlreadyPublished() throws Exception {
    publish(null).andExpect(status().isCreated());
    publish(null).andExpect(status().isConflict());
  }

  @Test
  void publish_isNotFound_forForeignSession() throws Exception {
    mockMvc.perform(post("/api/v1/share/sessions/{id}/publish", ownerSession.getId())
                    .with(TestHelpers.authenticatedAs(subscriber))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isNotFound());
  }

  @Test
  void subscribe_copiesSessionContent_outsideOfLibrary() throws Exception {
    String shareCode = shareCodeOf(publish(null));

    JsonNode session = json(subscribe(shareCode).andExpect(status().isCreated()));

    assertTrue(session.get("readOnly").asBoolean());
    assertEquals("Campaign", session.get("sessionName").asText());
    assertEquals(3, session.get("boards").size());
    assertEquals(2, session.get("trackCount").asInt());
    JsonNode subscription = session.get("subscription");
    assertEquals(1, subscription.get("installedVersion").asInt());
    assertFalse(subscription.get("updateAvailable").asBoolean());
    assertFalse(subscription.get("modified").asBoolean());
    assertEquals(2, subscription.get("tracks").size());
    assertEquals(1, subscription.get("groups").size());
    JsonNode groupItems = subscription.get("groups").get(0).get("tracks");
    assertEquals(3, groupItems.size());
    assertEquals("Renamed", groupItems.get(1).get("trackName").asText());
    assertEquals("Chorus only", groupItems.get(2).get("trackName").asText());
    assertTrue(groupItems.get(2).get("isWindow").asBoolean());
    String copiedWindowId = groupItems.get(2).get("windowId").asText();
    assertNotEquals(window.getId().toString(), copiedWindowId);
    assertEquals(copiedWindowId, boardNamed(session, "Fight").get("selectedWindow").get("id").asText());
    assertFalse(subscription.get("tracks").get(0).get("owned").asBoolean());

    UUID copiedGroupId = UUID.fromString(subscription.get("groups").get(0).get("id").asText());
    assertNotEquals(group.getId(), copiedGroupId);

    JsonNode fight = boardNamed(session, "Fight");
    JsonNode ambient = boardNamed(session, "Ambient");
    assertEquals(copiedGroupId.toString(), fight.get("selectedGroup").get("id").asText());
    assertEquals("Chorus", fight.get("selectedWindow").get("name").asText());
    assertEquals(70, fight.get("volume").asInt());
    assertEquals(fight.get("id").asText(), ambient.get("linkedBoard").get("boardId").asText());

    mockMvc.perform(get("/api/v1/tracks").with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(jsonPath("$.length()").value(0));
    mockMvc.perform(get("/api/v1/groups").with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(jsonPath("$.length()").value(0));
    UserLimits limits = transactionTemplate.execute(status ->
            userRankEvaluatorService.getLimitsForUser(userRepository.findById(subscriber.getId()).orElseThrow()));
    assertEquals(0, limits.getTracks().getActualTracks());
    assertEquals(0, limits.getGroups().getActualGroups());
    assertEquals(0, limits.getSessions().getActualSessions());
    assertEquals(0, limits.getBoards().size());
    assertEquals(1, limits.getSubscribes().getActualSubscribes());
    assertEquals(5, limits.getSubscribes().getMaxSubscribes());
  }

  @Test
  void install_selectsFirstTrack_forStagesPublishedWithoutOne() throws Exception {
    JsonNode session = json(subscribe(shareCodeOf(publish(null))));
    String sessionId = session.get("sessionId").asText();

    JsonNode ambient = boardNamed(session, "Ambient");
    JsonNode explore = boardNamed(session, "Explore");
    assertEquals("Direct", ambient.get("selectedTrack").get("trackName").asText());
    assertEquals("In group", explore.get("selectedTrack").get("trackName").asText());
    assertFalse(explore.hasNonNull("selectedWindow"));

    updateBoard(ambient, playbackUpdate(ambient).selectedTrackId(null))
            .andExpect(status().isOk());
    JsonNode cleared = boardNamed(json(mockMvc.perform(get("/api/v1/sessions/{id}", sessionId)
            .with(TestHelpers.authenticatedAs(subscriber)))), "Ambient");
    assertFalse(cleared.hasNonNull("selectedTrack"));

    JsonNode synced = json(sync(sessionId));
    assertEquals("Direct", boardNamed(synced, "Ambient").get("selectedTrack").get("trackName").asText());
  }

  @Test
  void subscribe_rejectsOwnSession_duplicates_andUnknownCodes() throws Exception {
    String shareCode = shareCodeOf(publish(null));

    subscribe(shareCode, owner).andExpect(status().isBadRequest());
    subscribe(shareCode).andExpect(status().isCreated());
    subscribe(shareCode).andExpect(status().isConflict());
    subscribe(UUID.randomUUID().toString()).andExpect(status().isNotFound());
  }

  @Test
  void subscribedSession_rejectsStructuralChanges() throws Exception {
    JsonNode session = json(subscribe(shareCodeOf(publish(null))));
    String sessionId = session.get("sessionId").asText();
    JsonNode subscription = session.get("subscription");
    String copiedTrackId = subscription.get("tracks").get(0).get("id").asText();
    String copiedGroupId = subscription.get("groups").get(0).get("id").asText();
    JsonNode fight = boardNamed(session, "Fight");

    mockMvc.perform(post("/api/v1/sessions")
                    .with(TestHelpers.authenticatedAs(subscriber))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sessionId\":\"" + sessionId + "\",\"sessionName\":\"Mine\"}"))
            .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/v1/sessions/{s}/groups/{g}", sessionId, copiedGroupId)
                    .with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/boards")
                    .with(TestHelpers.authenticatedAs(subscriber))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"New\",\"sessionId\":\"" + sessionId + "\"}"))
            .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/v1/boards/{id}", fight.get("id").asText())
                    .with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/v1/tracks/{id}", copiedTrackId)
                    .with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/v1/groups/{id}", copiedGroupId)
                    .with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(status().isNotFound());

    updateBoard(fight, playbackUpdate(fight).name("Renamed"))
            .andExpect(status().isForbidden());
    updateBoard(fight, playbackUpdate(fight).selectedGroupId(null))
            .andExpect(status().isForbidden());
    JsonNode ambient = boardNamed(session, "Ambient");
    updateBoard(ambient, playbackUpdate(ambient).linkedBoard(null))
            .andExpect(status().isForbidden());
  }

  @Test
  void subscribedSession_isNotUsableFromOwnSessions() throws Exception {
    JsonNode session = json(subscribe(shareCodeOf(publish(null))));
    String copiedTrackId = session.get("subscription").get("tracks").get(0).get("id").asText();
    String copiedGroupId = session.get("subscription").get("groups").get(0).get("id").asText();

    SessionEntity own = new SessionEntity();
    own.setName("Own");
    own.setOwner(subscriber);
    own = sessionRepository.save(own);

    mockMvc.perform(put("/api/v1/sessions/{s}/tracks/{t}", own.getId(), copiedTrackId)
                    .with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(status().isNotFound());
    mockMvc.perform(put("/api/v1/sessions/{s}/groups/{g}", own.getId(), copiedGroupId)
                    .with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(status().isNotFound());
  }

  @Test
  void subscribedBoard_allowsPlaybackChanges_andSyncRestoresThem() throws Exception {
    JsonNode session = json(subscribe(shareCodeOf(publish(null))));
    String sessionId = session.get("sessionId").asText();
    JsonNode fight = boardNamed(session, "Fight");
    String selectedTrackId = fight.get("selectedTrack").get("id").asText();
    String otherTrackId = null;
    for (JsonNode available : fight.get("availableTracks")) {
      if (!available.get("id").asText().equals(selectedTrackId)) {
        otherTrackId = available.get("id").asText();
      }
    }

    updateBoard(fight, playbackUpdate(fight)
            .volume(10)
            .shuffle(true)
            .repeat(true)
            .overplay(true)
            .sequenceMode(true)
            .playlistMode(true)
            .selectedTrackId(UUID.fromString(otherTrackId))
            .selectedWindowId(null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.volume").value(10))
            .andExpect(jsonPath("$.shuffle").value(false))
            .andExpect(jsonPath("$.repeat").value(false))
            .andExpect(jsonPath("$.overplay").value(false))
            .andExpect(jsonPath("$.sequenceMode").value(false))
            .andExpect(jsonPath("$.playlistMode").value(false))
            .andExpect(jsonPath("$.selectedTrack.id").value(otherTrackId));

    updateBoard(fight, playbackUpdate(fight).selectedTrackId(directTrack.getId()).selectedWindowId(null))
            .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/sessions/{id}", sessionId).with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(jsonPath("$.subscription.modified").value(true))
            .andExpect(jsonPath("$.subscription.updateAvailable").value(false));

    JsonNode synced = json(sync(sessionId).andExpect(status().isOk()));
    JsonNode restored = boardNamed(synced, "Fight");
    assertEquals(70, restored.get("volume").asInt());
    assertFalse(restored.get("shuffle").asBoolean());
    assertEquals("Chorus", restored.get("selectedWindow").get("name").asText());
    assertFalse(synced.get("subscription").get("modified").asBoolean());

    assertEquals(2, trackRepository.findByOwner_Id(subscriber.getId()).size());
    assertEquals(1, groupRepository.findByOwner_Id(subscriber.getId()).size());
  }

  @Test
  void publishUpdate_flagsUpdate_andSyncInstallsIt() throws Exception {
    JsonNode session = json(subscribe(shareCodeOf(publish(null))));
    String sessionId = session.get("sessionId").asText();

    transactionTemplate.executeWithoutResult(status -> {
      SessionEntity live = sessionRepository.findById(ownerSession.getId()).orElseThrow();
      live.setName("Campaign II");
      live.getGroups().clear();
    });

    mockMvc.perform(get("/api/v1/sessions/{id}", sessionId).with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(jsonPath("$.subscription.updateAvailable").value(false));

    mockMvc.perform(put("/api/v1/share/sessions/{id}/publish", ownerSession.getId())
                    .with(TestHelpers.authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.name").value("Campaign II"));

    mockMvc.perform(get("/api/v1/sessions/{id}", sessionId).with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(jsonPath("$.subscription.installedVersion").value(1))
            .andExpect(jsonPath("$.subscription.latestVersion").value(2))
            .andExpect(jsonPath("$.subscription.updateAvailable").value(true))
            .andExpect(jsonPath("$.sessionName").value("Campaign"));

    sync(sessionId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionName").value("Campaign II"))
            .andExpect(jsonPath("$.subscription.installedVersion").value(2))
            .andExpect(jsonPath("$.subscription.updateAvailable").value(false))
            .andExpect(jsonPath("$.subscription.groups.length()").value(0));
  }

  @Test
  void updateDescription_doesNotCreateVersion() throws Exception {
    publish("Old").andExpect(status().isCreated());

    mockMvc.perform(patch("/api/v1/share/sessions/{id}/publish", ownerSession.getId())
                    .with(TestHelpers.authenticatedAs(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new UpdateSessionShareRequest().description("New"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("New"))
            .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void unpublish_hidesShare_butSubscribersKeepSession() throws Exception {
    String shareCode = shareCodeOf(publish(null));
    String sessionId = json(subscribe(shareCode)).get("sessionId").asText();

    mockMvc.perform(delete("/api/v1/share/sessions/{id}/publish", ownerSession.getId())
                    .with(TestHelpers.authenticatedAs(owner)))
            .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/share/sessions").with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(jsonPath("$.length()").value(0));

    UserEntity newcomer = userRepository.save(TestHelpers.withKeycloakId(
            UserHelper.createValidatedUser("newcomer", "password", "newcomer@email.cz")));
    subscribe(shareCode, newcomer).andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/sessions/{id}", sessionId).with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(jsonPath("$.subscription.unpublished").value(true))
            .andExpect(jsonPath("$.subscription.restorable").value(true))
            .andExpect(jsonPath("$.subscription.updateAvailable").value(false))
            .andExpect(jsonPath("$.boards.length()").value(3));

    sync(sessionId).andExpect(status().isOk());

    mockMvc.perform(delete("/api/v1/sessions/{id}", sessionId).with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(status().isOk());

    assertEquals(0, sessionShareRepository.count());
    assertEquals(0, trackRepository.findByOwner_Id(subscriber.getId()).size());
    assertEquals(0, groupRepository.findByOwner_Id(subscriber.getId()).size());
  }

  @Test
  void unpublish_withoutSubscribers_removesShare() throws Exception {
    publish(null).andExpect(status().isCreated());

    mockMvc.perform(delete("/api/v1/share/sessions/{id}/publish", ownerSession.getId())
                    .with(TestHelpers.authenticatedAs(owner)))
            .andExpect(status().isNoContent());

    assertEquals(0, sessionShareRepository.count());
    publish(null).andExpect(status().isCreated());
  }

  @Test
  void deletingOwnerSession_unpublishesIt_andSubscribersKeepTheirCopy() throws Exception {
    String sessionId = json(subscribe(shareCodeOf(publish(null)))).get("sessionId").asText();

    mockMvc.perform(delete("/api/v1/sessions/{id}", ownerSession.getId()).with(TestHelpers.authenticatedAs(owner)))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/sessions/{id}", sessionId).with(TestHelpers.authenticatedAs(subscriber)))
            .andExpect(jsonPath("$.subscription.unpublished").value(true))
            .andExpect(jsonPath("$.boards.length()").value(3));
  }

  @Test
  void subscribe_isLimitedToFiveSessions() throws Exception {
    for (int i = 0; i < 6; i++) {
      SessionEntity session = new SessionEntity();
      session.setName("Session " + i);
      session.setOwner(owner);
      session.getTracks().add(directTrack);
      session = sessionRepository.save(session);

      BoardEntity board = new BoardEntity();
      board.setName("Stage");
      board.setOwner(owner);
      board.setSession(session);
      boardRepository.save(board);

      String shareCode = shareCodeOf(mockMvc.perform(post("/api/v1/share/sessions/{id}/publish", session.getId())
              .with(TestHelpers.authenticatedAs(owner))
              .contentType(MediaType.APPLICATION_JSON)
              .content("{}")));

      subscribe(shareCode).andExpect(i < 5 ? status().isCreated() : status().isForbidden());
    }
  }

  @Test
  void publish_isBadRequest_withoutStagesOrTracks() throws Exception {
    SessionEntity noStages = new SessionEntity();
    noStages.setName("No stages");
    noStages.setOwner(owner);
    noStages.getTracks().add(directTrack);
    noStages = sessionRepository.save(noStages);

    SessionEntity noTracks = new SessionEntity();
    noTracks.setName("No tracks");
    noTracks.setOwner(owner);
    noTracks = sessionRepository.save(noTracks);
    BoardEntity board = new BoardEntity();
    board.setName("Stage");
    board.setOwner(owner);
    board.setSession(noTracks);
    boardRepository.save(board);

    for (SessionEntity session : new SessionEntity[]{noStages, noTracks}) {
      mockMvc.perform(post("/api/v1/share/sessions/{id}/publish", session.getId())
                      .with(TestHelpers.authenticatedAs(owner))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
              .andExpect(status().isBadRequest());
    }
  }

  @Test
  void sync_isBadRequest_forOwnSession() throws Exception {
    sync(ownerSession.getId().toString(), owner).andExpect(status().isBadRequest());
  }

  private ResultActions publish(String description) throws Exception {
    return mockMvc.perform(post("/api/v1/share/sessions/{id}/publish", ownerSession.getId())
            .with(TestHelpers.authenticatedAs(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new PublishSessionRequest().description(description))));
  }

  private ResultActions subscribe(String shareCode) throws Exception {
    return subscribe(shareCode, subscriber);
  }

  private ResultActions subscribe(String shareCode, UserEntity user) throws Exception {
    return mockMvc.perform(post("/api/v1/share/sessions/subscribe")
            .with(TestHelpers.authenticatedAs(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SubscribeRequest().shareCode(shareCode))));
  }

  private ResultActions sync(String sessionId) throws Exception {
    return sync(sessionId, subscriber);
  }

  private ResultActions sync(String sessionId, UserEntity user) throws Exception {
    return mockMvc.perform(post("/api/v1/share/sessions/{id}/sync", sessionId)
            .with(TestHelpers.authenticatedAs(user)));
  }

  private ResultActions updateBoard(JsonNode board, BoardUpdateRequest request) throws Exception {
    return mockMvc.perform(put("/api/v1/boards/{id}", board.get("id").asText())
            .with(TestHelpers.authenticatedAs(subscriber))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
  }

  private BoardUpdateRequest playbackUpdate(JsonNode board) {
    BoardUpdateRequest request = new BoardUpdateRequest()
            .name(board.get("name").asText())
            .volume(board.get("volume").asInt());
    if (board.hasNonNull("selectedGroup")) {
      request.selectedGroupId(UUID.fromString(board.get("selectedGroup").get("id").asText()));
    }
    if (board.hasNonNull("selectedTrack")) {
      request.selectedTrackId(UUID.fromString(board.get("selectedTrack").get("id").asText()));
    }
    if (board.hasNonNull("selectedWindow")) {
      request.selectedWindowId(UUID.fromString(board.get("selectedWindow").get("id").asText()));
    }
    if (board.hasNonNull("linkedBoard")) {
      request.linkedBoard(new org.dnd.api.model.LinkedBoard()
              .boardId(UUID.fromString(board.get("linkedBoard").get("boardId").asText()))
              .mode(org.dnd.api.model.LinkedBoardMode.fromValue(board.get("linkedBoard").get("mode").asText())));
    }
    return request;
  }

  private JsonNode boardNamed(JsonNode session, String name) {
    for (JsonNode board : session.get("boards")) {
      if (board.get("name").asText().equals(name)) {
        return board;
      }
    }
    throw new AssertionError("No board named " + name);
  }

  private String shareCodeOf(ResultActions result) throws Exception {
    return json(result).get("shareCode").asText();
  }

  private JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private TrackEntity createTrack(String name, UserEntity trackOwner) {
    TrackEntity track = new TrackEntity();
    track.setTrackName(name);
    track.setTrackOriginalName(name);
    track.setTrackLink("https://example.com/" + UUID.randomUUID());
    track.setDuration(180);
    track.setOwner(trackOwner);
    return trackRepository.save(track);
  }
}
