package com.pothole.sighting.domain;

public class InvalidSightingException extends RuntimeException {

    public InvalidSightingException(String message) {
        super(message);
    }
}
