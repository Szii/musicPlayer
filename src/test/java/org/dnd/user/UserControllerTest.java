package org.dnd.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.TestHelpers;
import org.dnd.api.model.*;
import org.dnd.email.EmailService;
import org.dnd.exception.EmailDeliveryException;
import org.dnd.exception.ErrorCode;
import org.dnd.exception.UnauthorizedException;
import org.dnd.keycloak.KeycloakAdminClient;
import org.dnd.keycloak.KeycloakAuthClient;
import org.dnd.keycloak.KeycloakBruteForceStatus;
import org.dnd.keycloak.KeycloakTokenResponse;
import org.dnd.token.TokenEntity;
import org.dnd.token.TokenRepository;
import org.dnd.token.TokenType;
import org.dnd.user.rank.UserRankLimitProvider;
import org.dnd.user.rank.UserRankLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class UserControllerTest extends DatabaseBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TokenRepository tokenRepository;

  @MockitoBean
  private EmailService emailService;

  @MockitoBean
  private KeycloakAdminClient keycloakAdminClient;

  @MockitoBean
  private KeycloakAuthClient keycloakAuthClient;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private final Map<String, String> keycloakPasswordsByUsername = new HashMap<>();
  private final Map<UUID, String> keycloakUsernamesById = new HashMap<>();

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
    userRepository.deleteAll();

    keycloakPasswordsByUsername.clear();
    keycloakUsernamesById.clear();

    when(keycloakAdminClient.createUser(
            anyString(),
            anyString(),
            anyString(),
            anyBoolean()
    )).thenAnswer(invocation -> {
      String username = invocation.getArgument(0);
      String password = invocation.getArgument(2);

      UUID keycloakId = UUID.randomUUID();

      keycloakUsernamesById.put(keycloakId, username);
      keycloakPasswordsByUsername.put(username, password);

      return keycloakId;
    });

    doAnswer(invocation -> {
      UUID keycloakId = invocation.getArgument(0);
      String newPassword = invocation.getArgument(1);

      String username = keycloakUsernamesById.get(keycloakId);

      if (username != null) {
        keycloakPasswordsByUsername.put(username, newPassword);
      }

      return null;
    }).when(keycloakAdminClient).updatePassword(any(UUID.class), anyString());

    when(keycloakAuthClient.login(anyString(), anyString()))
            .thenAnswer(invocation -> {
              String username = invocation.getArgument(0);
              String rawPassword = invocation.getArgument(1);

              UserEntity user = userRepository.findByName(username)
                      .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

              String storedPassword = keycloakPasswordsByUsername.get(username);

              if (storedPassword == null || !storedPassword.equals(rawPassword)) {
                throw new UnauthorizedException("Invalid username or password");
              }

              return new KeycloakTokenResponse(
                      TestHelpers.getKeycloakAccessTokenForUser(user),
                      300,
                      1800,
                      "test-refresh-token",
                      "Bearer",
                      0,
                      UUID.randomUUID().toString(),
                      "profile email"
              );
            });
  }

  private UserEntity givenKeycloakUser(UserEntity user, String rawPassword) {
    UUID keycloakId = TestHelpers.withKeycloakId(user).getKeycloakId();

    keycloakUsernamesById.put(keycloakId, user.getName());
    keycloakPasswordsByUsername.put(user.getName(), rawPassword);

    return userRepository.save(user);
  }

  private String captureVerificationToken() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendVerificationEmail(anyString(), anyString(), captor.capture());
    return captor.getValue();
  }

  private String captureEmailChangeToken() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendEmailChangeMail(anyString(), anyString(), captor.capture());
    return captor.getValue();
  }

  private String capturePasswordResetToken() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendPasswordReset(anyString(), captor.capture());
    return captor.getValue();
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
    assertNotNull(user.getKeycloakId());

    TokenEntity token = tokenRepository
            .findByUserIdAndType(user.getId(), TokenType.REGISTRATION)
            .orElseThrow();

    assertNotNull(token.getTokenHash());
    assertNotNull(token.getExpiresAt());
    assertEquals(TokenType.REGISTRATION, token.getType());
    assertEquals(request.getEmail().toLowerCase(), token.getTargetEmail());

    verify(emailService).sendVerificationEmail(
            eq("testUser"),
            eq("user@email.cz"),
            anyString()
    );

    assertNotEquals(captureVerificationToken(), token.getTokenHash());
  }

  @Test
  void registerUser_EmailDeliveryFails_RollsBackUserAndKeycloak() throws Exception {
    UUID keycloakId = UUID.randomUUID();

    when(keycloakAdminClient.createUser(
            anyString(),
            anyString(),
            anyString(),
            anyBoolean()
    )).thenReturn(keycloakId);

    doThrow(new EmailDeliveryException("smtp is down", new RuntimeException()))
            .when(emailService)
            .sendVerificationEmail(anyString(), anyString(), anyString());

    UserRegisterRequest request = new UserRegisterRequest()
            .name("testUser")
            .email("user@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_DELIVERY_FAILED.getCode()));

    assertTrue(userRepository.findByName("testUser").isEmpty());
    assertFalse(userRepository.existsByEmail("user@email.cz"));

    verify(keycloakAdminClient).deleteUser(keycloakId);
  }

  @Test
  void registerUser_duplicateUsernameOnUnverifiedAccount_replacesTheClaim() throws Exception {
    UserRegisterRequest request = new UserRegisterRequest()
            .name("testUser")
            .email("typo@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

    UUID abandonedKeycloakId = userRepository.findByName("testUser").orElseThrow().getKeycloakId();

    UserRegisterRequest retry = new UserRegisterRequest()
            .name("testUser")
            .email("correct@email.cz")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(retry)))
            .andExpect(status().isCreated());

    UserEntity user = userRepository.findByName("testUser").orElseThrow();

    assertEquals("correct@email.cz", user.getEmail());
    assertNotEquals(abandonedKeycloakId, user.getKeycloakId());
    assertFalse(userRepository.existsByEmail("typo@email.cz"));
    verify(keycloakAdminClient).deleteUser(abandonedKeycloakId);
  }

  @Test
  void registerUser_duplicateUsernameOnVerifiedAccount_returnsConflict() throws Exception {
    givenKeycloakUser(
            UserHelper.createValidatedUser("testUser", null, "owner@email.cz"),
            "password123"
    );

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
  void registerUser_duplicateEmailOnVerifiedAccount_returnsConflict() throws Exception {
    givenKeycloakUser(
            UserHelper.createValidatedUser("owner", null, "user@email.cz"),
            "password123"
    );

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
            .email(email)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value(username))
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.token").exists());

    UserEntity migratedUser = userRepository.findByName(username).orElseThrow();
    assertNotNull(migratedUser.getKeycloakId());
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
            .email(email)
            .password(password);

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_NOT_VERIFIED.getCode()));
  }

  @Test
  void loginUser_lockedOutByKeycloak_returnsTooManyRequestsWithRetryAfter() throws Exception {
    String username = "testUser";
    String email = "email@email.com";

    UserEntity user = UserHelper.createValidatedUser(username, null, email);
    givenKeycloakUser(user, "password123");

    when(keycloakAdminClient.getBruteForceStatus(user.getKeycloakId()))
            .thenReturn(new KeycloakBruteForceStatus(
                    true,
                    Instant.now().getEpochSecond() + 90
            ));

    UserLoginRequest loginRequest = new UserLoginRequest()
            .email(email)
            .password("wrongPassword");

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_ATTEMPTS.getCode()));
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
            .email(email)
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
            .email("user@email.cz")
            .password("wrongPassword");

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
  }

  @Test
  void changeUnverifiedEmail_endpointNoLongerExists() throws Exception {
    UserRegisterRequest request = new UserRegisterRequest()
            .name("testUser")
            .email("typo@email.com")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/verify/change-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is4xxClientError());

    verifyNoInteractions(emailService);
  }

  @Test
  void changeEmail_verifiedSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String oldEmail = "email@email.com";
    String newEmail = "new-email@email.com";

    UserEntity user = givenKeycloakUser(
            UserHelper.createValidatedUser(username, null, oldEmail),
            password
    );

    ChangeEmailRequest request = new ChangeEmailRequest()
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/users/change-email")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    verify(emailService).sendEmailChangeMail(
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

    ChangeEmailRequest request = new ChangeEmailRequest()
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/users/change-email")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer: invalidToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());

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

    String verificationToken = captureVerificationToken();

    assertFalse(registeredUser.isEmailVerified());
    assertEquals(normalizedEmail, registeredUser.getEmail());
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
            .email(email)
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

    UserEntity user = givenKeycloakUser(
            UserHelper.createValidatedUser(username, null, oldEmail),
            password
    );

    ChangeEmailRequest request = new ChangeEmailRequest()
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/users/change-email")
                    .with(TestHelpers.authenticatedAs(user))
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

    assertEquals(TokenType.EMAIL_CHANGE, tokenEntity.getType());
    assertEquals(newEmail, tokenEntity.getTargetEmail());

    mockMvc.perform(post("/api/v1/auth/verify/{verificationToken}", captureEmailChangeToken()))
            .andExpect(status().isOk());

    UserEntity afterVerification = userRepository.findByName(username).orElseThrow();

    assertTrue(tokenRepository
            .findByUserIdAndType(afterVerification.getId(), TokenType.EMAIL_CHANGE).isEmpty());

    assertTrue(afterVerification.isEmailVerified());
    assertEquals(newEmail, afterVerification.getEmail());
    assertNull(afterVerification.getPendingEmail());
  }

  @Test
  void changePassword_unverifiedFlowSuccess() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";
    String newPassword = "password12345";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            email
    );

    UserEntity managedUser = userRepository.save(user);

    ForgotPasswordRequest request = new ForgotPasswordRequest()
            .email(email);

    mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    Optional<TokenEntity> passwordResetToken = tokenRepository.findByUserIdAndType(managedUser.getId(), TokenType.FORGOT_PASSWORD);

    assertTrue(passwordResetToken.isPresent());

    String token = capturePasswordResetToken();

    UserChangePasswordWithTokenRequest changePasswordRequest = new UserChangePasswordWithTokenRequest();
    changePasswordRequest.setPassword(newPassword);
    changePasswordRequest.setToken(token);

    mockMvc.perform(post("/api/v1/auth/verify/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(changePasswordRequest)))
            .andExpect(status().isOk());

    UserEntity afterPasswordChange = userRepository.findByName(username).orElseThrow();

    assertTrue(afterPasswordChange.isEmailVerified());
    assertEquals(newPassword, keycloakPasswordsByUsername.get(username));
    assertNull(afterPasswordChange.getPassword());
    assertTrue(tokenRepository
            .findByUserIdAndType(afterPasswordChange.getId(), TokenType.FORGOT_PASSWORD).isEmpty());
  }

  @Test
  void changePassword_expiredResetToken_isRejected() throws Exception {
    String username = "testUser";
    String email = "email@email.com";

    UserEntity user = givenKeycloakUser(
            UserHelper.createValidatedUser(username, null, email),
            "password123"
    );

    mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ForgotPasswordRequest().email(email))))
            .andExpect(status().isOk());

    String token = capturePasswordResetToken();

    TokenEntity resetToken = tokenRepository
            .findByUserIdAndType(user.getId(), TokenType.FORGOT_PASSWORD)
            .orElseThrow();

    resetToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    tokenRepository.saveAndFlush(resetToken);

    UserChangePasswordWithTokenRequest changePasswordRequest = new UserChangePasswordWithTokenRequest();
    changePasswordRequest.setPassword("password12345");
    changePasswordRequest.setToken(token);

    mockMvc.perform(post("/api/v1/auth/verify/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(changePasswordRequest)))
            .andExpect(status().isForbidden());

    assertEquals("password123", keycloakPasswordsByUsername.get(username));
    verify(keycloakAdminClient, never()).updatePassword(any(UUID.class), anyString());
  }

  @Test
  void changePasswordByToken_cancelsAPendingEmailChange() throws Exception {
    String username = "victim";
    String ownEmail = "victim@email.com";
    String attackerEmail = "attacker@evil.com";

    UserEntity user = givenKeycloakUser(
            UserHelper.createValidatedUser(username, null, ownEmail),
            "password123"
    );

    mockMvc.perform(post("/api/v1/users/change-email")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ChangeEmailRequest()
                            .email(attackerEmail)
                            .password("password123"))))
            .andExpect(status().isOk());

    String emailChangeToken = captureEmailChangeToken();

    assertEquals(attackerEmail, userRepository.findByName(username).orElseThrow().getPendingEmail());

    mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ForgotPasswordRequest().email(ownEmail))))
            .andExpect(status().isOk());

    UserChangePasswordWithTokenRequest reset = new UserChangePasswordWithTokenRequest();
    reset.setPassword("brandNewPassword1");
    reset.setToken(capturePasswordResetToken());

    mockMvc.perform(post("/api/v1/auth/verify/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reset)))
            .andExpect(status().isOk());

    UserEntity afterReset = userRepository.findByName(username).orElseThrow();

    assertNull(afterReset.getPendingEmail());
    assertTrue(tokenRepository
            .findByUserIdAndType(afterReset.getId(), TokenType.EMAIL_CHANGE).isEmpty());

    mockMvc.perform(post("/api/v1/auth/verify/{verificationToken}", emailChangeToken))
            .andExpect(status().isForbidden());

    assertEquals(ownEmail, userRepository.findByName(username).orElseThrow().getEmail());
  }

  @Test
  void changePasswordByAuth_cancelsAPendingEmailChange() throws Exception {
    String username = "victim";
    String ownEmail = "victim@email.com";

    UserEntity user = givenKeycloakUser(
            UserHelper.createValidatedUser(username, null, ownEmail),
            "password123"
    );

    mockMvc.perform(post("/api/v1/users/change-email")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ChangeEmailRequest()
                            .email("attacker@evil.com")
                            .password("password123"))))
            .andExpect(status().isOk());

    String emailChangeToken = captureEmailChangeToken();

    mockMvc.perform(post("/api/v1/verify/change-password")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new UserChangePasswordRequest("password123", "brandNewPassword1"))))
            .andExpect(status().isOk());

    UserEntity afterChange = userRepository.findByName(username).orElseThrow();

    assertNull(afterChange.getPendingEmail());

    mockMvc.perform(post("/api/v1/auth/verify/{verificationToken}", emailChangeToken))
            .andExpect(status().isForbidden());

    assertEquals(ownEmail, userRepository.findByName(username).orElseThrow().getEmail());
  }

  private UserEntity givenGoogleBrokeredUser(String username, String email) {
    UserEntity user = givenKeycloakUser(UserHelper.createValidatedUser(username, null, email), null);

    when(keycloakAdminClient.hasFederatedIdentity(user.getKeycloakId())).thenReturn(true);

    return user;
  }

  @Test
  void getCurrentUser_reportsWhetherTheAccountIsGoogleManaged() throws Exception {
    UserEntity googleUser = givenGoogleBrokeredUser("googler", "googler@gmail.com");

    mockMvc.perform(get("/api/v1/users/me").with(TestHelpers.authenticatedAs(googleUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.managedByGoogle").value(true));

    UserEntity passwordUser = givenKeycloakUser(
            UserHelper.createValidatedUser("localuser", null, "local@email.com"), "password123");

    mockMvc.perform(get("/api/v1/users/me").with(TestHelpers.authenticatedAs(passwordUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.managedByGoogle").value(false));
  }

  @Test
  void changePasswordByAuth_googleUser_isRefused() throws Exception {
    UserEntity user = givenGoogleBrokeredUser("googler", "googler@gmail.com");

    mockMvc.perform(post("/api/v1/verify/change-password")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new UserChangePasswordRequest("whatever", "newPassword1"))))
            .andExpect(status().isForbidden());

    verify(keycloakAdminClient, never()).updatePassword(any(UUID.class), anyString());
  }

  @Test
  void changeEmailByAuth_googleUser_isRefused() throws Exception {
    UserEntity user = givenGoogleBrokeredUser("googler", "googler@gmail.com");

    mockMvc.perform(post("/api/v1/users/change-email")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ChangeEmailRequest()
                            .email("elsewhere@email.com")
                            .password("whatever"))))
            .andExpect(status().isForbidden());

    verifyNoInteractions(emailService);
  }

  @Test
  void forgotPassword_googleUser_sendsNothingButStillAnswers200() throws Exception {
    UserEntity user = givenGoogleBrokeredUser("googler", "googler@gmail.com");

    mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new ForgotPasswordRequest().email("googler@gmail.com"))))
            .andExpect(status().isOk());

    verifyNoInteractions(emailService);
    assertTrue(tokenRepository
            .findByUserIdAndType(user.getId(), TokenType.FORGOT_PASSWORD).isEmpty());
  }

  @Test
  void changeUsername_success() throws Exception {
    UserEntity user = givenGoogleBrokeredUser("alice1", "alice@gmail.com");

    mockMvc.perform(patch("/api/v1/users/me/username")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ChangeUsernameRequest().name("alice"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("alice"));

    assertEquals("alice", userRepository.findById(user.getId()).orElseThrow().getName());
    verify(keycloakAdminClient).updateUsername(user.getKeycloakId(), "alice");
  }

  @Test
  void changeUsername_emailShapedName_isRejected() throws Exception {
    UserEntity victim = givenKeycloakUser(
            UserHelper.createValidatedUser("victim", null, "victim@email.com"), "password123");
    UserEntity attacker = givenGoogleBrokeredUser("alice1", "alice@gmail.com");

    mockMvc.perform(patch("/api/v1/users/me/username")
                    .with(TestHelpers.authenticatedAs(attacker))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new ChangeUsernameRequest().name("victim@email.com"))))
            .andExpect(status().isBadRequest());

    assertEquals("alice1", userRepository.findById(attacker.getId()).orElseThrow().getName());
    assertEquals("victim", userRepository.findById(victim.getId()).orElseThrow().getName());
    verify(keycloakAdminClient, never()).updateUsername(any(UUID.class), anyString());
  }

  @Test
  void changeUsername_invalidShapes_areRejectedBeforeReachingKeycloak() throws Exception {
    UserEntity user = givenGoogleBrokeredUser("alice1", "alice@gmail.com");

    for (String invalid : new String[]{"ab", "with space", "has@sign", "trailing-"}) {
      mockMvc.perform(patch("/api/v1/users/me/username")
                      .with(TestHelpers.authenticatedAs(user))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(new ChangeUsernameRequest().name(invalid))))
              .andExpect(status().isBadRequest());
    }

    verify(keycloakAdminClient, never()).updateUsername(any(UUID.class), anyString());
  }

  @Test
  void changeUsername_conflictsCaseInsensitively() throws Exception {
    givenKeycloakUser(UserHelper.createValidatedUser("Taken", null, "taken@email.com"), "password123");
    UserEntity user = givenGoogleBrokeredUser("alice1", "alice@gmail.com");

    mockMvc.perform(patch("/api/v1/users/me/username")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ChangeUsernameRequest().name("taken"))))
            .andExpect(status().isConflict());

    verify(keycloakAdminClient, never()).updateUsername(any(UUID.class), anyString());
  }

  @Test
  void registerUser_emailShapedUsername_isRejected() throws Exception {
    UserRegisterRequest request = new UserRegisterRequest()
            .name("victim@email.com")
            .email("attacker@email.com")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

    assertEquals(0, userRepository.count());
    verifyNoInteractions(keycloakAdminClient);
  }

  @Test
  void changeUsername_alreadyTaken_returnsConflict() throws Exception {
    givenKeycloakUser(UserHelper.createValidatedUser("taken", null, "taken@email.com"), "password123");
    UserEntity user = givenGoogleBrokeredUser("alice1", "alice@gmail.com");

    mockMvc.perform(patch("/api/v1/users/me/username")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ChangeUsernameRequest().name("taken"))))
            .andExpect(status().isConflict());

    verify(keycloakAdminClient, never()).updateUsername(any(UUID.class), anyString());
  }

  @Test
  void changePassword_revokesExistingSessions() throws Exception {
    UserEntity user = givenKeycloakUser(
            UserHelper.createValidatedUser("franta", null, "email@email.com"),
            "lala"
    );

    UserChangePasswordRequest request = new UserChangePasswordRequest("lala", "lalaNewPass1");

    mockMvc.perform(post("/api/v1/verify/change-password")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    verify(keycloakAdminClient).logoutUser(user.getKeycloakId());
  }

  @Test
  void changeEmail_verifiedUser_notifiesThePreviousAddress() throws Exception {
    String username = "testUser";
    String oldEmail = "old@email.com";
    String newEmail = "new@email.com";

    UserEntity user = givenKeycloakUser(
            UserHelper.createValidatedUser(username, null, oldEmail),
            "password123"
    );

    ChangeEmailRequest request = new ChangeEmailRequest()
            .email(newEmail)
            .password("password123");

    mockMvc.perform(post("/api/v1/users/change-email")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    verify(emailService).sendEmailChangeMail(eq(username), eq(newEmail), anyString());
    verify(emailService).sendEmailChangeNotice(eq(username), eq(oldEmail), eq(newEmail));
  }

  @Test
  void changePassword_unverifiedFlowFails() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";
    String newPassword = "password12345";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            email
    );

    UserEntity managedUser = userRepository.save(user);

    ForgotPasswordRequest request = new ForgotPasswordRequest()
            .email(email);

    mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    Optional<TokenEntity> passwordResetToken = tokenRepository.findByUserIdAndType(managedUser.getId(), TokenType.FORGOT_PASSWORD);

    assertTrue(passwordResetToken.isPresent());

    verify(emailService).sendPasswordReset(eq(request.getEmail()), anyString());

    UserChangePasswordWithTokenRequest changePasswordRequest = new UserChangePasswordWithTokenRequest();
    changePasswordRequest.setPassword(newPassword);
    changePasswordRequest.setToken("random-token");

    mockMvc.perform(post("/api/v1/auth/verify/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(changePasswordRequest)))
            .andExpect(status().isForbidden());
  }

  @Test
  void changePassword_verifiedUser_success() throws Exception {
    UserEntity user = UserHelper.createValidatedUser("franta", null, "email@email.com");
    UserEntity managedUser = givenKeycloakUser(user, "lala");

    UserChangePasswordRequest request = new UserChangePasswordRequest("lala", "lala1");

    mockMvc.perform(post("/api/v1/verify/change-password")
                    .with(TestHelpers.authenticatedAs(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

    UserEntity modifiedUser = userRepository.findById(managedUser.getId()).orElseThrow();

    assertEquals("lala1", keycloakPasswordsByUsername.get("franta"));
    assertNull(modifiedUser.getPassword());
  }

  @Test
  void getCurrentUser_Success() throws Exception {
    String username = "testUser";
    String password = "password123";
    String email = "email@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            null,
            email
    );

    givenKeycloakUser(user, password);

    UserLoginRequest loginRequest = new UserLoginRequest()
            .email(email)
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

    UserRankLimits limits = new UserRankLimitProvider().getLimits(UserRank.NORMAL);

    mockMvc.perform(get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(username))
            .andExpect(jsonPath("$.limits.level").value("NORMAL"))

            .andExpect(jsonPath("$.limits.tracks.actualTracks").value(0))
            .andExpect(jsonPath("$.limits.tracks.maxTracks").value(limits.maxTracks()))
            .andExpect(jsonPath("$.limits.tracks.trackLimitReached").value(false))

            .andExpect(jsonPath("$.limits.boards").isArray())
            .andExpect(jsonPath("$.limits.boards").isEmpty())

            .andExpect(jsonPath("$.limits.groups.actualGroups").value(0))
            .andExpect(jsonPath("$.limits.groups.maxGroups").value(limits.maxGroups()))
            .andExpect(jsonPath("$.limits.groups.groupLimitReached").value(false))

            .andExpect(jsonPath("$.limits.sessions.actualSessions").value(0))
            .andExpect(jsonPath("$.limits.sessions.maxSessions").value(limits.maxSessions()))
            .andExpect(jsonPath("$.limits.sessions.sessionLimitReached").value(false))

            .andExpect(jsonPath("$.limits.subscribes.actualSubscribes").value(0))
            .andExpect(jsonPath("$.limits.subscribes.maxSubscribes").value(limits.maxShares()))
            .andExpect(jsonPath("$.limits.subscribes.subscribeLimitReached").value(false))

            .andExpect(jsonPath("$.limits.windows").isArray())
            .andExpect(jsonPath("$.limits.windows").isEmpty());
  }
}