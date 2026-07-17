package org.dnd.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.*;
import org.dnd.email.EmailService;
import org.dnd.exception.*;
import org.dnd.keycloak.KeycloakAdminClient;
import org.dnd.keycloak.KeycloakAuthClient;
import org.dnd.keycloak.KeycloakTokenResponse;
import org.dnd.security.AuthenticationResult;
import org.dnd.security.LoginLockoutService;
import org.dnd.security.RefreshCookieService;
import org.dnd.token.TokenEntity;
import org.dnd.token.TokenRepository;
import org.dnd.token.TokenService;
import org.dnd.token.TokenType;
import org.dnd.utils.SecurityUtils;
import org.dnd.utils.TransactionCompensation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserAuthService {

  @Value("${app.email-use}")
  private boolean emailUse;

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
          "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
          Pattern.CASE_INSENSITIVE
  );

  private static final int USERNAME_MAX_LENGTH = 30;

  private static final Pattern USERNAME_PATTERN =
          Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{1,28}[A-Za-z0-9]$");

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final LoginLockoutService loginLockoutService;
  private final EmailService emailService;
  private final TokenRepository tokenRepository;
  private final TokenService tokenService;
  private final RefreshCookieService refreshCookieService;
  private final KeycloakAuthClient keycloakAuthClient;
  private final KeycloakAdminClient keycloakAdminClient;
  private final ObjectMapper objectMapper;
  private final SecurityUtils securityUtils;
  private final TransactionCompensation transactionCompensation;

  @Transactional
  public User registerUser(UserRegisterRequest request) {
    log.debug("Registering new user with name: {}", request.getName());

    String username = validateUsername(request.getName());
    String normalizedEmail = normalizeEmail(request.getEmail());

    request.setName(username);

    releaseUnverifiedClaim(userRepository.findByName(username));
    releaseUnverifiedClaim(userRepository.findByEmail(normalizedEmail));

    if (userRepository.existsByNameIgnoreCase(username)) {
      throw new UserAlreadyExistsException("Username already exists");
    }

    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new EmailAlreadyExistsException("Email already exists");
    }

    request.setEmail(normalizedEmail);

    boolean emailVerified = !emailUse;

    UUID keycloakId = keycloakAdminClient.createUser(
            request.getName(),
            normalizedEmail,
            request.getPassword(),
            emailVerified
    );

    transactionCompensation.onRollback(() -> keycloakAdminClient.deleteUser(keycloakId));

    UserEntity user = userMapper.fromRegisterRequest(request);
    user.setKeycloakId(keycloakId);
    user.setPassword(null);
    user.setEmailVerified(emailVerified);
    user.setPendingEmail(null);

    user = userRepository.save(user);

    if (emailUse) {
      String verificationToken = tokenService.create(
              user,
              TokenType.REGISTRATION,
              user.getEmail()
      );

      emailService.sendVerificationEmail(
              user.getName(),
              user.getEmail(),
              verificationToken
      );
    }

    return userMapper.toDto(user);
  }

  private void releaseUnverifiedClaim(Optional<UserEntity> existingUser) {
    existingUser
            .filter(user -> !user.isEmailVerified())
            .ifPresent(user -> {
              log.info(
                      "Replacing unverified registration. userId={}, username={}",
                      user.getId(),
                      user.getName()
              );

              if (user.getKeycloakId() != null) {
                keycloakAdminClient.deleteUser(user.getKeycloakId());
              }

              userRepository.delete(user);
              userRepository.flush();
            });
  }

  @Transactional
  public AuthenticationResult loginUser(UserLoginRequest request) {
    log.debug("Attempting login for: {}", request.getEmail());

    UserEntity user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
            .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

    try {
      if (user.getKeycloakId() == null) {
        user = migrateExistingUserToKeycloak(user, request.getPassword());
      }

      KeycloakTokenResponse keycloakToken = keycloakAuthClient.login(
              user.getName(),
              request.getPassword()
      );

      user = syncUserFromAccessToken(user, keycloakToken.accessToken());

      if (!user.isEmailVerified()) {
        throw new EmailNotVerifiedException("Email is not verified");
      }

      return createAuthenticationResult(user, keycloakToken);
    } catch (UnauthorizedException exception) {
      loginLockoutService.throwIfLockedOut(user);
      throw exception;
    }
  }

  @Transactional
  public AuthenticationResult refreshUserToken(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new UnauthorizedException("Invalid refresh token");
    }

    KeycloakTokenResponse keycloakToken = keycloakAuthClient.refresh(refreshToken);

    UUID keycloakId = extractSubjectFromAccessToken(keycloakToken.accessToken());

    UserEntity user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

    user = syncUserFromAccessToken(user, keycloakToken.accessToken());

    if (!user.isEmailVerified()) {
      throw new EmailNotVerifiedException("Email is not verified");
    }

    return createAuthenticationResult(user, keycloakToken);
  }

  @Transactional
  public void provisionBrokeredUser(String accessToken) {
    JsonNode payload = extractPayloadFromAccessToken(accessToken);

    UUID keycloakId = extractSubjectFromAccessToken(accessToken);

    if (userRepository.findByKeycloakId(keycloakId).isPresent()) {
      return;
    }

    String email = normalizeEmail(payload.path("email").asText(null));

    Optional<UserEntity> existingByEmail = userRepository.findByEmail(email);

    if (existingByEmail.isPresent()) {
      UserEntity user = existingByEmail.get();
      user.setKeycloakId(keycloakId);
      user.setEmailVerified(true);

      userRepository.save(user);

      return;
    }

    UserEntity user = new UserEntity();
    user.setKeycloakId(keycloakId);
    user.setName(claimUsernameFor(keycloakId, payload, email));
    user.setEmail(email);
    user.setPassword(null);
    user.setEmailVerified(true);
    user.setPendingEmail(null);

    userRepository.save(user);
  }

  private String claimUsernameFor(UUID keycloakId, JsonNode payload, String email) {
    String base = sanitizeUsername(payload.path("given_name").asText(null));

    if (base.isBlank()) {
      base = sanitizeUsername(email.substring(0, email.indexOf('@')));
    }

    if (base.length() < 3) {
      base = base + "user";
    }

    base = base.substring(0, Math.min(base.length(), USERNAME_MAX_LENGTH - 4));

    String candidate = base;

    for (int suffix = 1; isUsernameTaken(candidate); suffix++) {
      if (suffix > 999) {
        candidate = base + UUID.randomUUID().toString().substring(0, 4);
        break;
      }

      candidate = base + suffix;
    }

    keycloakAdminClient.updateUsername(keycloakId, candidate);

    return candidate;
  }

  private String validateUsername(String username) {
    String trimmed = username == null ? "" : username.trim();

    if (!USERNAME_PATTERN.matcher(trimmed).matches()) {
      throw new BadRequestException(
              "Username must be 3-30 characters: letters, digits, dot, underscore or hyphen, "
                      + "starting and ending with a letter or digit"
      );
    }

    return trimmed;
  }

  private boolean isUsernameTaken(String username) {
    return userRepository.existsByNameIgnoreCase(username)
            || keycloakAdminClient.usernameExists(username);
  }

  private String sanitizeUsername(String value) {
    if (value == null) {
      return "";
    }

    return value.toLowerCase().replaceAll("[^a-z0-9]", "");
  }

  @Transactional
  public User changeUsername(String newUsername) {
    UserEntity user = securityUtils.getCurrentUserEntity();

    String requested = validateUsername(newUsername);

    if (requested.equals(user.getName())) {
      return userMapper.toDto(user);
    }

    if (isUsernameTaken(requested)) {
      throw new ConflictException("Username already exists");
    }

    String previousUsername = user.getName();

    keycloakAdminClient.updateUsername(user.getKeycloakId(), requested);

    transactionCompensation.onRollback(
            () -> keycloakAdminClient.updateUsername(user.getKeycloakId(), previousUsername)
    );

    user.setName(requested);

    return userMapper.toDto(userRepository.save(user));
  }

  private void assertNotGoogleManaged(UserEntity user) {
    if (keycloakAdminClient.hasFederatedIdentity(user.getKeycloakId())) {
      throw new ForbiddenException("This account is managed by Google");
    }
  }

  public ResponseCookie logoutUser(String refreshToken) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      keycloakAuthClient.logout(refreshToken);
    }

    return refreshCookieService.clearRefreshCookie();
  }

  public ResponseCookie createRefreshCookie(String refreshToken) {
    return refreshCookieService.createRefreshCookie(refreshToken);
  }

  @Transactional
  public void verifyEmail(String token) {
    TokenEntity verificationToken = tokenService
            .findValid(token)
            .orElseThrow(() -> new ForbiddenException("Invalid verification token"));

    UserEntity user = verificationToken.getUser();

    if (verificationToken.getType() == TokenType.REGISTRATION) {
      verifyRegistrationEmail(user, verificationToken);
    } else if (verificationToken.getType() == TokenType.EMAIL_CHANGE) {
      verifyEmailChange(user, verificationToken);
    } else {
      throw new BadRequestException("Unsupported verification token type");
    }

    userRepository.save(user);
    tokenRepository.delete(verificationToken);
  }

  @Transactional
  public void changePasswordByToken(UserChangePasswordWithTokenRequest request) {
    TokenEntity tokenEntity = tokenService
            .findValid(request.getToken())
            .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

    if (tokenEntity.getType() != TokenType.FORGOT_PASSWORD) {
      throw new BadRequestException("Unsupported password token type");
    }

    UserEntity user = tokenEntity.getUser();

    assertNotGoogleManaged(user);

    if (user.getKeycloakId() == null) {
      UUID keycloakId = keycloakAdminClient.createUser(
              user.getName(),
              user.getEmail(),
              request.getPassword(),
              user.isEmailVerified()
      );

      user.setKeycloakId(keycloakId);
    } else {
      keycloakAdminClient.updatePassword(
              user.getKeycloakId(),
              request.getPassword()
      );

      keycloakAdminClient.logoutUser(user.getKeycloakId());
    }

    user.setPassword(null);
    cancelPendingEmailChange(user);

    userRepository.save(user);
    tokenRepository.delete(tokenEntity);
  }

  private void cancelPendingEmailChange(UserEntity user) {
    tokenRepository
            .findByUserIdAndType(user.getId(), TokenType.EMAIL_CHANGE)
            .ifPresent(tokenRepository::delete);

    user.setPendingEmail(null);
  }

  @Transactional
  public void changeEmailByAuth(ChangeEmailRequest request) {
    String password = request.getPassword();
    String newEmail = normalizeEmail(request.getEmail());

    UserEntity user = securityUtils.getCurrentUserEntity();

    assertNotGoogleManaged(user);

    if (user.getKeycloakId() == null) {
      user = migrateExistingUserToKeycloak(user, password);
    } else {
      validatePasswordInKeycloak(user.getName(), password);
    }

    if (!user.isEmailVerified()) {
      throw new BadRequestException("Current email is not verified");
    }

    if (newEmail.equalsIgnoreCase(user.getEmail())) {
      throw new BadRequestException("New email must be different from current email");
    }

    validateEmailIsNotUsedByAnotherUser(newEmail, user.getId());

    if (emailUse) {
      String previousEmail = user.getEmail();

      user.setPendingEmail(newEmail);
      userRepository.save(user);

      String verificationToken = tokenService.create(
              user,
              TokenType.EMAIL_CHANGE,
              newEmail
      );

      emailService.sendEmailChangeMail(
              user.getName(),
              newEmail,
              verificationToken
      );

      emailService.sendEmailChangeNotice(
              user.getName(),
              previousEmail,
              newEmail
      );

      return;
    }

    keycloakAdminClient.updateEmail(
            user.getKeycloakId(),
            newEmail,
            true
    );

    user.setEmail(newEmail);
    user.setPendingEmail(null);
    user.setEmailVerified(true);

    userRepository.save(user);
  }

  @Transactional
  public void changePasswordByAuth(UserChangePasswordRequest request) {
    UserEntity user = securityUtils.getCurrentUserEntity();

    assertNotGoogleManaged(user);

    if (user.getKeycloakId() == null) {
      user = migrateExistingUserToKeycloak(user, request.getPassword());
    } else {
      validatePasswordInKeycloak(user.getName(), request.getPassword());
    }

    keycloakAdminClient.updatePassword(
            user.getKeycloakId(),
            request.getNewPassword()
    );

    keycloakAdminClient.logoutUser(user.getKeycloakId());

    user.setPassword(null);
    cancelPendingEmailChange(user);

    userRepository.save(user);
  }

  private void verifyRegistrationEmail(
          UserEntity user,
          TokenEntity verificationToken
  ) {
    if (!verificationToken.getTargetEmail().equalsIgnoreCase(user.getEmail())) {
      throw new BadRequestException("Verification token does not match current email");
    }

    if (user.getKeycloakId() == null) {
      throw new BadRequestException("User is not linked to Keycloak");
    }

    keycloakAdminClient.updateEmail(
            user.getKeycloakId(),
            user.getEmail(),
            true
    );

    user.setEmailVerified(true);
  }

  private void verifyEmailChange(
          UserEntity user,
          TokenEntity verificationToken
  ) {
    String pendingEmail = user.getPendingEmail();

    if (pendingEmail == null || pendingEmail.isBlank()) {
      throw new BadRequestException("No pending email change");
    }

    if (!verificationToken.getTargetEmail().equalsIgnoreCase(pendingEmail)) {
      throw new BadRequestException("Verification token does not match pending email");
    }

    validateEmailIsNotUsedByAnotherUser(pendingEmail, user.getId());

    if (user.getKeycloakId() == null) {
      throw new BadRequestException("User is not linked to Keycloak");
    }

    keycloakAdminClient.updateEmail(
            user.getKeycloakId(),
            pendingEmail,
            true
    );

    user.setEmail(pendingEmail);
    user.setPendingEmail(null);
    user.setEmailVerified(true);
  }

  private UserEntity migrateExistingUserToKeycloak(UserEntity user, String rawPassword) {
    if (user.getPassword() == null
            || !passwordEncoder.matches(rawPassword, user.getPassword())) {
      throw new UnauthorizedException("Invalid username or password");
    }

    UUID keycloakId = keycloakAdminClient.createUser(
            user.getName(),
            user.getEmail(),
            rawPassword,
            user.isEmailVerified()
    );

    user.setKeycloakId(keycloakId);

    user.setPassword(null);

    return userRepository.save(user);
  }

  private AuthenticationResult createAuthenticationResult(
          UserEntity user,
          KeycloakTokenResponse keycloakToken
  ) {
    AuthResponse response = new AuthResponse();
    response.setUser(userMapper.toDto(user));
    response.setToken(keycloakToken.accessToken());

    return new AuthenticationResult(response, keycloakToken.refreshToken());
  }

  private UserEntity syncUserFromAccessToken(UserEntity user, String accessToken) {
    JsonNode payload = extractPayloadFromAccessToken(accessToken);

    String tokenEmail = payload.path("email").asText(null);
    boolean tokenEmailVerified = payload.path("email_verified").asBoolean(user.isEmailVerified());

    boolean changed = false;

    if (tokenEmail != null && !tokenEmail.isBlank()) {
      String normalizedTokenEmail = normalizeEmail(tokenEmail);

      if (!normalizedTokenEmail.equalsIgnoreCase(user.getEmail())) {
        validateEmailIsNotUsedByAnotherUser(normalizedTokenEmail, user.getId());
        user.setEmail(normalizedTokenEmail);
        user.setPendingEmail(null);
        changed = true;
      }
    }

    if (user.isEmailVerified() != tokenEmailVerified) {
      user.setEmailVerified(tokenEmailVerified);
      changed = true;
    }

    if (!changed) {
      return user;
    }

    return userRepository.save(user);
  }

  private void validateEmailIsNotUsedByAnotherUser(String email, UUID currentUserId) {
    userRepository.findByEmail(email)
            .filter(existingUser -> !existingUser.getId().equals(currentUserId))
            .ifPresent(existingUser -> {
              throw new ConflictException("Email is already used");
            });
  }

  private UUID extractSubjectFromAccessToken(String accessToken) {
    JsonNode payload = extractPayloadFromAccessToken(accessToken);
    String subject = payload.path("sub").asText(null);

    if (subject == null || subject.isBlank()) {
      throw new UnauthorizedException("Invalid access token");
    }

    try {
      return UUID.fromString(subject);
    } catch (Exception exception) {
      throw new UnauthorizedException("Invalid access token");
    }
  }

  private JsonNode extractPayloadFromAccessToken(String accessToken) {
    try {
      String[] parts = accessToken.split("\\.");

      if (parts.length < 2) {
        throw new UnauthorizedException("Invalid access token");
      }

      String payloadJson = new String(
              Base64.getUrlDecoder().decode(parts[1]),
              StandardCharsets.UTF_8
      );

      return objectMapper.readTree(payloadJson);
    } catch (Exception exception) {
      throw new UnauthorizedException("Invalid access token");
    }
  }

  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new BadRequestException("Email is required");
    }

    String normalizedEmail = email.trim().toLowerCase();

    if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
      throw new BadRequestException("Invalid email format");
    }

    return normalizedEmail;
  }

  private void validatePasswordInKeycloak(String username, String rawPassword) {
    try {
      KeycloakTokenResponse tokenResponse = keycloakAuthClient.login(username, rawPassword);

      if (tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank()) {
        keycloakAuthClient.logout(tokenResponse.refreshToken());
      }
    } catch (UnauthorizedException exception) {
      throw new ForbiddenException("Invalid credentials");
    }
  }
}