package org.dnd.exception;

public class EmailNotVerifiedException extends RuntimeException {

  public EmailNotVerifiedException(String message) {
    super(message);
  }

  public String getCode() {
    return ErrorCode.EMAIL_NOT_VERIFIED.getCode();
  }
}


