package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.ProjectionNotReadyResponse;
import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidPageSizeException;
import com.sanmoo.eventsourcing.creditaccount.core.error.ProjectionNotReadyException;
import com.sanmoo.eventsourcing.creditaccount.core.error.SummaryNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountAlreadyExistsException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(AccountAlreadyExistsException.class)
  public ResponseEntity<Map<String, String>> handleAccountAlreadyExists(AccountAlreadyExistsException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(ConcurrencyConflictException.class)
  public ResponseEntity<Map<String, String>> handleConcurrencyConflict(ConcurrencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(AccountNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleAccountNotFound(AccountNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(SummaryNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleSummaryNotFound(SummaryNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(ProjectionNotReadyException.class)
  public ResponseEntity<ProjectionNotReadyResponse> handleProjectionNotReady(ProjectionNotReadyException ex) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new ProjectionNotReadyResponse(
                ex.getMessage(),
                ex.getCreditAccountId(),
                ex.getCurrentProjectionVersion(),
                ex.getRequiredVersion()
        ));
  }

  @ExceptionHandler(InvalidPageSizeException.class)
  public ResponseEntity<Map<String, String>> handleInvalidPageSize(InvalidPageSizeException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<Map<String, String>> handleDomainException(DomainException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
        .body(Map.of("error", ex.getMessage()));
  }
}
