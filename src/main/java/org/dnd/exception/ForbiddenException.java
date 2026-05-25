package org.dnd.exception;

public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }

  public String getCode() {
    return ErrorCode.FORBIDDEN.getCode();
  }
}

