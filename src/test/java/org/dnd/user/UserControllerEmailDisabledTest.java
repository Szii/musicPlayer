package org.dnd.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.DatabaseBase;
import org.dnd.TestHelpers;
import org.dnd.api.model.UserChangePasswordWithTokenRequest;
import org.dnd.api.model.UserLoginRequest;
import org.dnd.api.model.UserRegisterRequest;
import org.dnd.email.EmailService;
import org.dnd.keycloak.KeycloakAdminClient;
import org.dnd.keycloak.KeycloakAuthClient;
import org.dnd.security.JwtService;
import org.dnd.token.TokenRepository;
import org.dnd.token.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.email-use=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerEmailDisabledTest extends DatabaseBase {

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

  @Autowired
  private PasswordEncoder passwordEncoder;

  @MockitoBean
  private EmailService emailService;

  @MockitoBean
  private KeycloakAdminClient keycloakAdminClient;

  @MockitoBean
  private KeycloakAuthClient keycloakAuthClient;

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
    userRepository.deleteAll();

    when(keycloakAdminClient.createUser(
            anyString(),
            anyString(),
            anyString(),
            anyBoolean()
    )).thenAnswer(invocation -> UUID.randomUUID());
  }

  @Test
  void verifyUserToken_whenEmailDisabled_returnsMethodNotAllowed() throws Exception {
    mockMvc.perform(post("/api/v1/auth/verify/{verificationToken}", "some-token"))
            .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(emailService);
  }

  @Test
  void changeUnverifiedEmail_whenEmailDisabled_returnsMethodNotAllowed() throws Exception {
    UserRegisterRequest request = new UserRegisterRequest()
            .name("testUser")
            .email("new@email.com")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/verify/change-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(emailService);
  }

  @Test
  void changeUnverifiedPassword_whenEmailDisabled_returnsMethodNotAllowed() throws Exception {
    UserChangePasswordWithTokenRequest request = new UserChangePasswordWithTokenRequest();
    request.setToken("some-token");
    request.setPassword("newPassword123");

    mockMvc.perform(post("/api/v1/auth/verify/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(emailService);
  }

  @Test
  void changeVerifiedEmail_whenEmailDisabled_returnsMethodNotAllowed() throws Exception {
    String username = "testUser";
    String password = "password123";
    String oldEmail = "old@email.com";
    String newEmail = "new@email.com";

    UserEntity user = UserHelper.createValidatedUser(
            username,
            passwordEncoder.encode(password),
            oldEmail
    );

    UserEntity savedUser = userRepository.save(user);

    UserRegisterRequest request = new UserRegisterRequest()
            .name(username)
            .email(newEmail)
            .password(password);

    mockMvc.perform(post("/api/v1/users/change-email")
                    .with(TestHelpers.authenticatedAs(savedUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isMethodNotAllowed());

    UserEntity unchangedUser = userRepository.findById(savedUser.getId()).orElseThrow();

    assertEquals(oldEmail, unchangedUser.getEmail());
    assertNull(unchangedUser.getPendingEmail());

    verifyNoInteractions(emailService);
  }

  @Test
  void resendVerificationEmail_whenEmailDisabled_returnsMethodNotAllowed() throws Exception {
    UserLoginRequest request = new UserLoginRequest()
            .name("testUser")
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/verify/resend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(emailService);
  }

  @Test
  void registerUser_whenEmailDisabled_registersUserAsEmailVerified() throws Exception {
    String username = "testUser";
    String email = "user@email.cz";

    UserRegisterRequest request = new UserRegisterRequest()
            .name(username)
            .email(email)
            .password("password123");

    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(username))
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.user").doesNotExist());

    UserEntity user = userRepository.findByName(username).orElseThrow();

    assertTrue(userRepository.existsByEmail(email));
    assertTrue(user.isEmailVerified());
    assertNull(user.getPendingEmail());
    assertNotNull(user.getKeycloakId());

    assertTrue(tokenRepository
            .findByUserIdAndType(user.getId(), TokenType.REGISTRATION)
            .isEmpty());

    verifyNoInteractions(emailService);
  }
}