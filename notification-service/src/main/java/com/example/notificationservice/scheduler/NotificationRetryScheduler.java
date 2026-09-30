package com.example.notificationservice.scheduler;

import com.example.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "notification.retry.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationService service;

    public NotificationRetryScheduler(NotificationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${notification.retry.rate:PT2M}", initialDelayString = "${notification.retry.rate:PT2M}")
    public void retryPendingNotifications() {
        int sent = service.retryPending();
        if (sent > 0) {
            log.info("{} notification(s) en attente envoyee(s) apres relance", sent);
        }
    }
}
