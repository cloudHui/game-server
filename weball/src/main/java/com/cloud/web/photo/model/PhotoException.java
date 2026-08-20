package com.cloud.web.photo.model;

public class PhotoException extends RuntimeException {
    private final int status;
    public PhotoException(int status, String message) { super(message); this.status = status; }
    public PhotoException(int status, String message, Throwable cause) { super(message, cause); this.status = status; }
    public int getStatus() { return status; }
}
