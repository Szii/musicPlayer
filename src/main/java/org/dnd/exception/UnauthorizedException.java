package org.dnd.exception;

public class UnauthorizedException extends RuntimeException {

  public UnauthorizedException(String message) {
    super(message);
  }

  public String getCode() {
    return ErrorCode.UNAUTHORIZED.getCode();
  }
}

