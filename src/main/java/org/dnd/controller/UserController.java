package org.dnd.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.dnd.api.UsersApi;
import org.dnd.api.model.AuthResponse;
import org.dnd.api.model.User;
import org.dnd.api.model.UserLoginRequest;
import org.dnd.api.model.UserRegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@Tag(name = "Users", description = "User authentication and profile operations")
@RestController
@Validated
public class UserController implements UsersApi {

    @Override
    public ResponseEntity<AuthResponse> registerUser(UserRegisterRequest userRegisterRequest) {

        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<AuthResponse> loginUser(UserLoginRequest userLoginRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<User> getCurrentUser() {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}