package com.example.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

import java.time.LocalDateTime;

@Entity
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String email;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String subject;

    @Column(length = 2000)
    private String content;

    /** Date de creation, puis date du dernier envoi reussi. */
    private LocalDateTime sentDate;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private int attempts;

    private String lastError;

    protected Notification() {
    }

    public Notification(Long userId, String email, NotificationType type, String subject, String content) {
        this.userId = userId;
        this.email = email;
        this.type = type;
        this.subject = subject;
        this.content = content;
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
    }

    @PrePersist
    void onCreate() {
        if (sentDate == null) {
            sentDate = LocalDateTime.now();
        }
    }

    public void markSent(LocalDateTime date) {
        this.attempts++;
        this.status = NotificationStatus.SENT;
        this.sentDate = date;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.status = NotificationStatus.FAILED;
        this.lastError = error;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public NotificationType getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getSentDate() {
        return sentDate;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
