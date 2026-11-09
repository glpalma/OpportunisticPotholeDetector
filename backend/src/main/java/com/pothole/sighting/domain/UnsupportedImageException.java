package com.pothole.sighting.domain;

public class UnsupportedImageException extends RuntimeException {

    public UnsupportedImageException(String message) {
        super(message);
    }
}
