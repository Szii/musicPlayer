package org.dnd.exception;

import lombok.Getter;

@Getter
public class LoginThrottledException extends RuntimeException {
  private final long retryAfterSeconds;

  public LoginThrottledException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

}
