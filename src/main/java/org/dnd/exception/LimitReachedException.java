package org.dnd.exception;

public class LimitReachedException extends RuntimeException {

  public LimitReachedException(String message) {
    super(message);
  }

  public String getCode() {
    return ErrorCode.LIMIT_EXCEEDED.getCode();
  }
}
