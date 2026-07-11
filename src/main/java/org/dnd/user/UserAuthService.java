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
import org.dnd.security.LoginThrottleService;
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

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final LoginThrottleService loginThrottleService;
  private final EmailService emailService;
  private final TokenRepository tokenRepository;
  private final TokenService tokenService;
  private final RefreshCookieService refreshCookieService;
  private final KeycloakAuthClient keycloakAuthClient;
  private final KeycloakAdminClient keycloakAdminClient;
  private final ObjectMapper objectMapper;
  private final SecurityUtils securityUtils;
  private final UserIdentifierResolver userIdentifierResolver;
  private final TransactionCompensation transactionCompensation;

  @Transactional
  public User registerUser(UserRegisterRequest request) {
    log.debug("Registering new user with name: {}", request.getName());

    if (userRepository.existsByName(request.getName())) {
      throw new UserAlreadyExistsException("Username already exists");
    }

    String normalizedEmail = normalizeEmail(request.getEmail());

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
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setEmailVerified(emailVerified);
    user.setPendingEmail(null);

    user = userRepository.save(user);

    if (emailUse) {
      TokenEntity verificationToken = tokenService.create(
              user,
              TokenType.REGISTRATION,
              user.getEmail()
      );

      emailService.sendVerificationEmail(
              user.getName(),
              user.getEmail(),
              verificationToken.getToken()
      );
    }

    return userMapper.toDto(user);
  }

  @Transactional
  public AuthenticationResult loginUser(UserLoginRequest request) {
    log.debug("Attempting login for user: {}", request.getName());

    String identifier = request.getName();

    Optional<UserEntity> resolvedUser = userIdentifierResolver.find(identifier);

    String throttleKey = resolvedUser
            .map(UserEntity::getName)
            .orElse(identifier);

    loginThrottleService.checkAllowed(throttleKey);

    UserEntity user = resolvedUser
            .orElseThrow(() -> {
              loginThrottleService.recordFailure(throttleKey);
              return new UnauthorizedException("Invalid username or password");
            });

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

      loginThrottleService.recordSuccess(throttleKey);

      return createAuthenticationResult(user, keycloakToken);
    } catch (UnauthorizedException exception) {
      loginThrottleService.recordFailure(throttleKey);
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
    TokenEntity verificationToken = tokenRepository
            .findByTokenAndValidTrue(token)
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
    TokenEntity tokenEntity = tokenRepository
            .findByTokenAndValidTrue(request.getToken())
            .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

    if (tokenEntity.getType() != TokenType.FORGOT_PASSWORD) {
      throw new BadRequestException("Unsupported password token type");
    }

    UserEntity user = tokenEntity.getUser();

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
    }

    user.setPassword(passwordEncoder.encode(request.getPassword()));

    userRepository.save(user);
    tokenRepository.delete(tokenEntity);
  }

  @Transactional
  public void changeEmailByAuth(UserRegisterRequest request) {
    String identifier = request.getName();
    String password = request.getPassword();
    String newEmail = normalizeEmail(request.getEmail());

    UserEntity user = userIdentifierResolver.find(identifier)
            .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

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
      user.setPendingEmail(newEmail);
      userRepository.save(user);

      TokenEntity verificationToken = tokenService.create(
              user,
              TokenType.EMAIL_CHANGE,
              newEmail
      );

      emailService.sendEmailChangeMail(
              user.getName(),
              newEmail,
              verificationToken.getToken()
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

    if (!userIdentifierResolver.matches(user, request.getName())) {
      throw new ForbiddenException("Invalid credentials");
    }

    if (user.getKeycloakId() == null) {
      user = migrateExistingUserToKeycloak(user, request.getPassword());
    } else {
      validatePasswordInKeycloak(user.getName(), request.getPassword());
    }

    keycloakAdminClient.updatePassword(
            user.getKeycloakId(),
            request.getNewPassword()
    );

    // Temporary while old local-password flows still exist.
    user.setPassword(passwordEncoder.encode(request.getNewPassword()));

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
    if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
      throw new UnauthorizedException("Invalid username or password");
    }

    UUID keycloakId = keycloakAdminClient.createUser(
            user.getName(),
            user.getEmail(),
            rawPassword,
            user.isEmailVerified()
    );

    user.setKeycloakId(keycloakId);

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

  private void validateEmailIsNotUsedByAnotherUser(String email, Long currentUserId) {
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