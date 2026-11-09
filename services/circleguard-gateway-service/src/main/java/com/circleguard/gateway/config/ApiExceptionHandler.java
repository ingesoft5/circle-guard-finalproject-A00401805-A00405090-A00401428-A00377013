/**
 * CG-DOC: configuration for the gateway service: Api Exception Handler.
 */
package com.circleguard.gateway.config;

import com.circleguard.gateway.service.QrValidationService.ValidationResult;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ValidationResult> redisUnavailable(DataAccessException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ValidationResult(false, "RED", "Service Unavailable: Health Status Unavailable"));
    }
}
