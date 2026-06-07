package org.dnd.configuration.limiting;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dnd.api.model.*;
import org.dnd.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component("rateLimitKeyResolver")
@RequiredArgsConstructor
public class RateLimitKeyResolver {

  private static final String ANONYMOUS = "anonymous";

  private final SecurityUtils securityUtils;

  @Value("${rate-limit.key-secret}")
  private String keySecret;

  public String clientIp() {
    HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();

    return "ip:" + request.getRemoteAddr();
  }

  public String currentUserKey() {
    Object currentUserId = securityUtils.getCurrentUserId();

    if (currentUserId == null) {
      return "user:" + ANONYMOUS + ":" + clientIp();
    }

    return "user:" + currentUserId;
  }

  public String loginAccountKey(UserLoginRequest request) {
    return "login:account:" + normalize(request == null ? null : request.getName());
  }

  public String registerSubjectKey(UserRegisterRequest request) {
    if (request == null) {
      return "register:subject:" + ANONYMOUS;
    }

    String subject = firstNonBlank(request.getEmail(), request.getName());

    return "register:subject:" + normalize(subject);
  }

  public String resendVerificationKey(UserLoginRequest request) {
    return "resend-verification:subject:" +
            normalize(request == null ? null : request.getName());
  }

  public String resendVerificationKey(UserRegisterRequest request) {
    if (request == null) {
      return "resend-verification:subject:" + ANONYMOUS;
    }

    String subject = firstNonBlank(request.getEmail(), request.getName());

    return "resend-verification:subject:" + normalize(subject);
  }

  public String verifyEmailTokenKey(String verificationToken) {
    return "verify-email:token:" + tokenFingerprint(verificationToken);
  }

  public String forgotPasswordEmailKey(ForgotPasswordRequest request) {
    return "forgot-password:email:" +
            normalize(request == null ? null : request.getEmail());
  }

  public String changePasswordAccountKey(UserChangePasswordRequest request) {
    return "change-password:account:" +
            normalize(request == null ? null : request.getName());
  }

  public String changePasswordTokenKey(UserChangePasswordWithTokenRequest request) {
    return "change-password:token:" +
            tokenFingerprint(request == null ? null : request.getToken());
  }

  public String streamKey(String streamToken) {
    if (streamToken == null || streamToken.isBlank()) {
      return "stream:anonymous:" + clientIp();
    }

    return "stream:token:" + tokenFingerprint(streamToken);
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return ANONYMOUS;
    }

    return value.trim().toLowerCase();
  }

  private String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }

    if (second != null && !second.isBlank()) {
      return second;
    }

    return ANONYMOUS;
  }

  private String tokenFingerprint(String token) {
    if (token == null || token.isBlank()) {
      return "empty";
    }

    try {
      Mac mac = Mac.getInstance("HmacSHA256");

      SecretKeySpec secretKeySpec = new SecretKeySpec(
              keySecret.getBytes(StandardCharsets.UTF_8),
              "HmacSHA256"
      );

      mac.init(secretKeySpec);

      byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));

      return Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(digest)
              .substring(0, 32);
    } catch (Exception exception) {
      throw new IllegalStateException("Could not create rate-limit token fingerprint", exception);
    }
  }
}