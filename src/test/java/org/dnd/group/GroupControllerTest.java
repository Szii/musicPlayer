package org.dnd.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.TestHelpers;
import org.dnd.api.model.GroupRequest;
import org.dnd.api.model.GroupTrackRef;
import org.dnd.api.model.GroupTrackRequest;
import org.dnd.api.model.ReorderGroupTracksRequest;
import org.dnd.board.BoardRepository;
import org.dnd.exception.ErrorCode;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.TrackWindowEntity;
import org.dnd.user.UserEntity;
import org.dnd.user.UserHelper;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankLimits;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupControllerTest extends DatabaseBase {
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private GroupRepository groupRepository;
  @Autowired
  private TrackRepository trackRepository;
  @Autowired
  private BoardRepository boardRepository;

  private UserEntity testUser;

  @BeforeEach
  void setUp() {
    testUser = UserHelper.createValidatedUser(
            "testUser",
            "password",
            "user@email.com");
    userRepository.save(TestHelpers.withKeycloakId(testUser));
  }

  @Test
  void getUserGroups_Success() throws Exception {
    GroupEntity group = new GroupEntity();
    group.setListName("Test Group");
    group.setOwner(testUser);
    groupRepository.save(group);

    mockMvc.perform(get("/api/v1/groups")
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].listName").value("Test Group"));
  }


  @Test
  void getUserGroups_Forbidden() throws Exception {
    GroupEntity group = new GroupEntity();
    group.setListName("Test Group");
    group.setOwner(testUser);
    groupRepository.save(group);

    mockMvc.perform(get("/api/v1/groups")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + "invalidToken"))
            .andExpect(status().isUnauthorized());
  }

  @Test
  void createGroup_Success() throws Exception {
    GroupRequest groupRequest = new GroupRequest()
            .listName("New Group");

    mockMvc.perform(post("/api/v1/groups")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(groupRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listName").value("New Group"));

    List<GroupEntity> groups = groupRepository.findByOwner_Id(testUser.getId());
    assertEquals(1, groups.size());
    assertEquals("New Group", groups.getFirst().getListName());
  }

  @Test
  void updateGroup_Success() throws Exception {
    GroupEntity group = new GroupEntity();
    group.setListName("Original Name");
    group.setOwner(testUser);
    group = groupRepository.save(group);

    GroupRequest updateRequest = new GroupRequest()
            .listName("Updated Name");

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listName").value("Updated Name"));

    GroupEntity updated = groupRepository.findById(group.getId()).orElseThrow();
    assertEquals("Updated Name", updated.getListName());
  }

  @Test
  void updateGroup_appliesCustomTrackName() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity track = createTrack("Original Song", testUser, null);

    GroupRequest updateRequest = new GroupRequest()
            .listName("Group")
            .tracks(List.of(new GroupTrackRequest().trackId(track.getId()).name("Custom Name")));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].id").value(track.getId().toString()))
            .andExpect(jsonPath("$.tracks[0].trackName").value("Custom Name"));
  }

  @Test
  void updateGroup_nullCustomTrackNameFallsBackToTrackName() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity track = createTrack("Original Song", testUser, null);

    GroupRequest withName = new GroupRequest()
            .listName("Group")
            .tracks(List.of(new GroupTrackRequest().trackId(track.getId()).name("Custom Name")));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(withName)))
            .andExpect(status().isOk());

    GroupRequest cleared = new GroupRequest()
            .listName("Group")
            .tracks(List.of(new GroupTrackRequest().trackId(track.getId())));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cleared)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].trackName").value("Original Song"));
  }

  @Test
  void updateGroup_addsRenamedWindowItem() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity track = createTrack("Song", testUser, null);
    TrackWindowEntity window = createWindow(track, "Intro", 0);

    GroupRequest request = new GroupRequest()
            .listName("Group")
            .tracks(List.of(new GroupTrackRequest()
                    .trackId(track.getId())
                    .windowId(window.getId())
                    .name("Custom Window Name")));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].id").value(track.getId().toString()))
            .andExpect(jsonPath("$.tracks[0].isWindow").value(true))
            .andExpect(jsonPath("$.tracks[0].windowId").value(window.getId().toString()))
            .andExpect(jsonPath("$.tracks[0].trackName").value("Custom Window Name"));
  }

  @Test
  void updateGroup_windowItemFallsBackToWindowName() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity track = createTrack("Song", testUser, null);
    TrackWindowEntity window = createWindow(track, "Intro", 0);

    GroupRequest request = new GroupRequest()
            .listName("Group")
            .tracks(List.of(new GroupTrackRequest()
                    .trackId(track.getId())
                    .windowId(window.getId())));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].isWindow").value(true))
            .andExpect(jsonPath("$.tracks[0].trackName").value("Intro"));
  }

  @Test
  void updateGroup_trackAndItsWindowCoexist() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity track = createTrack("Song", testUser, null);
    TrackWindowEntity window = createWindow(track, "Intro", 0);

    GroupRequest request = new GroupRequest()
            .listName("Group")
            .tracks(List.of(
                    new GroupTrackRequest().trackId(track.getId()),
                    new GroupTrackRequest().trackId(track.getId()).windowId(window.getId())));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks.length()").value(2));
  }

  @Test
  void updateGroup_rejectsWindowNotBelongingToTrack() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity track = createTrack("Song", testUser, null);
    TrackEntity otherTrack = createTrack("Other", testUser, null);
    TrackWindowEntity otherWindow = createWindow(otherTrack, "Intro", 0);

    GroupRequest request = new GroupRequest()
            .listName("Group")
            .tracks(List.of(new GroupTrackRequest()
                    .trackId(track.getId())
                    .windowId(otherWindow.getId())));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
  }

  @Test
  void updateGroup_assignsPositionsInAppendOrder() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity a = createTrack("A", testUser, null);
    TrackEntity b = createTrack("B", testUser, null);
    TrackEntity c = createTrack("C", testUser, null);

    GroupRequest initial = new GroupRequest()
            .listName("Group")
            .tracks(List.of(
                    new GroupTrackRequest().trackId(a.getId()),
                    new GroupTrackRequest().trackId(b.getId()),
                    new GroupTrackRequest().trackId(c.getId())));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(initial)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].id").value(a.getId().toString()))
            .andExpect(jsonPath("$.tracks[0].positionWithinGroup").value(1))
            .andExpect(jsonPath("$.tracks[1].id").value(b.getId().toString()))
            .andExpect(jsonPath("$.tracks[1].positionWithinGroup").value(2))
            .andExpect(jsonPath("$.tracks[2].id").value(c.getId().toString()))
            .andExpect(jsonPath("$.tracks[2].positionWithinGroup").value(3));
  }

  @Test
  void updateGroup_preservesOrderOfRetainedItems() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity a = createTrack("A", testUser, null);
    TrackEntity b = createTrack("B", testUser, null);

    GroupRequest initial = new GroupRequest()
            .listName("Group")
            .tracks(List.of(
                    new GroupTrackRequest().trackId(a.getId()),
                    new GroupTrackRequest().trackId(b.getId())));
    performUpdate(group, initial);

    GroupRequest resentInDifferentOrder = new GroupRequest()
            .listName("Group")
            .tracks(List.of(
                    new GroupTrackRequest().trackId(b.getId()),
                    new GroupTrackRequest().trackId(a.getId())));

    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(resentInDifferentOrder)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].id").value(a.getId().toString()))
            .andExpect(jsonPath("$.tracks[1].id").value(b.getId().toString()));
  }

  @Test
  void reorderGroupTracks_reordersItems() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity a = createTrack("A", testUser, null);
    TrackEntity b = createTrack("B", testUser, null);
    TrackEntity c = createTrack("C", testUser, null);
    TrackWindowEntity aIntro = createWindow(a, "Intro", 0);

    performUpdate(group, new GroupRequest()
            .listName("Group")
            .tracks(List.of(
                    new GroupTrackRequest().trackId(a.getId()),
                    new GroupTrackRequest().trackId(b.getId()),
                    new GroupTrackRequest().trackId(c.getId()),
                    new GroupTrackRequest().trackId(a.getId()).windowId(aIntro.getId()))));

    ReorderGroupTracksRequest reorder = new ReorderGroupTracksRequest()
            .tracks(List.of(
                    new GroupTrackRef().trackId(a.getId()).windowId(aIntro.getId()),
                    new GroupTrackRef().trackId(c.getId()),
                    new GroupTrackRef().trackId(a.getId()),
                    new GroupTrackRef().trackId(b.getId())));

    mockMvc.perform(patch("/api/v1/groups/{groupId}/reorder", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reorder)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].isWindow").value(true))
            .andExpect(jsonPath("$.tracks[0].windowId").value(aIntro.getId().toString()))
            .andExpect(jsonPath("$.tracks[0].positionWithinGroup").value(1))
            .andExpect(jsonPath("$.tracks[1].id").value(c.getId().toString()))
            .andExpect(jsonPath("$.tracks[1].positionWithinGroup").value(2))
            .andExpect(jsonPath("$.tracks[2].id").value(a.getId().toString()))
            .andExpect(jsonPath("$.tracks[2].isWindow").value(false))
            .andExpect(jsonPath("$.tracks[2].positionWithinGroup").value(3))
            .andExpect(jsonPath("$.tracks[3].id").value(b.getId().toString()))
            .andExpect(jsonPath("$.tracks[3].positionWithinGroup").value(4));
  }

  @Test
  void reorderGroupTracks_rejectsIncompleteSet() throws Exception {
    GroupEntity group = createGroup("Group", testUser);
    TrackEntity a = createTrack("A", testUser, null);
    TrackEntity b = createTrack("B", testUser, null);

    performUpdate(group, new GroupRequest()
            .listName("Group")
            .tracks(List.of(
                    new GroupTrackRequest().trackId(a.getId()),
                    new GroupTrackRequest().trackId(b.getId()))));

    ReorderGroupTracksRequest reorder = new ReorderGroupTracksRequest()
            .tracks(List.of(new GroupTrackRef().trackId(a.getId())));

    mockMvc.perform(patch("/api/v1/groups/{groupId}/reorder", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reorder)))
            .andExpect(status().isBadRequest());
  }

  @Test
  void updateGroup_NotFound() throws Exception {
    GroupRequest updateRequest = new GroupRequest()
            .listName("Updated Name");

    mockMvc.perform(put("/api/v1/groups/{groupId}", UUID.randomUUID())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isNotFound());
  }

  @Test
  void createGroup_isForbidden_whenGroupsLimitReached() throws Exception {
    for (int i = 0; i < UserRankLimits.normal().maxGroups(); i++) {
      GroupEntity group = new GroupEntity();
      group.setListName("Group " + i);
      group.setOwner(testUser);
      groupRepository.save(group);
    }

    GroupRequest groupRequest = new GroupRequest()
            .listName("New Group");

    mockMvc.perform(post("/api/v1/groups")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(groupRequest)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.LIMIT_EXCEEDED.getCode()));

  }

  @Test
  void deleteGroup_Success() throws Exception {
    GroupEntity group = new GroupEntity();
    group.setListName("To Delete");
    group.setOwner(testUser);
    group = groupRepository.save(group);

    mockMvc.perform(delete("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNoContent());

    assertFalse(groupRepository.existsById(group.getId()));
  }

  @Test
  void deleteGroup_NotFound() throws Exception {
    mockMvc.perform(delete("/api/v1/groups/{groupId}", UUID.randomUUID())
                    .with(TestHelpers.authenticatedAs(testUser)))
            .andExpect(status().isNotFound());
  }

  @Test
  void createGroup_InvalidRequest() throws Exception {
    GroupRequest groupRequest = new GroupRequest();

    mockMvc.perform(post("/api/v1/groups")
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(groupRequest)))
            .andExpect(status().isBadRequest());
  }


  private UserEntity createUser(String name) {
    UserEntity user = UserHelper.createValidatedUser(name, "password", name + "@email.com");
    return userRepository.save(user);
  }

  private void performUpdate(GroupEntity group, GroupRequest request) throws Exception {
    mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                    .with(TestHelpers.authenticatedAs(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
  }

  private GroupEntity createGroup(String name, UserEntity owner) {
    GroupEntity group = new GroupEntity();
    group.setListName(name);
    group.setOwner(owner);
    return groupRepository.save(group);
  }

  private TrackEntity createTrack(String name, UserEntity owner, GroupEntity group) {
    TrackEntity track = new TrackEntity();
    track.setTrackName(name);
    track.setTrackOriginalName(name);
    track.setTrackLink("https://example.com/" + name + ".mp3");
    track.setDuration(120);
    track.setOwner(owner);

    TrackEntity saved = trackRepository.save(track);

    if (group != null) {
      groupRepository.addTrackToGroup(group.getId(), saved.getId());
    }

    return saved;
  }

  private TrackWindowEntity createWindow(TrackEntity track, String name, int position) {
    TrackWindowEntity window = new TrackWindowEntity();
    window.setName(name);
    window.setPositionFrom(0L);
    window.setPositionTo(10L);
    window.setFadeInDurationMs(0);
    window.setFadeOutDurationMs(0);
    window.setPositionWithinTrack(position);

    track.addTrackWindow(window);
    TrackEntity saved = trackRepository.save(track);

    return saved.getTrackWindows().stream()
            .filter(w -> name.equals(w.getName()))
            .findFirst()
            .orElseThrow();
  }
}
