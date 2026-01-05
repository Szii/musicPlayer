package org.dnd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.api.model.GroupRequest;
import org.dnd.api.model.GroupShareRequest;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.model.UserGroupShareEntity;
import org.dnd.repository.*;
import org.dnd.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
    private UserGroupShareRepository userGroupShareRepository;
    @Autowired
    private UserTrackShareRepository userTrackShareRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private TrackRepository trackRepository;


    private UserEntity testUser;
    private String authToken;

    @BeforeEach
    void setUp() {
        userTrackShareRepository.deleteAll();
        userGroupShareRepository.deleteAll();
        trackRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setName("testUser");
        testUser.setPassword("password");
        testUser = userRepository.save(testUser);

        authToken = getTokenForUser(testUser);
    }

    @Test
    void listGroupShares_Success() throws Exception {
        UserEntity sharedUser = createUser("sharedUser");
        GroupEntity group = createGroup("Shared Group", testUser);
        createGroupShare(group, sharedUser);

        mockMvc.perform(get("/api/v1/groups/{groupId}/shares", group.getId())
                        .param("userId", testUser.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].user.name").value(sharedUser.getName()));
    }


    @Test
    void getUserTracks_SharedViaGroup_Success() throws Exception {
        UserEntity otherUser = createUser("otherUser");
        GroupEntity group = createGroup("Shared Group", otherUser);
        TrackEntity track1 = createTrack("Track 1", otherUser, group);
        TrackEntity track2 = createTrack("Track 2", otherUser, group);

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        shareGroup(group.getId(), testUser.getId(), otherUser);

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].trackName").value(containsInAnyOrder("Track 1", "Track 2")))
                .andExpect(jsonPath("$[*].ownerId").value(everyItem(is(otherUser.getId().intValue()))))
                .andExpect(jsonPath("$[*].groupId").value(everyItem(is(group.getId().intValue()))));
    }


    @Test
    void unshareGroup_UnsharesTracks() throws Exception {
        UserEntity otherUser = createUser("otherUser");
        GroupEntity group = createGroup("Shared Group", otherUser);
        TrackEntity track1 = createTrack("Track 1", otherUser, group);
        TrackEntity track2 = createTrack("Track 2", otherUser, group);

        UserAuthDTO otherUserAuth = new UserAuthDTO();
        otherUserAuth.setId(otherUser.getId());
        otherUserAuth.setName(otherUser.getName());
        String otherUserToken = jwtService.generateToken(otherUserAuth);

        GroupShareRequest shareRequest = new GroupShareRequest()
                .userId(testUser.getId());

        mockMvc.perform(post("/api/v1/groups/{groupId}/shares", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].trackName").value(containsInAnyOrder("Track 1", "Track 2")));

        mockMvc.perform(delete("/api/v1/groups/{groupId}/shares/{userId}", group.getId(), testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

    }


    @Test
    void shareGroup_AlreadyShared() throws Exception {
        GroupEntity group = createGroup("Test Group", testUser);
        UserEntity targetUser = createUser("targetUser");

        createGroupShare(group, targetUser);

        GroupShareRequest request = new GroupShareRequest()
                .userId(targetUser.getId());

        mockMvc.perform(post("/api/v1/groups/{groupId}/shares", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<UserGroupShareEntity> shares = userGroupShareRepository
                .findByUser_Id(targetUser.getId());
        assertEquals(1, shares.size());
    }

    private void createGroupShare(GroupEntity group, UserEntity sharedUser) {
        UserGroupShareEntity access = new UserGroupShareEntity();
        access.setUser(sharedUser);
        access.setGroup(group);
        userGroupShareRepository.save(access);
    }

    @Test
    void unshareGroup_UserUnsharesSelf_Success() throws Exception {
        GroupEntity group = createGroup("Shared Group", testUser);
        UserEntity sharedUser = createUser("sharedUser");

        createGroupShare(group, sharedUser);

        UserAuthDTO sharedUserAuth = new UserAuthDTO();
        sharedUserAuth.setId(sharedUser.getId());
        sharedUserAuth.setName(sharedUser.getName());
        String sharedUserToken = jwtService.generateToken(sharedUserAuth);

        mockMvc.perform(delete("/api/v1/groups/{groupId}/shares/{userId}", group.getId(), sharedUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sharedUserToken))
                .andExpect(status().isOk());

        assertFalse(userGroupShareRepository.existsByUser_IdAndGroup_Id(sharedUser.getId(), group.getId()));
    }

    @Test
    void unshareGroup_NonOwnerUnshareOther_Forbidden() throws Exception {
        GroupEntity group = createGroup("Shared Group", testUser);
        UserEntity sharedUser1 = createUser("sharedUser1");
        UserEntity sharedUser2 = createUser("sharedUser2");
        createGroupShare(group, sharedUser1);
        createGroupShare(group, sharedUser2);


        UserAuthDTO sharedUserAuth1 = new UserAuthDTO();
        sharedUserAuth1.setId(sharedUser1.getId());
        sharedUserAuth1.setName(sharedUser1.getName());
        String sharedUserToken1 = jwtService.generateToken(sharedUserAuth1);


        mockMvc.perform(delete("/api/v1/groups/{groupId}/shares/{userId}", group.getId(), sharedUser2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sharedUserToken1))
                .andExpect(status().isForbidden());

        assertTrue(userGroupShareRepository.existsByUser_IdAndGroup_Id(sharedUser2.getId(), group.getId()));
    }

    @Test
    void shareGroup_NonOwner_Forbidden() throws Exception {
        GroupEntity group = createGroup("Shared Group", testUser);
        UserEntity sharedUser = createUser("sharedUser");
        UserEntity targetUser = createUser("targetUser");
        createGroupShare(group, sharedUser);

        UserAuthDTO sharedUserAuth = new UserAuthDTO();
        sharedUserAuth.setId(sharedUser.getId());
        sharedUserAuth.setName(sharedUser.getName());
        String sharedUserToken = jwtService.generateToken(sharedUserAuth);

        GroupShareRequest request = new GroupShareRequest().userId(targetUser.getId());

        mockMvc.perform(post("/api/v1/groups/{groupId}/shares", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sharedUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }


    @Test
    void listGroupShares_NonOwner_Forbidden() throws Exception {
        UserEntity owner = createUser("ownerUser");
        GroupEntity group = createGroup("Shared Group", owner);

        UserEntity sharedUser = createUser("otherUser");
        createGroupShare(group, sharedUser);

        UserAuthDTO sharedUserAuth = new UserAuthDTO();
        sharedUserAuth.setId(sharedUser.getId());
        sharedUserAuth.setName(sharedUser.getName());
        String sharedUserToken = jwtService.generateToken(sharedUserAuth);

        mockMvc.perform(get("/api/v1/groups/{groupId}/shares", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sharedUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserGroups_Success() throws Exception {
        GroupEntity group = new GroupEntity();
        group.setListName("Test Group");
        group.setOwner(testUser);
        groupRepository.save(group);

        mockMvc.perform(get("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].listName").value("Test Group"))
                .andExpect(jsonPath("$[0].ownerId").value(testUser.getId()));
    }

    @Test
    void getUserGroups_Forbidden() throws Exception {
        GroupEntity group = new GroupEntity();
        group.setListName("Test Group");
        group.setOwner(testUser);
        groupRepository.save(group);

        mockMvc.perform(get("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + "invalidToken"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createGroup_Success() throws Exception {
        GroupRequest groupRequest = new GroupRequest()
                .listName("New Group")
                .ownerId(testUser.getId());

        mockMvc.perform(post("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(groupRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listName").value("New Group"))
                .andExpect(jsonPath("$.ownerId").value(testUser.getId()));

        List<GroupEntity> groups = groupRepository.findByOwner_Id(testUser.getId());
        assertEquals(1, groups.size());
        assertEquals("New Group", groups.get(0).getListName());
    }

    @Test
    void updateGroup_Success() throws Exception {
        GroupEntity group = new GroupEntity();
        group.setListName("Original Name");
        group.setOwner(testUser);
        group = groupRepository.save(group);

        GroupRequest updateRequest = new GroupRequest()
                .listName("Updated Name")
                .ownerId(testUser.getId());

        mockMvc.perform(put("/api/v1/groups/{groupId}", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listName").value("Updated Name"));

        GroupEntity updated = groupRepository.findById(group.getId()).orElseThrow();
        assertEquals("Updated Name", updated.getListName());
    }

    @Test
    void updateGroup_NotFound() throws Exception {
        GroupRequest updateRequest = new GroupRequest()
                .listName("Updated Name")
                .ownerId(testUser.getId());

        mockMvc.perform(put("/api/v1/groups/{groupId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup_Success() throws Exception {
        GroupEntity group = new GroupEntity();
        group.setListName("To Delete");
        group.setOwner(testUser);
        group = groupRepository.save(group);

        mockMvc.perform(delete("/api/v1/groups/{groupId}", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNoContent());

        assertFalse(groupRepository.existsById(group.getId()));
    }

    @Test
    void deleteGroup_NotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/{groupId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void createGroup_InvalidRequest() throws Exception {
        GroupRequest groupRequest = new GroupRequest()
                .ownerId(testUser.getId());

        mockMvc.perform(post("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(groupRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserGroups_OwnAndSharedGroups_Success() throws Exception {
        GroupEntity ownGroup = createGroup("Own Group", testUser);
        UserEntity otherUser = createUser("otherUser");
        GroupEntity sharedGroup = createGroup("Shared Group", otherUser);
        createGroupShare(sharedGroup, testUser);

        mockMvc.perform(get("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].listName").value(containsInAnyOrder("Own Group", "Shared Group")))
                .andExpect(jsonPath("$[*].ownerId").value(containsInAnyOrder(
                        testUser.getId().intValue(),
                        otherUser.getId().intValue()
                )));

        List<GroupEntity> accessibleGroups = groupRepository.findAccessibleGroupsForUser(testUser.getId());
        assertEquals(2, accessibleGroups.size());
    }


    @Test
    void unshareGroup_UserUnsharesSelf_UnsharesAllTracks() throws Exception {
        UserEntity owner = createUser("owner");
        GroupEntity group = createGroup("Owner's Group", owner);
        TrackEntity track1 = createTrack("Track 1", owner, group);
        TrackEntity track2 = createTrack("Track 2", owner, group);

        shareGroup(group.getId(), testUser.getId(), owner);

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].trackName").value(containsInAnyOrder("Track 1", "Track 2")));

        mockMvc.perform(delete("/api/v1/groups/{groupId}/shares/{userId}", group.getId(), testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteGroup_UnsharesAllTracks() throws Exception {
        UserEntity owner = createUser("owner");
        GroupEntity group = createGroup("Owner's Group", owner);
        TrackEntity track1 = createTrack("Track 1", owner, group);
        TrackEntity track2 = createTrack("Track 2", owner, group);

        String ownerAuthToken = getTokenForUser(owner);

        shareGroup(group.getId(), testUser.getId(), owner);

        mockMvc.perform(delete("/api/v1/groups/{groupId}", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAuthToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteGroup_preservesTracksForOwner() throws Exception {
        UserEntity owner = createUser("owner");
        GroupEntity group = createGroup("Owner's Group", owner);
        TrackEntity track1 = createTrack("Track 1", owner, group);
        TrackEntity track2 = createTrack("Track 2", owner, group);

        mockMvc.perform(delete("/api/v1/groups/{groupId}", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTokenForUser(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTokenForUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void shareGroup_GroupNotFound() throws Exception {
        GroupShareRequest request = new GroupShareRequest().userId(testUser.getId());

        mockMvc.perform(post("/api/v1/groups/{groupId}/shares", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shareGroup_UserNotFound() throws Exception {
        GroupEntity group = createGroup("Test Group", testUser);
        GroupShareRequest request = new GroupShareRequest().userId(999L);

        mockMvc.perform(post("/api/v1/groups/{groupId}/shares", group.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unshareGroup_GroupNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/{groupId}/shares/{userId}", 999L, testUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unshareGroup_NotShared() throws Exception {
        GroupEntity group = createGroup("Test Group", testUser);
        UserEntity otherUser = createUser("otherUser");

        mockMvc.perform(delete("/api/v1/groups/{groupId}/shares/{userId}", group.getId(), otherUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk());
    }


    private UserEntity createUser(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setPassword("password");
        return userRepository.save(user);
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
        track.setTrackLink("https://example.com/" + name + ".mp3");
        track.setDuration(120);
        track.setOwner(owner);

        TrackEntity saved = trackRepository.save(track);

        if (group != null) {
            groupRepository.addTrackToGroup(group.getId(), saved.getId());
        }

        return saved;
    }

    private void shareGroup(Long groupId, Long userId, UserEntity owner) throws Exception {
        UserAuthDTO ownerAuth = new UserAuthDTO();
        ownerAuth.setId(owner.getId());
        ownerAuth.setName(owner.getName());
        String ownerToken = jwtService.generateToken(ownerAuth);

        GroupShareRequest shareRequest = new GroupShareRequest().userId(userId);

        mockMvc.perform(post("/api/v1/groups/{groupId}/shares", groupId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isOk());
    }

    private String getTokenForUser(UserEntity user) {
        UserAuthDTO userAuth = new UserAuthDTO();
        userAuth.setId(user.getId());
        userAuth.setName(user.getName());
        return jwtService.generateToken(userAuth);
    }

}
