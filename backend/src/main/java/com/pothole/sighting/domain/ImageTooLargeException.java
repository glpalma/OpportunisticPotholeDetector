package com.pothole.sighting.domain;

public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException(String message) {
        super(message);
    }
}
