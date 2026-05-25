package org.dnd.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.api.model.SessionRequest;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.exception.ErrorCode;
import org.dnd.security.JwtService;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest extends DatabaseBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private SessionRepository sessionRepository;

  @Autowired
  private JwtService jwtService;

  private UserEntity testUser;
  private String authToken;

  @BeforeEach
  void setUp() {
    testUser = new UserEntity();
    testUser.setName("testUser");
    testUser.setPassword("password");
    testUser = userRepository.save(testUser);

    UserAuthDTO userAuth = new UserAuthDTO();
    userAuth.setId(testUser.getId());
    userAuth.setName(testUser.getName());

    authToken = jwtService.generateToken(userAuth);
  }

  @Test
  void getSessions_EmptyList() throws Exception {
    mockMvc.perform(get("/api/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isNoContent());
  }

  @Test
  void getSessions_Success() throws Exception {
    createSession("Session One", "First session");
    createSession("Session Two", "Second session");

    mockMvc.perform(get("/api/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions").isArray())
            .andExpect(jsonPath("$.sessions", hasSize(2)))
            .andExpect(jsonPath("$.sessions[0].sessionName").value("Session One"))
            .andExpect(jsonPath("$.sessions[0].sessionDescription").value("First session"))
            .andExpect(jsonPath("$.sessions[1].sessionName").value("Session Two"))
            .andExpect(jsonPath("$.sessions[1].sessionDescription").value("Second session"));
  }

  @Test
  void createSession_ReturnsForbidden_WhenLimitIsReached() throws Exception {

    for (int i = 0; i < UserRankLimits.normal().maxSessions(); i++) {
      createSession("Session One", "First session");
    }

    SessionRequest request = new SessionRequest()
            .sessionName("My Session")
            .sessionDescription("My session description");

    mockMvc.perform(post("/api/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.LIMIT_EXCEEDED.getCode()));
  }

  @Test
  void createSession_Success() throws Exception {
    SessionRequest request = new SessionRequest()
            .sessionName("My Session")
            .sessionDescription("My session description");

    mockMvc.perform(post("/api/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions").isArray())
            .andExpect(jsonPath("$.sessions", hasSize(1)))
            .andExpect(jsonPath("$.sessions[0].sessionName").value("My Session"))
            .andExpect(jsonPath("$.sessions[0].sessionDescription").value("My session description"));

    assertFalse(sessionRepository.findByOwner_Id(testUser.getId()).isEmpty());
  }

  @Test
  void updateSession_Success() throws Exception {
    SessionEntity session = createSession("Old Name", "Old description");

    SessionRequest request = new SessionRequest()
            .sessionId(session.getId())
            .sessionName("Updated Name")
            .sessionDescription("Updated description");

    mockMvc.perform(post("/api/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions").isArray())
            .andExpect(jsonPath("$.sessions", hasSize(1)))
            .andExpect(jsonPath("$.sessions[0].sessionId").value(session.getId()))
            .andExpect(jsonPath("$.sessions[0].sessionName").value("Updated Name"))
            .andExpect(jsonPath("$.sessions[0].sessionDescription").value("Updated description"));
  }

  @Test
  void deleteSession_Success() throws Exception {
    SessionEntity session = createSession("Session to Delete", "Will be deleted");

    mockMvc.perform(delete("/api/v1/sessions/{sessionId}", session.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions").isArray())
            .andExpect(jsonPath("$.sessions").isEmpty());

    assertTrue(sessionRepository.findById(session.getId()).isEmpty());
  }

  @Test
  void updateSession_NotFound() throws Exception {
    SessionRequest request = new SessionRequest()
            .sessionId(999L)
            .sessionName("Updated Name")
            .sessionDescription("Updated description");

    mockMvc.perform(post("/api/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
  }

  @Test
  void deleteSession_NotFound() throws Exception {
    mockMvc.perform(delete("/api/v1/sessions/{sessionId}", 999L)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isNotFound());
  }

  @Test
  void updateSession_WrongUser_NotFound() throws Exception {
    UserEntity otherUser = new UserEntity();
    otherUser.setName("otherUser");
    otherUser.setPassword("password");
    otherUser = userRepository.save(otherUser);

    SessionEntity otherUserSession = new SessionEntity();
    otherUserSession.setName("Other User Session");
    otherUserSession.setDescription("Should not be editable");
    otherUserSession.setOwner(otherUser);
    otherUserSession = sessionRepository.save(otherUserSession);

    SessionRequest request = new SessionRequest()
            .sessionId(otherUserSession.getId())
            .sessionName("Hacked Name")
            .sessionDescription("Hacked description");

    mockMvc.perform(post("/api/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
  }

  @Test
  void deleteSession_WrongUser_NotFound() throws Exception {
    UserEntity otherUser = new UserEntity();
    otherUser.setName("otherUser");
    otherUser.setPassword("password");
    otherUser = userRepository.save(otherUser);

    SessionEntity otherUserSession = new SessionEntity();
    otherUserSession.setName("Other User Session");
    otherUserSession.setDescription("Should not be deletable");
    otherUserSession.setOwner(otherUser);
    otherUserSession = sessionRepository.save(otherUserSession);

    mockMvc.perform(delete("/api/v1/sessions/{sessionId}", otherUserSession.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
            .andExpect(status().isNotFound());
  }

  @Test
  void getSessions_NoAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/sessions"))
            .andExpect(status().isUnauthorized());
  }

  @Test
  void getSessions_InvalidToken() throws Exception {
    mockMvc.perform(get("/api/v1/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here"))
            .andExpect(status().isForbidden());
  }

  private SessionEntity createSession(String name, String description) {
    SessionEntity session = new SessionEntity();
    session.setName(name);
    session.setDescription(description);
    session.setOwner(testUser);
    return sessionRepository.save(session);
  }
}
