package org.dnd.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.*;
import org.dnd.email.EmailService;
import org.dnd.exception.*;
import org.dnd.security.JwtService;
import org.dnd.security.LoginThrottleService;
import org.dnd.security.RegistrationTokenService;
import org.dnd.token.TokenEntity;
import org.dnd.token.TokenRepository;
import org.dnd.token.TokenService;
import org.dnd.token.TokenType;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
  private final TokenRepository tokenRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LoginThrottleService loginThrottleService;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final RegistrationTokenService registrationTokenService;
  private final EmailService emailService;
  private final TokenService tokenService;

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

    UserEntity user = userMapper.fromRegisterRequest(request);
    user.setPassword(passwordEncoder.encode(request.getPassword()));
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

    return userMapper.toDto(user);
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
  public void resendVerificationEmailToSameEmail(UserLoginRequest request) {
    UserEntity user = authenticateByNameAndPassword(
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
  public void sendVerificationEmailToNewEmail(String name, String password, String newEmail) {
    UserEntity user = userRepository.findByName(name)
            .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

    if (!passwordEncoder.matches(password, user.getPassword())) {
      throw new ForbiddenException("Invalid credentials");
    }

    if (user.isEmailVerified()) {
      throw new BadRequestException("Email is already verified");
    }

    String normalizedEmail = normalizeEmail(newEmail);

    if (!normalizedEmail.equalsIgnoreCase(user.getEmail())
            && userRepository.existsByEmail(normalizedEmail)) {
      throw new ConflictException("Email is already used");
    }

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
  public void changePasswordByToken(UserChangePasswordWithTokenRequest request) {
    tokenRepository.findByTokenAndValidTrue(request.getToken())
            .ifPresentOrElse(tokenEntity -> {
              UserEntity user = tokenEntity.getUser();
              user.setPassword(passwordEncoder.encode(request.getPassword()));
              userRepository.save(user);
              tokenRepository.delete(tokenEntity);
            }, () -> {
              throw new ForbiddenException("Invalid credentials");
            });
  }

  @Transactional
  public void changePasswordByAuth(UserChangePasswordRequest request) {
    User user = loginUser(userMapper.fromUserChangePasswordRequest(request)).getUser();

    UserEntity userEntity = userRepository.findById(user.getId())
            .orElseThrow(() -> new ForbiddenException("Invalid credentials"));
    userEntity.setPassword(passwordEncoder.encode(request.getNewPassword()));

    userRepository.save(userEntity);
  }

  @Transactional
  public void changeVerifiedEmail(String name, String password, String newEmail) {
    UserEntity user = userRepository.findByName(name)
            .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

    if (!passwordEncoder.matches(password, user.getPassword())) {
      throw new ForbiddenException("Invalid credentials");
    }

    if (!user.isEmailVerified()) {
      throw new BadRequestException("Current email is not verified");
    }

    String normalizedEmail = normalizeEmail(newEmail);

    if (normalizedEmail.equalsIgnoreCase(user.getEmail())) {
      throw new BadRequestException("New email must be different from current email");
    }

    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new ConflictException("Email is already used");
    }

    user.setPendingEmail(normalizedEmail);

    user = userRepository.save(user);

    TokenEntity verificationToken = tokenService.create(
            user,
            TokenType.EMAIL_CHANGE,
            normalizedEmail
    );

    emailService.sendVerificationEmail(
            user.getName(),
            normalizedEmail,
            verificationToken.getToken()
    );
  }

  public AuthResponse loginUser(UserLoginRequest request) {
    log.debug("Attempting login for user: {}", request.getName());

    UserEntity user = authenticateByNameAndPassword(
            request.getName(),
            request.getPassword()
    );

    if (!user.isEmailVerified()) {
      throw new EmailNotVerifiedException("Email is not verified");
    }

    return createAuthResponse(user);
  }

  @Transactional
  public User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    UserAuthDTO userAuth = (UserAuthDTO) authentication.getPrincipal();

    UserEntity user = userRepository.findById(userAuth.getId())
            .orElseThrow(() -> new NotFoundException("User not found"));

    User userResponse = userMapper.toDto(user);
    userResponse.setLimits(userRankEvaluatorService.getLimitsForUser(user));
    return userResponse;
  }

  private void verifyRegistrationEmail(
          UserEntity user,
          TokenEntity verificationToken
  ) {
    if (!verificationToken.getTargetEmail().equalsIgnoreCase(user.getEmail())) {
      throw new BadRequestException("Verification token does not match current email");
    }

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

    if (userRepository.existsByEmail(pendingEmail)) {
      throw new ConflictException("Email is already used");
    }

    user.setEmail(pendingEmail);
    user.setPendingEmail(null);
    user.setEmailVerified(true);
  }

  private AuthResponse createAuthResponse(UserEntity user) {
    AuthResponse response = new AuthResponse();
    response.setUser(userMapper.toDto(user));
    response.setToken(generateToken(userMapper.toAuthDto(user)));
    return response;
  }

  private String generateToken(UserAuthDTO user) {
    return jwtService.generateToken(user);
  }

  private String generateEmailVerificationToken() {
    return registrationTokenService.generateToken();
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

  private UserEntity authenticateByNameAndPassword(String username, String password) {
    loginThrottleService.checkAllowed(username);

    UserEntity user = userRepository.findByName(username)
            .orElseThrow(() -> {
              loginThrottleService.recordFailure(username);
              return new UnauthorizedException("Invalid username or password");
            });

    if (!passwordEncoder.matches(password, user.getPassword())) {
      loginThrottleService.recordFailure(username);
      throw new UnauthorizedException("Invalid username or password");
    }

    loginThrottleService.recordSuccess(username);
    return user;
  }
}