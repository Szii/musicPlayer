package org.dnd.configuration.limiting;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dnd.api.model.UserLoginRequest;
import org.dnd.api.model.UserRegisterRequest;
import org.dnd.service.JwtService;
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

  private String normalize(String value) {
    return value == null ? "anonymous" : value.trim().toLowerCase();
  }
}