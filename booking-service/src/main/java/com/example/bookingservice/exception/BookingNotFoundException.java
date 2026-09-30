package com.example.bookingservice.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long id) {
        super("Reservation introuvable avec l'id " + id);
    }
}
