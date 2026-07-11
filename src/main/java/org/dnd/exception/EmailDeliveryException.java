package org.dnd.exception;

public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }

  public String getCode() {
    return ErrorCode.EMAIL_DELIVERY_FAILED.getCode();
  }
}
