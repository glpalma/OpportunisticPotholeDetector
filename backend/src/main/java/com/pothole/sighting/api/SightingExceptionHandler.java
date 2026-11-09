package com.pothole.sighting.api;

import com.pothole.sighting.domain.ImageTooLargeException;
import com.pothole.sighting.domain.InvalidSightingException;
import com.pothole.sighting.domain.UnsupportedImageException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class SightingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SightingExceptionHandler.class);

    @ExceptionHandler(InvalidSightingException.class)
    ResponseEntity<Void> invalid(InvalidSightingException ex) {
        log.warn("Rejected sighting metadata: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(UnsupportedImageException.class)
    ResponseEntity<Void> unsupported(UnsupportedImageException ex) {
        log.warn("Rejected sighting image: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
    }

    @ExceptionHandler({ImageTooLargeException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<Void> tooLarge(Exception ex) {
        log.warn("Rejected oversized sighting image: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
    }
}
