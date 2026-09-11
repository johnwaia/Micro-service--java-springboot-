package com.example.loanservice.exception;

public class BookUnavailableException extends RuntimeException {

    public BookUnavailableException(Long bookId) {
        super("Aucun exemplaire disponible pour ce livre (id " + bookId + ")");
    }
}
