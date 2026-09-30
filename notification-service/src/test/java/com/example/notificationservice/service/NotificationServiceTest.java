package com.example.notificationservice.service;

import com.example.notificationservice.dto.NotificationRequest;
import com.example.notificationservice.exception.NotificationAlreadySentException;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.NotificationStatus;
import com.example.notificationservice.model.NotificationType;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.sender.LoggingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, new LoggingEmailSender(), 3);
        lenient().when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private NotificationRequest request(String email) {
        NotificationRequest request = new NotificationRequest();
        request.setUserId(1L);
        request.setEmail(email);
        request.setType(NotificationType.BOOKING_CONFIRMATION);
        request.setSubject("Reservation en attente");
        request.setContent("Payez avant demain");
        return request;
    }

    @Test
    void send_withValidEmail_isSent() {
        Notification notification = service.send(request("john@example.com"));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getAttempts()).isEqualTo(1);
    }

    @Test
    void send_withInvalidEmail_isFailed() {
        Notification notification = service.send(request("pas-un-email"));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getLastError()).contains("invalide");
    }

    @Test
    void retry_onSentNotification_isRejected() {
        Notification sent = service.send(request("john@example.com"));
        when(repository.findById(1L)).thenReturn(Optional.of(sent));

        assertThrows(NotificationAlreadySentException.class, () -> service.retry(1L));
    }

    @Test
    void retryPending_retriesFailedNotificationsAndCountsAttempts() {
        Notification failed = service.send(request(null));
        when(repository.findByStatusInAndAttemptsLessThanOrderByIdAsc(anyCollection(), eq(3)))
                .thenReturn(List.of(failed));

        int sent = service.retryPending();

        assertThat(sent).isZero();
        assertThat(failed.getAttempts()).isEqualTo(2);
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
}
