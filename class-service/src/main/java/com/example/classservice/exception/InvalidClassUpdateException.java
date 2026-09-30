package com.example.classservice.exception;

public class InvalidClassUpdateException extends RuntimeException {

    public InvalidClassUpdateException(String message) {
        super(message);
    }
}
