package com.example.bookingservice.exception;

/**
 * Erreur fonctionnelle (4xx) renvoyee par un service distant. On conserve le statut et le message
 * pour que booking-service puisse la traduire (ex : 409 de class-service -> "plus de places").
 */
public class RemoteServiceException extends RuntimeException {

    private final String serviceName;
    private final int status;

    public RemoteServiceException(String serviceName, int status, String message) {
        super(message);
        this.serviceName = serviceName;
        this.status = status;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatus() {
        return status;
    }
}
