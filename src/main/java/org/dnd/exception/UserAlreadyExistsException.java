package org.dnd.exception;

public class UserAlreadyExistsException extends RuntimeException {
  public UserAlreadyExistsException(String message) {
    super(message);
  }

  public String getCode() {
    return ErrorCode.USER_ALREADY_EXISTS.getCode();
  }
}
