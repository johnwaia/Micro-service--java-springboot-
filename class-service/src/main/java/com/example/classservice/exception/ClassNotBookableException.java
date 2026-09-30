package com.example.classservice.exception;

public class ClassNotBookableException extends RuntimeException {

    public ClassNotBookableException(String message) {
        super(message);
    }
}
