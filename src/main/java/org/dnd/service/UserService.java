package org.dnd.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.*;
import org.dnd.exception.ConflictException;
import org.dnd.exception.NotFoundException;
import org.dnd.exception.UnauthorizedException;
import org.dnd.mappers.UserMapper;
import org.dnd.model.UserEntity;
import org.dnd.repository.UserRepository;
import org.dnd.security.LoginThrottleService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LoginThrottleService loginThrottleService;

  @Transactional
  public AuthResponse registerUser(UserRegisterRequest request) {
    log.debug("Registering new user with name: {}", request.getName());

    if (userRepository.findByName(request.getName()).isPresent()) {
      log.debug("ser with name: {} already exists", request.getName());
      throw new ConflictException("Username already exists");
    }

    UserEntity user = userMapper.fromRegisterRequest(request);
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user = userRepository.save(user);

    return createAuthResponse(user);
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

    return userMapper.toDto(user);
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

}
