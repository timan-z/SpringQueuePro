package com.springqprobackend.springqpro.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.apache.catalina.connector.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/* 2025-11-25-NOTE(S):
- EXPANDING THIS TO HANDLE INVALID CREDENTIALS AND JWT-RELATED ERRORS ENCOUNTERED IN AuthenticationController.java
- OVERHAULING EXISTING METHODS TO MAKE RESPONSES MORE BROAD AND GENERAL AND NOT SO SPECIFIC LIKE THEY ORIGINALLY WERE TO THE EARLIER PHASES OF THIS PROJECT.
*/
/* IMPORTANT FUTURE REFINEMENT TO-DO:
JWT errors thrown inside JwtAuthenticationFilter are just caught in the filter and logged, they won't hit this handler.
My security rules just treat the request as anonymous and Spring Security itself returns a basic 401.
Later, maybe add a custom AuthenticationEntryPoint so we can have full JSON control over the 401 from the filter...
CURRENTLY RUSHING TO GET A MVP OUT AND READY FOR DEPLOYMENT AND DSIPLAY!!!
*/
@RestControllerAdvice   // NOTE: applies across all controllers (like a global try-catch).
public class GlobalExceptionHandler {
    // RETURN BODY TEMPLATE:
    private Map<String, Object> baseBody(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("error", message);
        return body;
    }
    // 2025-11-25-NOTE: MY OLD HANDLERS (FROM PRE-JWT REFACTORING, NOW REFACTORED AS PART OF THE JWT OVERHAUL):
    // INVALID ENUM HANDLER: (e.g., invalid TaskType in JSON payload).
    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidEnum(InvalidFormatException ex) {
        Map<String, Object> body = baseBody("Invalid value in the request body");
        body.put("details", ex.getValue() != null ? ex.getValue().toString() : null);
        // Special-case: enums (e.g., invalid TaskType / TaskStatus)
        if (ex.getTargetType() != null && ex.getTargetType().isEnum()) {
            body.put("hint", "Check your enum value (TaskType / TaskStatus, etc.)");    // NOTE: Old message was specifically about invalid Type/Status etc.
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // VALIDATION ERROR HANDLER (from Bean Annotation in Task.java): (e.g., violations of @NotNull, @NotBlank, and so on).
    @ExceptionHandler(MethodArgumentNotValidException.class)    // triggered automatically for any @Valid @RequestBody validation failure.
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, Object> body = baseBody("Validation failed");
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("fields", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // FALLBACK HANDLER: For unexpected exceptions:
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericErrors(Exception ex) {
        Map<String, Object> body = baseBody("Unexpected server error");
        body.put("details", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // 2025-11-25-NOTE:+DEBUG: JWT-RELATED ERRORS HANDLED BELOW:
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        Map<String, Object> body = baseBody("Invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredJwt(ExpiredJwtException ex) {
        Map<String, Object> body = baseBody("JWT token expired");
        body.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Map<String, Object>> handleJwt(JwtException ex) {
        Map<String, Object> body = baseBody("Invalid JWT token");
        body.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, Object> body = baseBody(ex.getReason() != null ? ex.getReason() : "Request failed");
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}
