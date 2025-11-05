package com.springqprobackend.springqpro.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.apache.catalina.connector.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice   // NOTE: applies across all controllers (like a global try-catch).
public class GlobalExceptionHandler {
    // INVALID ENUM HANDLER: (e.g., invalid TaskType in JSON payload).
    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<?> handleInvalidEnum(InvalidFormatException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid task type provided.",
                "details", ex.getValue().toString()
        ));
    }

    // VALIDATION ERROR HANDLER (from Bean Annotation in Task.java): (e.g., violations of @NotNull, @NotBlank, and so on).
    @ExceptionHandler(MethodArgumentNotValidException.class)    // triggered automatically for any @Valid @RequestBody validation failure.
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        // NOTE-TO-SELF:+TO-DO: Do further study on what's going on here later...
        Map<String, String> errors = new HashMap<>();
        for(FieldError err: ex.getBindingResult().getFieldErrors()) {
            errors.put(err.getField(), err.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(errors);
    }

    // FALLBACK HANDLER: For unexpected exceptions:
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericErrors(Exception ex) {
        // NOTE-TO-SELF:+TO-DO: Remember to take a written comprehensive note on ResponseEntity (master it).
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Unexpected server error.",
                "details", ex.getMessage()
        ));
    }
}
