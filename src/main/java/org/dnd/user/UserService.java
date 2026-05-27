package org.dnd.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.*;
import org.dnd.email.EmailService;
import org.dnd.exception.EmailAlreadyExistsException;
import org.dnd.exception.NotFoundException;
import org.dnd.exception.UnauthorizedException;
import org.dnd.exception.UserAlreadyExistsException;
import org.dnd.security.JwtService;
import org.dnd.security.LoginThrottleService;
import org.dnd.security.RegistrationTokenService;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LoginThrottleService loginThrottleService;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final RegistrationTokenService registrationTokenService;
  private final EmailService emailService;

  @Transactional
  public AuthResponse registerUser(UserRegisterRequest request) {
    log.debug("Registering new user with name: {}", request.getName());

    if (userRepository.existsByName(request.getName())) {
      log.debug("User with name: {} already exists", request.getName());
      throw new UserAlreadyExistsException("Username already exists");
    }

    if (userRepository.existsByEmail(request.getEmail())) {
      log.debug("User with email: {} already exists", request.getEmail());
      throw new EmailAlreadyExistsException("Email already exists");
    }

    request.setEmail(request.getEmail().toLowerCase());

    UserEntity user = userMapper.fromRegisterRequest(request);
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setVerificationToken(generateEmailVerificationToken());

    emailService.sendVerificationEmail(user.getName(), user.getEmail(), user.getVerificationToken());

    user = userRepository.save(user);

    return createAuthResponse(user);
  }

  public void verifyEmail(String token) {
    UserEntity user = userRepository.findByVerificationToken(token)
            .orElseThrow(() -> new NotFoundException("Invalid verification token"));
    user.setEmailVerified(true);
    user.setVerificationToken(null);
    userRepository.save(user);
  }

  @Transactional
  public void resendVerificationEmail(String email) {
    String normalizedEmail = email.trim().toLowerCase();

    Optional<UserEntity> optionalUser = userRepository.findByEmail(normalizedEmail);

    if (optionalUser.isEmpty() || optionalUser.get().isEmailVerified()) {
      return;
    }

    UserEntity user = optionalUser.get();

    String verificationToken = registrationTokenService.generateToken();

    user.setVerificationToken(verificationToken);
    userRepository.save(user);

    emailService.sendVerificationEmail(
            user.getName(),
            user.getEmail(),
            verificationToken
    );
  }

  public AuthResponse loginUser(UserLoginRequest request) {
    log.debug("Attempting login for user: {}", request.getName());

    String username = request.getName();
    loginThrottleService.checkAllowed(username);

    UserEntity user = userRepository.findByName(username)
            .orElseThrow(() -> {
              loginThrottleService.recordFailure(username);
              return new UnauthorizedException("Invalid username or password");
            });

    if (!user.isEmailVerified()) {
      loginThrottleService.recordFailure(username);
      throw new UnauthorizedException("Invalid username or password");
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      loginThrottleService.recordFailure(username);
      throw new UnauthorizedException("Invalid username or password");
    }

    loginThrottleService.recordSuccess(username);
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
}
