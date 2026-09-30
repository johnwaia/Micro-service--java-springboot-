package com.example.notificationservice.exception;

public class NotificationAlreadySentException extends RuntimeException {

    public NotificationAlreadySentException(Long id) {
        super("La notification " + id + " a deja ete envoyee");
    }
}
