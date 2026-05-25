package org.dnd.exception;

public enum ErrorCode {
  UNAUTHORIZED("UNAUTHORIZED"),
  FORBIDDEN("FORBIDDEN"),
  CONFLICT("CONFLICT"),
  NOT_FOUND("NOT_FOUND"),
  INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR"),
  BAD_REQUEST("BAD_REQUEST"),
  TOO_MANY_REQUESTS("TOO_MANY_REQUESTS"),
  TOO_MANY_ATTEMPTS("TOO_MANY_ATTEMPTS"),
  LIMIT_EXCEEDED("LIMIT_EXCEEDED"),
  ;

  private final String code;

  ErrorCode(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
