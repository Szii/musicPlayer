package org.dnd.exception;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflictException(ConflictException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );

    return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(response);
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(EmailNotVerifiedException.class)
  public ResponseEntity<ErrorResponse> handleEmailNotVerified(EmailNotVerifiedException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
  }

  @ExceptionHandler(RateLimitException.class)
  public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException e) {
    ErrorResponse response = new ErrorResponse(
            "TOO_MANY_REQUESTS",
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
  }

  @ExceptionHandler(LoginThrottledException.class)
  public ResponseEntity<ErrorResponse> handleLoginThrottled(LoginThrottledException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
            .body(response);
  }

  @ExceptionHandler(LimitReachedException.class)
  public ResponseEntity<ErrorResponse> handleLoginThrottled(LimitReachedException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(response);
  }

  @ExceptionHandler(UserAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleUserExistsException(UserAlreadyExistsException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  @ExceptionHandler(EmailAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleUserExistsException(EmailAlreadyExistsException e) {
    ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage()
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }
}

