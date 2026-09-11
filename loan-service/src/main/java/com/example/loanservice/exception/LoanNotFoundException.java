package com.example.loanservice.exception;

public class LoanNotFoundException extends RuntimeException {

    public LoanNotFoundException(Long id) {
        super("Emprunt introuvable avec l'id " + id);
    }
}
