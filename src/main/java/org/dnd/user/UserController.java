package org.dnd.user;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dnd.api.UsersApi;
import org.dnd.api.model.*;
import org.dnd.security.AuthenticationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.dnd.configuration.limiting.RateLimitNames.*;

@RequestMapping("/api/v1")
@Tag(name = "Users", description = "User authentication and profile operations")
@RestController
@Validated
@RequiredArgsConstructor
public class UserController implements UsersApi {

  @Value("${app.email-use}")
  private boolean emailUse;

  private final UserService userService;
  private final UserAuthService userAuthService;

  @Override
  @RateLimiting(
          name = REGISTER_SUBJECT,
          cacheKey = REGISTER_SUBJECT_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<User> registerUser(UserRegisterRequest userRegisterRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(userAuthService.registerUser(userRegisterRequest));
  }

  @Override
  @RateLimiting(
          name = LOGIN_ACCOUNT,
          cacheKey = LOGIN_ACCOUNT_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<AuthResponse> loginUser(UserLoginRequest userLoginRequest) {
    AuthenticationResult result = userAuthService.loginUser(userLoginRequest);

    return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, userAuthService.createRefreshCookie(result.refreshToken()).toString())
            .body(result.authResponse());
  }

  @Override
  public ResponseEntity<Void> logoutUser(String refreshToken) {
    ResponseCookie clearCookie = userAuthService.logoutUser(refreshToken);

    return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
            .build();
  }

  @Override
  public ResponseEntity<AuthResponse> refreshUserToken(String refreshToken) {
    AuthenticationResult result = userAuthService.refreshUserToken(refreshToken);

    return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, userAuthService.createRefreshCookie(result.refreshToken()).toString())
            .body(result.authResponse());
  }

  @Override
  @RateLimiting(
          name = VERIFY_EMAIL_TOKEN,
          cacheKey = VERIFY_EMAIL_TOKEN_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Void> verifyUserToken(String verificationToken) {
    if (!emailUse) {
      return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    userAuthService.verifyEmail(verificationToken);
    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(
          name = RESEND_VERIFICATION_SUBJECT,
          cacheKey = RESEND_VERIFICATION_LOGIN_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Void> resendVerificationEmail(UserLoginRequest request) throws Exception {
    if (!emailUse) {
      return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    userService.resendVerificationEmailToSameEmail(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(
          name = PASSWORD_TOKEN,
          cacheKey = PASSWORD_TOKEN_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Void> changeUnverifiedPassword(
          UserChangePasswordWithTokenRequest userChangePasswordWithTokenRequest
  ) {
    if (!emailUse) {
      return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    userAuthService.changePasswordByToken(userChangePasswordWithTokenRequest);
    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(
          name = CHANGE_PASSWORD_ACCOUNT,
          cacheKey = CHANGE_PASSWORD_ACCOUNT_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Void> changeVerifiedPassword(
          UserChangePasswordRequest userChangePasswordRequest
  ) {
    userAuthService.changePasswordByAuth(userChangePasswordRequest);
    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(
          name = FORGOT_PASSWORD_EMAIL,
          cacheKey = FORGOT_PASSWORD_EMAIL_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Void> forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
    userService.sendChangePasswordEmail(forgotPasswordRequest.getEmail());
    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(
          name = RESEND_VERIFICATION_SUBJECT,
          cacheKey = CHANGE_EMAIL_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Void> changeVerifiedEmail(ChangeEmailRequest changeEmailRequest) {
    if (!emailUse) {
      return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    userAuthService.changeEmailByAuth(changeEmailRequest);

    return ResponseEntity.ok().build();
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<User> getCurrentUser() {
    return ResponseEntity.ok(userService.getCurrentUser());
  }

  @Override
  @RateLimiting(
          name = CHANGE_USERNAME,
          cacheKey = CHANGE_USERNAME_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<User> changeUsername(ChangeUsernameRequest changeUsernameRequest) {
    return ResponseEntity.ok(userAuthService.changeUsername(changeUsernameRequest.getName()));
  }
}