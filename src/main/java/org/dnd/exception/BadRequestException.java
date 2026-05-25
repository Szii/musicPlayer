package org.dnd.exception;

public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }

  public String getCode() {
    return ErrorCode.BAD_REQUEST.getCode();
  }
}