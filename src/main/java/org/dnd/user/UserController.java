package org.dnd.user;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class UserController implements UsersApi {
  private final UserService userService;

  @Override
  @RateLimiting(name = "register-strict",
          cacheKey = "@rateLimitKeyResolver.registerKey(#userRegisterRequest)",
          ratePerMethod = true)
  public ResponseEntity<User> registerUser(UserRegisterRequest userRegisterRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.registerUser(userRegisterRequest));
  }


  @Override
  @RateLimiting(
          name = "resend-verification-strict",
          cacheKey = "@rateLimitKeyResolver.resendVerificationKey(#request)",
          ratePerMethod = true
  )
  public ResponseEntity<Void> resendVerificationEmail(UserLoginRequest request) throws Exception {
    userService.resendVerificationEmail(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(
          name = "verify-email-strict",
          cacheKey = "@rateLimitKeyResolver.verifyEmailKey(#verificationToken)",
          ratePerMethod = true
  )
  public ResponseEntity<Void> verifyUserToken(String verificationToken) {
    userService.verifyEmail(verificationToken);
    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(name = "login-strict",
          cacheKey = "@rateLimitKeyResolver.loginKey(#userLoginRequest)",
          ratePerMethod = true)
  public ResponseEntity<AuthResponse> loginUser(UserLoginRequest userLoginRequest) {
    return ResponseEntity.ok(userService.loginUser(userLoginRequest));
  }

  @Override
  @RateLimiting(
          name = "resend-verification-strict",
          cacheKey = "@rateLimitKeyResolver.registerKey(#userAuthDTO)",
          ratePerMethod = true
  )
  public ResponseEntity<Void> changeUnverifiedEmail(UserRegisterRequest userAuthDTO) {
    userService.changeUnverifiedEmail(userAuthDTO.getName(), userAuthDTO.getPassword(), userAuthDTO.getEmail());
    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(
          name = "resend-verification-strict",
          cacheKey = "@rateLimitKeyResolver.registerKey(#userAuthDTO)",
          ratePerMethod = true
  )
  public ResponseEntity<Void> changeVerifiedEmail(UserRegisterRequest userAuthDTO) {
    userService.changeVerifiedEmail(userAuthDTO.getName(), userAuthDTO.getPassword(), userAuthDTO.getEmail());
    return ResponseEntity.ok().build();
  }


  @Override
  @RateLimiting(
          name = "default-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<User> getCurrentUser() {
    return ResponseEntity.ok(userService.getCurrentUser());
  }
}