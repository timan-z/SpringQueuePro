package com.springqprobackend.springqpro.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<?> handleInvalidEnum(InvalidFormatException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid task type provided.",
                "details", ex.getValue().toString()
        ));
    }
}
