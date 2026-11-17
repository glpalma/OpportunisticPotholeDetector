package com.pothole.dashboard.api;

import com.pothole.dashboard.domain.InvalidDashboardQueryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DashboardController.class)
class DashboardExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardExceptionHandler.class);

    @ExceptionHandler(InvalidDashboardQueryException.class)
    ResponseEntity<Void> invalid(InvalidDashboardQueryException ex) {
        log.warn("Rejected dashboard query: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }
}
