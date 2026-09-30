package com.example.notificationservice.service;

import com.example.notificationservice.dto.NotificationRequest;
import com.example.notificationservice.exception.NotificationAlreadySentException;
import com.example.notificationservice.exception.NotificationNotFoundException;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.NotificationStatus;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.sender.NotificationDeliveryException;
import com.example.notificationservice.sender.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final EnumSet<NotificationStatus> NOT_SENT = EnumSet.of(NotificationStatus.PENDING, NotificationStatus.FAILED);

    private final NotificationRepository repository;
    private final NotificationSender sender;
    private final int maxAttempts;

    public NotificationService(NotificationRepository repository, NotificationSender sender,
                               @Value("${notification.retry.max-attempts:3}") int maxAttempts) {
        this.repository = repository;
        this.sender = sender;
        this.maxAttempts = maxAttempts;
    }

    /** Enregistre la notification (PENDING) puis tente immediatement l'envoi (SENT ou FAILED). */
    public Notification send(NotificationRequest request) {
        Notification notification = repository.save(new Notification(request.getUserId(), request.getEmail(),
                request.getType(), request.getSubject(), request.getContent()));
        return deliver(notification);
    }

    public List<Notification> findByUserId(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId);
    }

    /** Notifications pas encore envoyees avec succes (PENDING ou FAILED). */
    public List<Notification> findPending() {
        return repository.findByStatusInOrderByIdAsc(NOT_SENT);
    }

    public Notification findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    public Notification retry(Long id) {
        Notification notification = findById(id);
        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new NotificationAlreadySentException(id);
        }
        return deliver(notification);
    }

    /** Relance automatique (scheduler) des notifications non envoyees, dans la limite de maxAttempts. */
    public int retryPending() {
        List<Notification> retryable = repository.findByStatusInAndAttemptsLessThanOrderByIdAsc(NOT_SENT, maxAttempts);
        int sent = 0;
        for (Notification notification : retryable) {
            if (deliver(notification).getStatus() == NotificationStatus.SENT) {
                sent++;
            }
        }
        return sent;
    }

    private Notification deliver(Notification notification) {
        try {
            sender.send(notification);
            notification.markSent(LocalDateTime.now());
        } catch (NotificationDeliveryException ex) {
            log.warn("Echec d'envoi de la notification {} : {}", notification.getId(), ex.getMessage());
            notification.markFailed(ex.getMessage());
        }
        return repository.save(notification);
    }
}
