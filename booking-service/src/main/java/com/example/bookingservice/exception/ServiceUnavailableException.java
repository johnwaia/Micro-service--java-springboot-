package com.example.bookingservice.exception;

/**
 * Service distant injoignable, trop lent ou circuit ouvert : l'etape du saga n'a pas pu etre executee.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
