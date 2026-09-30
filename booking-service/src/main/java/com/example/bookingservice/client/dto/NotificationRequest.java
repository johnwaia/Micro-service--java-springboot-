package com.example.bookingservice.client.dto;

public record NotificationRequest(
        Long userId,
        String email,
        NotificationType type,
        String subject,
        String content) {

    public enum NotificationType {
        BOOKING_CONFIRMATION,
        PAYMENT_CONFIRMATION,
        BOOKING_REMINDER,
        BOOKING_CANCELLED,
        CLASS_CANCELLED
    }
}
