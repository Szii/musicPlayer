package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.AuthResponse;
import org.dnd.api.model.User;
import org.dnd.api.model.UserLoginRequest;
import org.dnd.api.model.UserRegisterRequest;
import org.dnd.exception.ConflictException;
import org.dnd.exception.NotFoundException;
import org.dnd.exception.UnauthorizedException;
import org.dnd.mappers.UserMapper;
import org.dnd.model.UserEntity;
import org.dnd.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse registerUser(UserRegisterRequest request) {
        log.debug("Registering new user with name: {}", request.getName());

        if (userRepository.findByName(request.getName()).isPresent()) {
            throw new ConflictException("Username already exists");
        }

        UserEntity user = userMapper.fromRegisterRequest(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        return createAuthResponse(user);
    }

    public AuthResponse loginUser(UserLoginRequest request) {
        log.debug("Attempting login for user: {}", request.getName());

        UserEntity user = userRepository.findByName(request.getName())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return createAuthResponse(user);
    }

    public User getCurrentUser(Long userId) {
        log.debug("Fetching current user with id: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));

        return userMapper.toDto(user);
    }

    private AuthResponse createAuthResponse(UserEntity user) {
        AuthResponse response = new AuthResponse();
        response.setUser(userMapper.toDto(user));
        response.setToken(generateToken(user));
        return response;
    }

    private String generateToken(UserEntity user) {
        // implement token generation
        return "generated.jwt.token";
    }
}
