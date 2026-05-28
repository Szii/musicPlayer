package org.dnd.configuration.limiting;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dnd.api.model.*;
import org.dnd.security.JwtService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component("rateLimitKeyResolver")
@RequiredArgsConstructor
public class RateLimitKeyResolver {

  private final JwtService jwtService;

  public String loginKey(UserLoginRequest request) {
    return clientIp() + ":" + normalize(request.getName());
  }

  public String registerKey(UserRegisterRequest request) {
    return clientIp() + ":" + normalize(request.getName());
  }

  public String currentUserKey() {
    return String.valueOf(SecurityUtils.getCurrentUserId());
  }

  public String streamKey(String streamToken) {
    return jwtService.getUserIdFromToken(streamToken);
  }

  public String clientIp() {
    HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();

    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  public String verifyEmailKey(String verificationToken) {
    return "verify-email:" + clientIp() + ":" + verificationToken;
  }


  public String resendVerificationKey(UserChangePasswordWithTokenRequest request) {
    return "resend-verification:" + clientIp() + ":" +
            normalize(request == null ? null : request.getToken());
  }

  public String resendVerificationKey(UserRegisterRequest request) {
    return "resend-verification:" + clientIp() + ":" +
            normalize(request == null ? null : request.getName());
  }

  public String resendVerificationKey(UserLoginRequest request) {
    return "resend-verification:" + clientIp() + ":" +
            normalize(request == null ? null : request.getName());
  }

  public String resendPasswordVerification(ForgotPasswordRequest request) {
    return "forgot-password:" + clientIp() + ":" +
            normalize(request == null ? null : request.getEmail());
  }

  public String changeUserPassword(UserChangePasswordRequest request) {
    return "forgot-password:" + clientIp() + ":" +
            normalize(request == null ? null : request.getName());
  }

  public String changeUserPasswordWithToken(UserChangePasswordWithTokenRequest request) {
    return "forgot-password:" + clientIp() + ":" +
            normalize(request == null ? null : request.getToken());
  }

  private String normalize(String value) {
    return value == null ? "anonymous" : value.trim().toLowerCase();
  }
}