package org.dnd.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.TestHelpers;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.api.model.CreateTrackRequestV2;
import org.dnd.api.model.GroupRequest;
import org.dnd.api.model.SubscribeRequest;
import org.dnd.board.BoardEntity;
import org.dnd.board.BoardRepository;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.trackShare.TrackShareEntity;
import org.dnd.user.UserEntity;
import org.dnd.user.UserHelper;
import org.dnd.user.UserRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionScopeTest extends DatabaseBase {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private SessionRepository sessionRepository;
  @Autowired
  private TrackRepository trackRepository;
  @Autowired
  private GroupRepository groupRepository;
  @Autowired
  private BoardRepository boardRepository;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private UserEntity testUser;
  private UserEntity otherUser;
  private SessionEntity session;

  @BeforeEach
  void setUp() {
    testUser = userRepository.save(TestHelpers.withKeycloakId(
            UserHelper.createValidatedUser("testUser", "password", "email@email.cz")));
    otherUser = userRepository.save(TestHelpers.withKeycloakId(
            UserHelper.createValidatedUser("otherUser", "password", "other@email.cz")));

    session = new SessionEntity();
    session.setName("Session");
    session.setOwner(testUser);
    session = sessionRepository.save(session);
  }

  @Test
  void addAndRemoveTrack() throws Exception {
    TrackEntity track = createTrack("Track", testUser);

    mockMvc.perform(put("/api/v1/sessions/{sessionId}/tracks/{trackId}", session.getId(), track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackIds").value(Matchers.contains(track.getId().toString())));

    mockMvc.perform(put("/api/v1/sessions/{sessionId}/tracks/{trackId}", session.getId(), track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackIds.length()").value(1));

    mockMvc.perform(delete("/api/v1/sessions/{sessionId}/tracks/{trackId}", session.getId(), track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackIds").isEmpty());

    assertEquals(0, sessionTrackIds().size());
  }

  @Test
  void addTrack_isNotFound_whenTrackNotAccessible() throws Exception {
    TrackEntity foreignTrack = createTrack("Foreign", otherUser);

    mockMvc.perform(put("/api/v1/sessions/{sessionId}/tracks/{trackId}", session.getId(), foreignTrack.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNotFound());
  }

  @Test
  void addTrack_isNotFound_whenSessionNotOwned() throws Exception {
    TrackEntity track = createTrack("Track", otherUser);

    mockMvc.perform(put("/api/v1/sessions/{sessionId}/tracks/{trackId}", session.getId(), track.getId())
                    .with(TestHelpers.authenticatedAs(otherUser)))
            .andExpect(status().isNotFound());
  }

  @Test
  void addAndRemoveGroup() throws Exception {
    GroupEntity group = createGroup("Group", testUser);

    mockMvc.perform(put("/api/v1/sessions/{sessionId}/groups/{groupId}", session.getId(), group.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupIds").value(Matchers.contains(group.getId().toString())));

    mockMvc.perform(delete("/api/v1/sessions/{sessionId}/groups/{groupId}", session.getId(), group.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupIds").isEmpty());
  }

  @Test
  void addGroup_isNotFound_whenGroupNotOwned() throws Exception {
    GroupEntity foreignGroup = createGroup("Foreign", otherUser);

    mockMvc.perform(put("/api/v1/sessions/{sessionId}/groups/{groupId}", session.getId(), foreignGroup.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNotFound());
  }

  @Test
  void boardWithoutGroup_offersSessionTracksAndTracksOfSessionGroups() throws Exception {
    TrackEntity sessionTrack = createTrack("In session", testUser);
    TrackEntity groupTrack = createTrack("In session group", testUser);
    createTrack("Outside session", testUser);

    GroupEntity group = new GroupEntity();
    group.setListName("Group");
    group.setOwner(testUser);
    group.addTrack(groupTrack);
    group = groupRepository.save(group);

    attach(sessionTrack.getId(), "tracks");
    attach(group.getId(), "groups");

    BoardEntity board = createBoard();

    mockMvc.perform(get("/api/v1/boards/{boardId}", board.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableTracks[*].id").value(Matchers.containsInAnyOrder(
                    sessionTrack.getId().toString(),
                    groupTrack.getId().toString()
            )));
  }

  @Test
  void selectingTrackAndGroupOnBoard_addsThemToSession() throws Exception {
    TrackEntity track = createTrack("Track", testUser);
    GroupEntity group = createGroup("Group", testUser);
    BoardEntity board = createBoard();

    mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new BoardUpdateRequest()
                            .selectedTrackId(track.getId())
                            .selectedGroupId(group.getId()))))
            .andExpect(status().isOk());

    assertEquals(List.of(track.getId()), sessionTrackIds());
    assertEquals(List.of(group.getId()), sessionGroupIds());
  }

  @Test
  void updatingBoardWithUnchangedSelection_doesNotReAddRemovedTrack() throws Exception {
    TrackEntity track = createTrack("Track", testUser);
    BoardEntity board = createBoard();
    board.setSelectedTrack(track);
    boardRepository.save(board);

    mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new BoardUpdateRequest()
                            .selectedTrackId(track.getId())
                            .volume(80))))
            .andExpect(status().isOk());

    assertEquals(List.of(), sessionTrackIds());
  }

  @Test
  void createTrack_withSessionId_addsTrackToSession() throws Exception {
    CreateTrackRequestV2 request = new CreateTrackRequestV2()
            .trackName("Imported")
            .trackOriginalName("Imported original")
            .trackLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            .duration(120)
            .sessionId(session.getId());

    mockMvc.perform(post("/api/v1/v2/tracks")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    TrackEntity created = trackRepository.findByOwner_Id(testUser.getId()).getFirst();
    assertEquals(List.of(created.getId()), sessionTrackIds());
  }

  @Test
  void createTrack_isNotFound_whenSessionNotOwned() throws Exception {
    CreateTrackRequestV2 request = new CreateTrackRequestV2()
            .trackName("Imported")
            .trackOriginalName("Imported original")
            .trackLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            .duration(120)
            .sessionId(session.getId());

    mockMvc.perform(post("/api/v1/v2/tracks")
                    .with(TestHelpers.authenticatedAs(otherUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());

    assertEquals(0, trackRepository.findByOwner_Id(otherUser.getId()).size());
  }

  @Test
  void createGroup_withSessionId_addsGroupToSession() throws Exception {
    GroupRequest request = new GroupRequest()
            .listName("Group")
            .sessionId(session.getId());

    mockMvc.perform(post("/api/v1/groups")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    GroupEntity created = groupRepository.findByOwner_Id(testUser.getId()).getFirst();
    assertEquals(List.of(created.getId()), sessionGroupIds());
  }

  @Test
  void subscribeWithSessionId_addsTrack_andUnsubscribeRemovesIt() throws Exception {
    TrackEntity sharedTrack = createTrack("Shared", otherUser);
    TrackShareEntity share = new TrackShareEntity();
    share.setShareCode(UUID.randomUUID().toString());
    share.setTrack(sharedTrack);
    sharedTrack.setTrackShare(share);
    sharedTrack = trackRepository.saveAndFlush(sharedTrack);

    SubscribeRequest request = new SubscribeRequest()
            .shareCode(share.getShareCode())
            .sessionId(session.getId());

    mockMvc.perform(post("/api/v1/share/subscribe")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

    assertEquals(List.of(sharedTrack.getId()), sessionTrackIds());

    mockMvc.perform(delete("/api/v1/share/unsubscribe/{trackId}", sharedTrack.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    assertEquals(List.of(), sessionTrackIds());
  }

  @Test
  void deletingTrack_removesItFromSession() throws Exception {
    TrackEntity track = createTrack("Track", testUser);
    attach(track.getId(), "tracks");

    mockMvc.perform(delete("/api/v1/tracks/{trackId}", track.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    assertEquals(List.of(), sessionTrackIds());
  }

  private void attach(UUID id, String collection) throws Exception {
    mockMvc.perform(put("/api/v1/sessions/{sessionId}/" + collection + "/{id}", session.getId(), id)
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk());
  }

  private List<UUID> sessionTrackIds() {
    return jdbcTemplate.queryForList(
            "select track_id from session_tracks where session_id = ?", UUID.class, session.getId());
  }

  private List<UUID> sessionGroupIds() {
    return jdbcTemplate.queryForList(
            "select group_id from session_groups where session_id = ?", UUID.class, session.getId());
  }

  private TrackEntity createTrack(String name, UserEntity owner) {
    TrackEntity track = new TrackEntity();
    track.setTrackName(name);
    track.setTrackOriginalName(name);
    track.setTrackLink("https://example.com/" + UUID.randomUUID());
    track.setDuration(120);
    track.setOwner(owner);
    return trackRepository.save(track);
  }

  private GroupEntity createGroup(String name, UserEntity owner) {
    GroupEntity group = new GroupEntity();
    group.setListName(name);
    group.setOwner(owner);
    return groupRepository.save(group);
  }

  private BoardEntity createBoard() {
    BoardEntity board = new BoardEntity();
    board.setName("Board");
    board.setOwner(testUser);
    board.setSession(session);
    return boardRepository.save(board);
  }
}
