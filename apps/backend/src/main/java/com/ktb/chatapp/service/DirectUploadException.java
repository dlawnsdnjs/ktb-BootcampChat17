package com.ktb.chatapp.service;

import org.springframework.http.HttpStatus;

public class DirectUploadException extends RuntimeException {
    private final HttpStatus status;

    public DirectUploadException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
