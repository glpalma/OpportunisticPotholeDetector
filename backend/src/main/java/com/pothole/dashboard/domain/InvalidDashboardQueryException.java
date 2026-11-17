package com.pothole.dashboard.domain;

public class InvalidDashboardQueryException extends RuntimeException {

    public InvalidDashboardQueryException(String message) {
        super(message);
    }
}
