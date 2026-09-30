package com.example.classservice.exception;

public class FitnessClassNotFoundException extends RuntimeException {

    public FitnessClassNotFoundException(Long id) {
        super("Cours introuvable avec l'id " + id);
    }
}
