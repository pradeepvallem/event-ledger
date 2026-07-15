package com.eventledger.account.api;

import com.eventledger.account.api.dto.ApiErrorResponse;
import com.eventledger.account.exception.AccountNotFoundException;
import com.eventledger.account.exception.ConflictingEventException;
import com.eventledger.account.exception.CurrencyMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(
            AccountNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler({
            ConflictingEventException.class,
            CurrencyMismatchException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(
            Exception exception,
            HttpServletRequest request
    ) {
        String message =
                exception instanceof DataIntegrityViolationException
                        ? "The transaction conflicts with existing data"
                        : exception.getMessage();

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                message,
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        validationErrors.putIfAbsent(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                validationErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getConstraintViolations()
                .forEach(violation ->
                        validationErrors.put(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()
                        )
                );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                validationErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Request body is malformed or contains an unsupported value",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity.status(status).body(response);
    }
}