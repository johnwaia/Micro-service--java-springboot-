package com.example.notificationservice.dto;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.NotificationStatus;
import com.example.notificationservice.model.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long userId,
        String email,
        NotificationType type,
        String subject,
        String content,
        LocalDateTime sentDate,
        NotificationStatus status,
        int attempts,
        String lastError) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getEmail(), n.getType(), n.getSubject(),
                n.getContent(), n.getSentDate(), n.getStatus(), n.getAttempts(), n.getLastError());
    }
}
