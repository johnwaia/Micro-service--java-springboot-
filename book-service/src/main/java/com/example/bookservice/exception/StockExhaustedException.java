package com.example.bookservice.exception;

public class StockExhaustedException extends RuntimeException {

    public StockExhaustedException(Long bookId) {
        super("Aucun exemplaire disponible pour le livre " + bookId);
    }
}
