package org.dnd.exception;

public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }

  public String getCode() {
    return ErrorCode.CONFLICT.getCode();
  }
}

