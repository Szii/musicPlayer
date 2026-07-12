package org.dnd.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.User;
import org.dnd.api.model.UserLoginRequest;
import org.dnd.email.EmailService;
import org.dnd.exception.BadRequestException;
import org.dnd.exception.UnauthorizedException;
import org.dnd.keycloak.KeycloakAdminClient;
import org.dnd.keycloak.KeycloakAuthClient;
import org.dnd.keycloak.KeycloakTokenResponse;
import org.dnd.security.LoginLockoutService;
import org.dnd.token.TokenService;
import org.dnd.token.TokenType;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;

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
  private final LoginLockoutService loginLockoutService;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final EmailService emailService;
  private final TokenService tokenService;
  private final SecurityUtils securityUtils;
  private final KeycloakAuthClient keycloakAuthClient;
  private final KeycloakAdminClient keycloakAdminClient;

  @Transactional
  public void resendVerificationEmailToSameEmail(UserLoginRequest request) {
    UserEntity user = authenticateByEmailAndPassword(
            request.getEmail(),
            request.getPassword()
    );

    if (user.isEmailVerified()) {
      return;
    }

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

  @Transactional
  public void sendChangePasswordEmail(String email) {
    String normalizedEmail = normalizeEmail(email);

    userRepository.findByEmail(normalizedEmail)
            .filter(user -> !keycloakAdminClient.hasFederatedIdentity(user.getKeycloakId()))
            .ifPresent(user -> {
              String resetToken = tokenService.create(
                      user,
                      TokenType.FORGOT_PASSWORD,
                      user.getEmail()
              );

              emailService.sendPasswordReset(user.getEmail(), resetToken);
            });
  }

  @Transactional
  public User getCurrentUser() {
    UserEntity user = securityUtils.getCurrentUserEntity();

    User userResponse = userMapper.toDto(user);
    userResponse.setLimits(userRankEvaluatorService.getLimitsForUser(user));
    userResponse.setManagedByGoogle(
            keycloakAdminClient.hasFederatedIdentity(user.getKeycloakId())
    );

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

  private UserEntity authenticateByEmailAndPassword(String email, String password) {
    UserEntity user = userRepository.findByEmail(normalizeEmail(email))
            .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

    try {
      validatePasswordInKeycloak(user, password);
      return user;
    } catch (UnauthorizedException exception) {
      loginLockoutService.throwIfLockedOut(user);
      throw exception;
    }
  }

  private void validatePasswordInKeycloak(UserEntity user, String password) {
    if (user.getKeycloakId() == null) {
      throw new UnauthorizedException("Invalid email or password");
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
