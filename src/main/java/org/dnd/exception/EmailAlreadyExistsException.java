package org.dnd.exception;

public class EmailAlreadyExistsException extends RuntimeException {
  public EmailAlreadyExistsException(String message) {
    super(message);
  }

  public String getCode() {
    return ErrorCode.EMAIL_ALREADY_EXISTS.getCode();
  }
}
