package org.dnd.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.User;
import org.dnd.api.model.UserLoginRequest;
import org.dnd.email.EmailService;
import org.dnd.exception.BadRequestException;
import org.dnd.exception.ConflictException;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.UnauthorizedException;
import org.dnd.keycloak.KeycloakAdminClient;
import org.dnd.keycloak.KeycloakAuthClient;
import org.dnd.keycloak.KeycloakTokenResponse;
import org.dnd.security.LoginThrottleService;
import org.dnd.token.TokenEntity;
import org.dnd.token.TokenService;
import org.dnd.token.TokenType;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.dnd.utils.TransactionCompensation;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
          "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
          Pattern.CASE_INSENSITIVE
  );

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final LoginThrottleService loginThrottleService;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final EmailService emailService;
  private final TokenService tokenService;
  private final SecurityUtils securityUtils;
  private final KeycloakAuthClient keycloakAuthClient;
  private final KeycloakAdminClient keycloakAdminClient;
  private final UserIdentifierResolver userIdentifierResolver;
  private final TransactionCompensation transactionCompensation;

  @Transactional
  public void resendVerificationEmailToSameEmail(UserLoginRequest request) {
    UserEntity user = authenticateByIdentifierAndPassword(
            request.getName(),
            request.getPassword()
    );

    if (user.isEmailVerified()) {
      return;
    }

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

  @Transactional
  public void sendVerificationEmailToNewEmail(String identifier, String password, String newEmail) {
    UserEntity user = userIdentifierResolver.find(identifier)
            .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

    String previousEmail = user.getEmail();

    validatePasswordInKeycloakOrForbidden(user, password);

    if (user.isEmailVerified()) {
      throw new BadRequestException("Email is already verified");
    }

    String normalizedEmail = normalizeEmail(newEmail);

    if (!normalizedEmail.equalsIgnoreCase(user.getEmail())
            && userRepository.existsByEmail(normalizedEmail)) {
      throw new ConflictException("Email is already used");
    }

    if (user.getKeycloakId() == null) {
      throw new ForbiddenException("User is not linked to Keycloak");
    }

    UUID keycloakId = user.getKeycloakId();

    keycloakAdminClient.updateEmail(
            keycloakId,
            normalizedEmail,
            false
    );

    transactionCompensation.onRollback(
            () -> keycloakAdminClient.updateEmail(keycloakId, previousEmail, false)
    );

    user.setEmail(normalizedEmail);
    user.setEmailVerified(false);
    user.setPendingEmail(null);

    user = userRepository.save(user);

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

  @Transactional
  public void sendChangePasswordEmail(String email) {
    String normalizedEmail = normalizeEmail(email);

    userRepository.findByEmail(normalizedEmail)
            .ifPresent(user -> {
              TokenEntity resetToken = tokenService.create(
                      user,
                      TokenType.FORGOT_PASSWORD,
                      user.getEmail()
              );

              emailService.sendPasswordReset(user.getEmail(), resetToken.getToken());
            });
  }

  @Transactional
  public User getCurrentUser() {
    UserEntity user = securityUtils.getCurrentUserEntity();

    User userResponse = userMapper.toDto(user);
    userResponse.setLimits(userRankEvaluatorService.getLimitsForUser(user));
    return userResponse;
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

  private UserEntity authenticateByIdentifierAndPassword(String identifier, String password) {
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
      validatePasswordInKeycloak(user, password);
      loginThrottleService.recordSuccess(throttleKey);
      return user;
    } catch (UnauthorizedException exception) {
      loginThrottleService.recordFailure(throttleKey);
      throw exception;
    }
  }

  private void validatePasswordInKeycloakOrForbidden(UserEntity user, String password) {
    try {
      validatePasswordInKeycloak(user, password);
    } catch (UnauthorizedException exception) {
      throw new ForbiddenException("Invalid credentials");
    }
  }

  private void validatePasswordInKeycloak(UserEntity user, String password) {
    if (user.getKeycloakId() == null) {
      throw new UnauthorizedException("Invalid username or password");
    }

    KeycloakTokenResponse tokenResponse = keycloakAuthClient.login(
            user.getName(),
            password
    );

    if (tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank()) {
      keycloakAuthClient.logout(tokenResponse.refreshToken());
    }
  }
}
