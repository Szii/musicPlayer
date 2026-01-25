package org.dnd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.api.model.PublishTrackRequest;
import org.dnd.api.model.SubscribeRequest;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackShareEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.DatabaseBase;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.TrackShareRepository;
import org.dnd.repository.UserRepository;
import org.dnd.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
    private JwtService jwtService;

    private UserEntity testUser;
    private UserEntity otherUser;
    private String authToken;
    private String otherUserToken;

    @BeforeEach
    void setUp() {
        trackShareRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        testUser = createUser("testUser");
        otherUser = createUser("otherUser");
        authToken = getTokenForUser(testUser);
        otherUserToken = getTokenForUser(otherUser);
    }

    @Test
    void publishTrack_Owner_Success() throws Exception {
        TrackEntity track = createTrackEntity("My Track", testUser);

        PublishTrackRequest request = new PublishTrackRequest();
        request.setDescription("Great track for studying");

        mockMvc.perform(post("/api/v1/share/tracks/{trackId}/publish", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.description").value("Great track for studying"))
                .andExpect(jsonPath("$.shareCode").exists())
                .andExpect(jsonPath("$.track.id").value(track.getId().intValue()));
    }

    @Test
    void publishTrack_NotOwner_Forbidden() throws Exception {
        TrackEntity track = createTrackEntity("Other Track", otherUser);

        System.out.println(track.getId());

        PublishTrackRequest request = new PublishTrackRequest();
        request.setDescription("Try to publish");

        mockMvc.perform(post("/api/v1/share/tracks/{trackId}/publish", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishTrack_TrackNotFound() throws Exception {
        PublishTrackRequest request = new PublishTrackRequest();
        request.setDescription("Non-existent track");

        mockMvc.perform(post("/api/v1/share/tracks/{trackId}/publish", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unpublishTrack_Owner_Success() throws Exception {
        TrackEntity track = createTrackEntity("My Track", testUser);
        createTrackShare(track, "Published track");

        mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNoContent());

        assertNull(trackRepository.findById(track.getId()).orElseThrow().getTrackShare());
    }

    @Test
    void unpublishTrack_NotOwner_Forbidden() throws Exception {
        TrackEntity track = createTrackEntity("Other Track", otherUser);
        createTrackShare(track, "Published track");

        mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unpublishTrack_NotPublished_Conflict() throws Exception {
        TrackEntity track = createTrackEntity("My Track", testUser);

        mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unpublishTrack_TrackNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/publish", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void subscribeToTrack_Success() throws Exception {
        TrackEntity track = createTrackEntity("Shared Track", otherUser);
        TrackShareEntity share = createTrackShare(track, "Popular track");

        SubscribeRequest request = new SubscribeRequest();
        request.setShareCode(share.getShareCode());

        mockMvc.perform(post("/api/v1/share/subscribe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        UserEntity user = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(user.getSubscribedTracks().stream()
                .anyMatch(t -> t.getId().equals(track.getId())));
    }

    @Test
    void subscribeToTrack_InvalidShareCode() throws Exception {
        SubscribeRequest request = new SubscribeRequest();
        request.setShareCode("invalid-code-12345");

        mockMvc.perform(post("/api/v1/share/subscribe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsubscribeFromTrack_Success() throws Exception {
        TrackEntity track = createTrackEntity("Shared Track", otherUser);
        createTrackShare(track, "Popular track");

        UserEntity user = userRepository.findById(testUser.getId()).orElseThrow();
        user.getSubscribedTracks().add(track);
        userRepository.save(user);

        mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/unsubscribe", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNoContent());

        UserEntity updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertFalse(updatedUser.getSubscribedTracks().contains(track));
    }

    @Test
    void unsubscribeFromTrack_NotSubscribed() throws Exception {
        TrackEntity track = createTrackEntity("Shared Track", otherUser);

        mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/unsubscribe", track.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void unsubscribeFromTrack_TrackNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/share/tracks/{trackId}/unsubscribe", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    private UserEntity createUser(String name) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setPassword("password");
        return userRepository.save(u);
    }

    private TrackEntity createTrackEntity(String name, UserEntity owner) {
        TrackEntity t = new TrackEntity();
        t.setTrackName(name);
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

        trackRepository.save(track);
        return track.getTrackShare();
    }


    private String getTokenForUser(UserEntity user) {
        UserAuthDTO dto = new UserAuthDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        return jwtService.generateToken(dto);
    }
}

