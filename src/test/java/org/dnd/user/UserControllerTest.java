package org.dnd.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.api.model.AuthResponse;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.api.model.UserLoginRequest;
import org.dnd.api.model.UserRegisterRequest;
import org.dnd.email.EmailService;
import org.dnd.exception.ErrorCode;
import org.dnd.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest extends DatabaseBase {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private EmailService emailService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  @Test
  void registerUser_Success() throws Exception {
    UserRegisterRequest request = new UserRegisterRequest()
            .name("testUser")
            .email("user@email.cz")
            .password("password123");

    MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.name").value("testUser"))
            .andExpect(jsonPath("$.token").exists())
            .andReturn();

    Optional<UserEntity> user = userRepository.findByName(request.getName());

    assertTrue(user.isPresent());
    assertTrue(userRepository.existsByEmail(request.getEmail()));
    assertFalse(user.get().isEmailVerified());
  }

  @Test
  void registerUser_DuplicateUsername() throws Exception {
    UserRegisterRequest request = new UserRegisterRequest()
            .name("testUser")
            .email("user@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ErrorCode.USER_ALREADY_EXISTS.getCode()));
    ;
  }

  @Test
  void registerUser_DuplicateEmail() throws Exception {
    UserRegisterRequest request = new UserRegisterRequest()
            .name("testUser")
            .email("user@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

    request.setName("anotherUser");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_ALREADY_EXISTS.getCode()));
  }

  @Test
  void loginUser_Success() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";

    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name(username)
            .email(email)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

    userRepository.findByName(username).ifPresent(user -> {
      user.setEmailVerified(true);
      userRepository.save(user);
    });

    UserLoginRequest loginRequest = new UserLoginRequest()
            .name(username)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value("testUser"))
            .andExpect(jsonPath("$.token").exists());
  }

  @Test
  void loginUser_emailNotVerified() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";

    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name(username)
            .email(email)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

    UserLoginRequest loginRequest = new UserLoginRequest()
            .name(username)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized());
  }

  @Test
  void changeEmail_unverifiedSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";

    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name(username)
            .email(email)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

    registerRequest.setEmail("changedEmail@email.com");


    mockMvc.perform(post("/api/v1/auth/verify/change-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk());

    UserEntity user = userRepository.findByName(username).orElseThrow();

    assertFalse(user.isEmailVerified());
    assertEquals(registerRequest.getEmail().toLowerCase(), user.getEmail());
  }

  @Test
  void changeEmail_verifiedSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";
    String newEmail = "new-email@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            email
    );

    String token = getTokenForUser(userRepository.save(user));

    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name(username)
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/users/change-email")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk());

    UserEntity thatUser = userRepository.findByName(username).orElseThrow();

    assertFalse(thatUser.isEmailVerified());
    assertEquals(registerRequest.getEmail().toLowerCase(), thatUser.getEmail());
  }

  @Test
  void changeEmail_verifiedFailsWhenUserNotAuthenticated() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";

    UserEntity user = UserHelper.createValidatedUser(username, password, email);
    getTokenForUser(userRepository.save(user));


    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name(username)
            .email(email)
            .password(password);

    mockMvc.perform(post("/api/v1/user/change-email")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + "invalidToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isForbidden());

    UserEntity thatUser = userRepository.findByName(username).orElseThrow();

    assertTrue(thatUser.isEmailVerified());
    assertEquals(email, thatUser.getEmail());
  }

  @Test
  void verifyEmail_registerToLoginFlowSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "emMAIl@email.com";

    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name(username)
            .email(email)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

    UserEntity registeredUser = userRepository.findByName(username).orElseThrow();
    String token = registeredUser.getVerificationToken();

    assertFalse(registeredUser.isEmailVerified());
    assertEquals(registeredUser.getEmail(), email.toLowerCase());
    assertNotNull(token);

    mockMvc.perform(post("/api/v1/auth/verify/{verificationToken}", token))
            .andExpect(status().isOk());

    verify(emailService).sendVerificationEmail(
            eq(username),
            eq(email.toLowerCase()),
            anyString()
    );

    UserEntity verifiedUser = userRepository.findByName(username).orElseThrow();

    assertTrue(verifiedUser.isEmailVerified());
    assertNull(verifiedUser.getVerificationToken());

    UserLoginRequest loginRequest = new UserLoginRequest()
            .name(username)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value(username))
            .andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
            .andExpect(jsonPath("$.token").exists());
  }

  @Test
  void loginUser_InvalidCredentials() throws Exception {
    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name("testUser")
            .email("user@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());


    UserLoginRequest loginRequest = new UserLoginRequest()
            .name("testUser")
            .password("wrongPassword");

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
  }

  @Test
  void loginUser_NotVerifiedEmail() throws Exception {
    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name("testUser")
            .email("user@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());


    UserLoginRequest loginRequest = new UserLoginRequest()
            .name("testUser")
            .password("wrongPassword");

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
  }

  @Test
  void getCurrentUser_Success() throws Exception {
    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name("testUser")
            .email("email@email.com")
            .password("password123");

    MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    String response = registerResult.getResponse().getContentAsString();
    AuthResponse authResponse = objectMapper.readValue(response, AuthResponse.class);
    String token = authResponse.getToken();

    mockMvc.perform(get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("testUser"))
            .andExpect(jsonPath("$.limits.level").value("NORMAL"))

            .andExpect(jsonPath("$.limits.tracks.actualTracks").value(0))
            .andExpect(jsonPath("$.limits.tracks.maxTracks").value(10))
            .andExpect(jsonPath("$.limits.tracks.trackLimitReached").value(false))

            .andExpect(jsonPath("$.limits.boards.actualBoards").value(0))
            .andExpect(jsonPath("$.limits.boards.maxBoards").value(3))
            .andExpect(jsonPath("$.limits.boards.boardLimitReached").value(false))

            .andExpect(jsonPath("$.limits.groups.actualGroups").value(0))
            .andExpect(jsonPath("$.limits.groups.maxGroups").value(5))
            .andExpect(jsonPath("$.limits.groups.groupLimitReached").value(false))

            .andExpect(jsonPath("$.limits.sessions.actualSessions").value(0))
            .andExpect(jsonPath("$.limits.sessions.maxSessions").value(5))
            .andExpect(jsonPath("$.limits.sessions.sessionLimitReached").value(false))

            .andExpect(jsonPath("$.limits.subscribes.actualSubscribes").value(0))
            .andExpect(jsonPath("$.limits.subscribes.maxSubscribes").value(10))
            .andExpect(jsonPath("$.limits.subscribes.subscribeLimitReached").value(false))

            .andExpect(jsonPath("$.limits.windows").isArray())
            .andExpect(jsonPath("$.limits.windows").isEmpty());
  }

  private String getTokenForUser(UserEntity user) {
    UserAuthDTO dto = new UserAuthDTO();
    dto.setId(user.getId());
    dto.setName(user.getName());
    dto.setEmail(user.getEmail());
    return jwtService.generateToken(dto);
  }


}
