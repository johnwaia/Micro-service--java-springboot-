package com.example.bookingservice.support;

import com.example.bookingservice.client.NotificationClient;
import com.example.bookingservice.client.dto.NotificationDto;
import com.example.bookingservice.client.dto.NotificationRequest;
import com.example.bookingservice.client.dto.NotificationRequest.NotificationType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** notification-service en memoire : memorise les notifications envoyees. */
public class FakeNotificationClient implements NotificationClient {

    private final List<NotificationRequest> sent = new CopyOnWriteArrayList<>();

    @Override
    public NotificationDto send(NotificationRequest request) {
        sent.add(request);
        return new NotificationDto((long) sent.size(), "SENT");
    }

    public List<NotificationRequest> sent() {
        return List.copyOf(sent);
    }

    public List<NotificationType> typesSentTo(Long userId) {
        return sent.stream().filter(n -> n.userId().equals(userId)).map(NotificationRequest::type).toList();
    }

    public void reset() {
        sent.clear();
    }
}
