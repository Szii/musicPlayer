package org.dnd.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dnd.api.UsersApi;
import org.dnd.api.model.AuthResponse;
import org.dnd.api.model.User;
import org.dnd.api.model.UserLoginRequest;
import org.dnd.api.model.UserRegisterRequest;
import org.dnd.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@Tag(name = "Users", description = "User authentication and profile operations")
@RestController
@Validated
@RequiredArgsConstructor
public class UserController implements UsersApi {
    private final UserService userService;

    @Override
    public ResponseEntity<AuthResponse> registerUser(UserRegisterRequest userRegisterRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.registerUser(userRegisterRequest));
    }

    @Override
    public ResponseEntity<AuthResponse> loginUser(UserLoginRequest userLoginRequest) {
        return ResponseEntity.ok(userService.loginUser(userLoginRequest));
    }

    @Override
    public ResponseEntity<User> getCurrentUser() {
        // implement getting user id through security context
        Long currentUserId = 1L;
        return ResponseEntity.ok(userService.getCurrentUser(currentUserId));
    }
}