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
import org.dnd.token.TokenEntity;
import org.dnd.token.TokenRepository;
import org.dnd.token.TokenType;
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
  private TokenRepository tokenRepository;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private EmailService emailService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void registerUser_Success() throws Exception {
    UserRegisterRequest request = new UserRegisterRequest()
            .name("testUser")
            .email("user@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("testUser"))
            .andExpect(jsonPath("$.email").value("user@email.cz"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.user").doesNotExist());

    UserEntity user = userRepository.findByName(request.getName()).orElseThrow();

    assertTrue(userRepository.existsByEmail(request.getEmail()));
    assertFalse(user.isEmailVerified());
    assertNull(user.getPendingEmail());

    TokenEntity token = tokenRepository
            .findByUserIdAndType(user.getId(), TokenType.REGISTRATION)
            .orElseThrow();

    assertTrue(token.isValid());
    assertNotNull(token.getToken());
    assertEquals(TokenType.REGISTRATION, token.getType());
    assertEquals(request.getEmail().toLowerCase(), token.getTargetEmail());

    verify(emailService).sendVerificationEmail(
            eq("testUser"),
            eq("user@email.cz"),
            anyString()
    );
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

    UserRegisterRequest duplicateRequest = new UserRegisterRequest()
            .name("testUser")
            .email("other@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(duplicateRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ErrorCode.USER_ALREADY_EXISTS.getCode()));
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

    UserRegisterRequest duplicateRequest = new UserRegisterRequest()
            .name("anotherUser")
            .email("user@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(duplicateRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_ALREADY_EXISTS.getCode()));
  }

  @Test
  void loginUser_Success() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            email
    );

    userRepository.save(user);

    UserLoginRequest loginRequest = new UserLoginRequest()
            .name(username)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value(username))
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.token").exists());
  }

  @Test
  void loginUser_emailNotVerifiedWithCorrectPassword_returnsForbidden() throws Exception {
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
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").doesNotExist());

    UserLoginRequest loginRequest = new UserLoginRequest()
            .name(username)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_NOT_VERIFIED.getCode()));
  }

  @Test
  void loginUser_InvalidCredentials() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            email
    );

    userRepository.save(user);

    UserLoginRequest loginRequest = new UserLoginRequest()
            .name(username)
            .password("wrongPassword");

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
  }

  @Test
  void loginUser_NotVerifiedButWrongPasswordStillReturnsUnauthorized() throws Exception {
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
  void changeEmail_unverifiedSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String originalEmail = "email@email.com";
    String newEmail = "changedEmail@email.com";
    String normalizedNewEmail = newEmail.toLowerCase();

    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name(username)
            .email(originalEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

    UserRegisterRequest changeEmailRequest = new UserRegisterRequest()
            .name(username)
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/verify/change-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(changeEmailRequest)))
            .andExpect(status().isOk());

    verify(emailService).sendVerificationEmail(
            eq(username),
            eq(normalizedNewEmail),
            anyString()
    );

    UserEntity user = userRepository.findByName(username).orElseThrow();

    assertFalse(user.isEmailVerified());
    assertEquals(normalizedNewEmail, user.getEmail());
    assertNull(user.getPendingEmail());

    assertTrue(tokenRepository
            .findByUserIdAndType(user.getId(), TokenType.EMAIL_CHANGE).isEmpty());
  }

  @Test
  void changeEmail_verifiedSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String oldEmail = "email@email.com";
    String newEmail = "new-email@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            oldEmail
    );

    String token = getTokenForUser(userRepository.save(user));

    UserRegisterRequest request = new UserRegisterRequest()
            .name(username)
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/users/change-email")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    verify(emailService).sendVerificationEmail(
            eq(username),
            eq(newEmail),
            anyString()
    );

    UserEntity updatedUser = userRepository.findByName(username).orElseThrow();

    assertTrue(updatedUser.isEmailVerified());
    assertEquals(oldEmail, updatedUser.getEmail());
    assertEquals(newEmail, updatedUser.getPendingEmail());

    TokenEntity tokenEntity = tokenRepository
            .findByUserIdAndType(user.getId(), TokenType.EMAIL_CHANGE)
            .orElseThrow();

    assertTrue(tokenEntity.isValid());
    assertEquals(TokenType.EMAIL_CHANGE, tokenEntity.getType());
    assertEquals(newEmail, tokenEntity.getTargetEmail());
  }

  @Test
  void changeEmail_verifiedFailsWhenUserNotAuthenticated() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";
    String newEmail = "new-email@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            email
    );

    userRepository.save(user);

    UserRegisterRequest request = new UserRegisterRequest()
            .name(username)
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/users/change-email")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalidToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());

    UserEntity unchangedUser = userRepository.findByName(username).orElseThrow();

    assertTrue(unchangedUser.isEmailVerified());
    assertEquals(email, unchangedUser.getEmail());
    assertNull(unchangedUser.getPendingEmail());
    assertTrue(tokenRepository.findByUserIdAndType(unchangedUser.getId(), TokenType.EMAIL_CHANGE).isEmpty());
  }

  @Test
  void verifyEmail_registerToLoginFlowSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "emMAIl@email.com";
    String normalizedEmail = email.toLowerCase();

    UserRegisterRequest registerRequest = new UserRegisterRequest()
            .name(username)
            .email(email)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").doesNotExist());

    UserEntity registeredUser = userRepository.findByName(username).orElseThrow();

    TokenEntity tokenEntity = tokenRepository
            .findByUserIdAndType(registeredUser.getId(), TokenType.REGISTRATION)
            .orElseThrow();

    String verificationToken = tokenEntity.getToken();

    assertFalse(registeredUser.isEmailVerified());
    assertEquals(normalizedEmail, registeredUser.getEmail());
    assertTrue(tokenEntity.isValid());
    assertEquals(TokenType.REGISTRATION, tokenEntity.getType());
    assertEquals(normalizedEmail, tokenEntity.getTargetEmail());

    mockMvc.perform(post("/api/v1/auth/verify/{verificationToken}", verificationToken))
            .andExpect(status().isOk());

    verify(emailService).sendVerificationEmail(
            eq(username),
            eq(normalizedEmail),
            anyString()
    );

    UserEntity verifiedUser = userRepository.findByName(username).orElseThrow();


    assertTrue(verifiedUser.isEmailVerified());
    assertTrue(tokenRepository
            .findByUserIdAndType(verifiedUser.getId(), TokenType.REGISTRATION).isEmpty());

    UserLoginRequest loginRequest = new UserLoginRequest()
            .name(username)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value(username))
            .andExpect(jsonPath("$.user.email").value(normalizedEmail))
            .andExpect(jsonPath("$.token").exists());
  }

  @Test
  void verifyEmail_emailChangeFlowSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String oldEmail = "old@email.com";
    String newEmail = "new@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            oldEmail
    );

    String authToken = getTokenForUser(userRepository.save(user));

    UserRegisterRequest request = new UserRegisterRequest()
            .name(username)
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/users/change-email")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    UserEntity beforeVerification = userRepository.findByName(username).orElseThrow();

    assertTrue(beforeVerification.isEmailVerified());
    assertEquals(oldEmail, beforeVerification.getEmail());
    assertEquals(newEmail, beforeVerification.getPendingEmail());

    TokenEntity tokenEntity = tokenRepository
            .findByUserIdAndType(beforeVerification.getId(), TokenType.EMAIL_CHANGE)
            .orElseThrow();

    assertTrue(tokenEntity.isValid());
    assertEquals(TokenType.EMAIL_CHANGE, tokenEntity.getType());
    assertEquals(newEmail, tokenEntity.getTargetEmail());

    mockMvc.perform(post("/api/v1/auth/verify/{verificationToken}", tokenEntity.getToken()))
            .andExpect(status().isOk());

    UserEntity afterVerification = userRepository.findByName(username).orElseThrow();

    assertTrue(tokenRepository
            .findByUserIdAndType(afterVerification.getId(), TokenType.EMAIL_CHANGE).isEmpty());

    assertTrue(afterVerification.isEmailVerified());
    assertEquals(newEmail, afterVerification.getEmail());
    assertNull(afterVerification.getPendingEmail());
  }

  @Test
  void getCurrentUser_Success() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            email
    );

    userRepository.save(user);

    UserLoginRequest loginRequest = new UserLoginRequest()
            .name(username)
            .password(password);

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    AuthResponse authResponse = objectMapper.readValue(
            loginResult.getResponse().getContentAsString(),
            AuthResponse.class
    );

    mockMvc.perform(get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(username))
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