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

        UserEntity user = userRepository.findByName(request.getName())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");

        }
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
