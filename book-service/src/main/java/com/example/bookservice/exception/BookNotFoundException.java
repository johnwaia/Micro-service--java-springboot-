package com.example.bookservice.exception;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long id) {
        super("Livre introuvable avec l'id " + id);
    }
}
