package org.dnd.security;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class RegistrationTokenService {

  private final SecureRandom secureRandom = new SecureRandom();

  public String generateToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);

    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
  }
}
