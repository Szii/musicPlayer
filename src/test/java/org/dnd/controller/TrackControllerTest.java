package org.dnd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.api.model.TrackRequest;
import org.dnd.api.model.TrackWindowRequest;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackWindowEntity;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
    private JwtService jwtService;

    private UserEntity testUser;
    private String authToken;

    @BeforeEach
    void setUp() {
        trackWindowRepository.deleteAll();
        trackRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        testUser = createUser("testUser");
        authToken = getTokenForUser(testUser);
    }

    @Test
    void getUserTracks_OwnTracks_Success() throws Exception {
        TrackEntity t1 = createTrackEntity("T1", testUser, null);
        TrackEntity t2 = createTrackEntity("T2", testUser, null);

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].trackName").value(containsInAnyOrder("T1", "T2")))
                .andExpect(jsonPath("$[*].ownerId").value(everyItem(is(testUser.getId().intValue()))));
    }

    @Test
    void createTrack_Success() throws Exception {
        TrackRequest req = new TrackRequest()
                .trackName("New Track")
                .trackLink("https://www.youtube.com/watch?v=gbFGnw2JYe0&list=PLDtPBNsaMdk-M7oRThTgSQm--LuxMUW4S");

        mockMvc.perform(post("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackName").value("New Track"));

    }

    @Test
    void updateTrack_Success() throws Exception {
        TrackEntity t = createTrackEntity("OldName", testUser, null);

        TrackRequest req = new TrackRequest()
                .trackName("UpdatedName")
                .trackLink("https://example.com/u.mp3");

        mockMvc.perform(put("/api/v1/tracks/{trackId}", t.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackName").value("UpdatedName"));
    }


    @Test
    void updateTrack_Forbidden_WhenNotOwner() throws Exception {
        UserEntity owner = createUser("owner3");
        TrackEntity t = createTrackEntity("O", owner, null);

        TrackRequest req = new TrackRequest()
                .trackName("Hack")
                .trackLink("https://example.com/x.mp3");

        mockMvc.perform(put("/api/v1/tracks/{trackId}", t.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }


    @Test
    void deleteTrack_Success() throws Exception {
        TrackEntity t = createTrackEntity("Del", testUser, null);

        mockMvc.perform(delete("/api/v1/tracks/{trackId}", t.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNoContent());

        assertFalse(trackRepository.existsById(t.getId()));
    }

    @Test
    void deleteTrack_NotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/tracks/{trackId}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTrack_Forbidden_WhenNotOwner() throws Exception {
        UserEntity owner = createUser("owner4");
        TrackEntity t = createTrackEntity("Del2", owner, null);

        mockMvc.perform(delete("/api/v1/tracks/{trackId}", t.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTrackWindow_Owner_Success() throws Exception {
        TrackEntity track = createTrackEntity("My Track", testUser, null);

        TrackWindowRequest req = new TrackWindowRequest()
                .name("Intro")
                .positionFrom(10)
                .positionTo(20)
                .fadeIn(true)
                .fadeOut(false);

        mockMvc.perform(post("/api/v1/tracks/{trackId}/windows", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(track.getId().intValue()))
                .andExpect(jsonPath("$.trackWindows").isArray())
                .andExpect(jsonPath("$.trackWindows", hasSize(1)))
                .andExpect(jsonPath("$.trackWindows[0].name").value("Intro"))
                .andExpect(jsonPath("$.trackWindows[0].positionFrom").value(10))
                .andExpect(jsonPath("$.trackWindows[0].positionTo").value(20))
                .andExpect(jsonPath("$.trackWindows[0].fadeIn").value(true))
                .andExpect(jsonPath("$.trackWindows[0].fadeOut").value(false));
    }

    @Test
    void createTrackWindow_NonOwner_Forbidden() throws Exception {
        UserEntity other = createUser("otherUser");
        TrackEntity track = createTrackEntity("Other Track", other, null);

        TrackWindowRequest req = new TrackWindowRequest()
                .name("Nope")
                .positionFrom(10)
                .positionTo(20)
                .fadeIn(false)
                .fadeOut(false);

        mockMvc.perform(post("/api/v1/tracks/{trackId}/windows", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
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
                .fadeIn(false)
                .fadeOut(false);

        mockMvc.perform(post("/api/v1/tracks/{trackId}/windows", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserTracks_TrackWindowsAreOrderedAscending() throws Exception {
        TrackEntity track = createTrackEntity("Ordered Track", testUser, null);

        createTrackWindow(track, "B", 50L, 100L, false, false);
        createTrackWindow(track, "A", 10L, 100L, false, false);
        createTrackWindow(track, "C", 90L, 100L, false, false);

        mockMvc.perform(get("/api/v1/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trackWindows[*].name", contains("A", "B", "C")));
    }

    @Test
    void updateTrackWindow_Owner_Success() throws Exception {
        TrackEntity track = createTrackEntity("My Track", testUser, null);
        TrackWindowEntity point = createTrackWindow(track, "Intro", 10L, 20L, true, false);

        TrackWindowRequest update = new TrackWindowRequest()
                .name("Intro Updated")
                .positionFrom(20)
                .positionTo(45)
                .fadeIn(false)
                .fadeOut(true);

        mockMvc.perform(patch("/api/v1/tracks/{trackId}/windows/{windowId}", track.getId(), point.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackWindows[*].id", hasItem(point.getId().intValue())))
                .andExpect(jsonPath("$.trackWindows[0].name").value("Intro Updated"))
                .andExpect(jsonPath("$.trackWindows[0].positionFrom").value(20))
                .andExpect(jsonPath("$.trackWindows[0].positionTo").value(45))
                .andExpect(jsonPath("$.trackWindows[0].fadeIn").value(false))
                .andExpect(jsonPath("$.trackWindows[0].fadeOut").value(true));
    }

    @Test
    void deleteTrackWindow_Owner_Success() throws Exception {
        TrackEntity track = createTrackEntity("My Track", testUser, null);
        TrackWindowEntity window = createTrackWindow(track, "Intro", 10L, 20L, true, false);

        mockMvc.perform(delete("/api/v1/tracks/{trackId}/windows/{windowId}", track.getId(), window.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackWindows").isArray())
                .andExpect(jsonPath("$.trackWindows", is(empty())));
    }


    private UserEntity createUser(String name) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setPassword("password");
        return userRepository.save(u);
    }

    private GroupEntity createGroup(String name, UserEntity owner) {
        GroupEntity g = new GroupEntity();
        g.setListName(name);
        g.setOwner(owner);
        return groupRepository.save(g);
    }

    private TrackEntity createTrackEntity(String name, UserEntity owner, GroupEntity group) {
        TrackEntity t = new TrackEntity();
        t.setTrackName(name);
        t.setTrackLink("https://example.com/" + name + ".mp3");
        t.setDuration(120);
        t.setOwner(owner);
        TrackEntity saved = trackRepository.save(t);

        if (group != null) {
            groupRepository.addTrackToGroup(group.getId(), saved.getId());
        }
        return saved;
    }

    private TrackWindowEntity createTrackWindow(TrackEntity track, String name, Long positionFrom, Long positionTo, boolean fadeIn, boolean fadeOut) {
        TrackWindowEntity p = new TrackWindowEntity();
        p.setTrack(track);
        p.setName(name);
        p.setPositionFrom(positionFrom);
        p.setPositionTo(positionTo);
        p.setFadeIn(fadeIn);
        p.setFadeOut(fadeOut);
        return trackWindowRepository.save(p);
    }


    private String getTokenForUser(UserEntity user) {
        UserAuthDTO dto = new UserAuthDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        return jwtService.generateToken(dto);
    }
}

