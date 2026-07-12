package org.dnd.user;

import jakarta.servlet.http.Cookie;
import org.dnd.DatabaseBase;
import org.dnd.TestHelpers;
import org.dnd.keycloak.KeycloakAdminClient;
import org.dnd.keycloak.KeycloakAuthClient;
import org.dnd.keycloak.KeycloakTokenResponse;
import org.dnd.security.RefreshCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class GoogleAuthControllerTest extends DatabaseBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @MockitoBean
  private KeycloakAuthClient keycloakAuthClient;

  @MockitoBean
  private KeycloakAdminClient keycloakAdminClient;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  private KeycloakTokenResponse googleToken(UUID keycloakId, String email, String givenName) throws Exception {
    UserEntity claims = new UserEntity();
    claims.setKeycloakId(keycloakId);
    claims.setName(email);
    claims.setEmail(email);
    claims.setEmailVerified(true);

    return new KeycloakTokenResponse(
            TestHelpers.getKeycloakAccessTokenForUser(claims, givenName),
            300,
            1800,
            "google-refresh-token",
            "Bearer",
            0,
            UUID.randomUUID().toString(),
            "profile email"
    );
  }

  @Test
  void startGoogleLogin_redirectsToKeycloakWithIdpHintAndSetsStateCookie() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/google"))
            .andExpect(status().isFound())
            .andReturn();

    String location = result.getResponse().getHeader(HttpHeaders.LOCATION);

    assertNotNull(location);
    assertTrue(location.startsWith("http://localhost:8081/realms/music-player/protocol/openid-connect/auth"),
            "authorize URL must use the public issuer, not the internal URL: " + location);
    assertTrue(location.contains("kc_idp_hint=google"));
    assertTrue(location.contains("response_type=code"));
    assertTrue(location.contains("state="));
    assertTrue(location.contains("scope=openid%20profile%20email"));
    assertTrue(location.contains("redirect_uri=http://localhost:8080/api/v1/auth/google/callback"),
            "must match the client's registered redirect URI byte for byte: " + location);

    Cookie state = result.getResponse().getCookie(RefreshCookieService.OAUTH_STATE_COOKIE);

    assertNotNull(state);
    assertTrue(state.isHttpOnly());
    assertEquals("Lax", state.getAttribute("SameSite"));
  }

  @Test
  void callback_provisionsTheUserAndSetsTheSameRefreshCookieAsPasswordLogin() throws Exception {
    UUID keycloakId = UUID.randomUUID();

    when(keycloakAuthClient.exchangeAuthorizationCode(eq("the-code"), anyString()))
            .thenReturn(googleToken(keycloakId, "alice@gmail.com", "Alice"));
    when(keycloakAdminClient.usernameExists(anyString())).thenReturn(false);

    MvcResult result = mockMvc.perform(get("/api/v1/auth/google/callback")
                    .param("code", "the-code")
                    .param("state", "abc123")
                    .cookie(new Cookie(RefreshCookieService.OAUTH_STATE_COOKIE, "abc123")))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:4200/auth/callback"))
            .andReturn();

    String setCookie = String.join(",", result.getResponse().getHeaders(HttpHeaders.SET_COOKIE));

    assertTrue(setCookie.contains("refreshToken=google-refresh-token"));
    assertTrue(setCookie.contains("HttpOnly"));
    assertTrue(setCookie.contains("Path=/api/v1/auth"));

    assertFalse(result.getResponse().getHeader(HttpHeaders.LOCATION).contains("token"));

    UserEntity provisioned = userRepository.findByKeycloakId(keycloakId).orElseThrow();

    assertEquals("alice@gmail.com", provisioned.getEmail());
    assertEquals("alice", provisioned.getName());
    assertNull(provisioned.getPassword());
    assertTrue(provisioned.isEmailVerified(), "a brokered user must not need our email verification");

    verify(keycloakAdminClient).updateUsername(keycloakId, "alice");
  }

  @Test
  void callback_derivedUsernameIsDedupedAgainstBothNamespaces() throws Exception {
    userRepository.save(UserHelper.createValidatedUser("alice", "hash", "someone.else@email.com"));

    UUID keycloakId = UUID.randomUUID();

    when(keycloakAuthClient.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(googleToken(keycloakId, "alice@gmail.com", "Alice"));
    when(keycloakAdminClient.usernameExists("alice1")).thenReturn(false);

    mockMvc.perform(get("/api/v1/auth/google/callback")
                    .param("code", "the-code")
                    .param("state", "abc123")
                    .cookie(new Cookie(RefreshCookieService.OAUTH_STATE_COOKIE, "abc123")))
            .andExpect(status().isFound());

    assertEquals("alice1", userRepository.findByKeycloakId(keycloakId).orElseThrow().getName());
  }

  @Test
  void callback_linksAnExistingLocalAccountWithTheSameEmail() throws Exception {
    UserEntity existing = userRepository.save(
            UserHelper.createValidatedUser("alice", "hash", "alice@gmail.com")
    );

    UUID keycloakId = UUID.randomUUID();

    when(keycloakAuthClient.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(googleToken(keycloakId, "alice@gmail.com", "Alice"));

    mockMvc.perform(get("/api/v1/auth/google/callback")
                    .param("code", "the-code")
                    .param("state", "abc123")
                    .cookie(new Cookie(RefreshCookieService.OAUTH_STATE_COOKIE, "abc123")))
            .andExpect(status().isFound());

    UserEntity linked = userRepository.findById(existing.getId()).orElseThrow();

    assertEquals(keycloakId, linked.getKeycloakId());
    assertEquals("alice", linked.getName(), "an existing account keeps its username");
    assertEquals(1, userRepository.count(), "linking must not create a second row");
    verify(keycloakAdminClient, never()).updateUsername(any(UUID.class), anyString());
  }

  @Test
  void callback_withMismatchedState_isRejected() throws Exception {
    mockMvc.perform(get("/api/v1/auth/google/callback")
                    .param("code", "the-code")
                    .param("state", "attacker-state")
                    .cookie(new Cookie(RefreshCookieService.OAUTH_STATE_COOKIE, "real-state")))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION,
                    "http://localhost:4200/auth/callback?error=login_failed"));

    verify(keycloakAuthClient, never()).exchangeAuthorizationCode(anyString(), anyString());
    assertEquals(0, userRepository.count());
  }

  @Test
  void callback_withNoStateCookie_isRejected() throws Exception {
    mockMvc.perform(get("/api/v1/auth/google/callback")
                    .param("code", "the-code")
                    .param("state", "abc123"))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION,
                    "http://localhost:4200/auth/callback?error=login_failed"));

    verify(keycloakAuthClient, never()).exchangeAuthorizationCode(anyString(), anyString());
  }
}
