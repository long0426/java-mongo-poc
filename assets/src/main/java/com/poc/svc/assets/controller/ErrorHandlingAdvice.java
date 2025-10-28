package com.poc.svc.assets.controller;

import com.poc.svc.assets.util.TraceContext;
import com.poc.svc.assets.dto.ErrorResponse;
import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.exception.AssetAggregationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@RestControllerAdvice
public class ErrorHandlingAdvice {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlingAdvice.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = extractCode(ex.getReason(), status);
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(code, ex.getReason(), Map.of(), TraceContext.traceId()));
    }

    @ExceptionHandler({IllegalArgumentException.class, BindException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleValidationExceptions(Exception ex) {
        Map<String, Object> details = Map.of();
        if (ex instanceof ConstraintViolationException constraintViolationException) {
            details = Map.of("violations", mapViolations(constraintViolationException.getConstraintViolations()));
        } else if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            details = Map.of("violations", methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> Map.of(
                            "field", error.getField(),
                            "message", error.getDefaultMessage()))
                    .toList());
        } else if (ex instanceof BindException bindException) {
            details = Map.of("violations", bindException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> Map.of(
                            "field", error.getField(),
                            "message", error.getDefaultMessage()))
                    .toList());
        }
        ErrorResponse response = ErrorResponse.of(
                "BAD_REQUEST",
                ex.getMessage(),
                details,
                TraceContext.traceId()
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AssetAggregationException.class)
    public ResponseEntity<ErrorResponse> handleAssetAggregationException(AssetAggregationException ex) {
        Map<String, Object> details = Map.of(
                "failedSources", ex.failedSources().stream().map(AssetSourceType::name).toList()
        );
        ErrorResponse response = ErrorResponse.of(
                "ASSET_AGGREGATION_FAILED",
                ex.getMessage(),
                details,
                TraceContext.traceId()
        );
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException ex) {
        log.error("MongoDB operation failed", ex);
        ErrorResponse response = ErrorResponse.of(
                "DATA_ACCESS_ERROR",
                "資料存取發生錯誤",
                Map.of("error", ex.getMostSpecificCause().getMessage()),
                TraceContext.traceId()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse response = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "系統發生未預期錯誤",
                Map.of("error", ex.getMessage()),
                TraceContext.traceId()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private List<Map<String, String>> mapViolations(Iterable<ConstraintViolation<?>> violations) {
        return violStream(violations)
                .map(violation -> Map.of(
                        "property", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .collect(Collectors.toList());
    }

    private Stream<ConstraintViolation<?>> violStream(Iterable<ConstraintViolation<?>> violations) {
        return violations == null
                ? Stream.empty()
                : StreamSupport.stream(violations.spliterator(), false);
    }

    private String extractCode(String reason, HttpStatus status) {
        return reason != null && !reason.isBlank()
                ? reason.replace(' ', '_').toUpperCase()
                : status.name();
    }
}
