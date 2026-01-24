package org.dnd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.api.model.GroupRequest;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private JwtService jwtService;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private BoardRepository boardRepository;

    private UserEntity testUser;
    private String authToken;

    @BeforeEach
    void setUp() {
        boardRepository.deleteAll();
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


    private String getTokenForUser(UserEntity user) {
        UserAuthDTO userAuth = new UserAuthDTO();
        userAuth.setId(user.getId());
        userAuth.setName(user.getName());
        return jwtService.generateToken(userAuth);
    }

}
