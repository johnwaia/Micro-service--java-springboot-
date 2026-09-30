package com.example.bookingservice.exception;

public class FitnessClassNotFoundException extends RuntimeException {

    public FitnessClassNotFoundException(Long classId) {
        super("Cours introuvable avec l'id " + classId);
    }
}
