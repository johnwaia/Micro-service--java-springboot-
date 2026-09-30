package com.example.notificationservice.sender;

import com.example.notificationservice.model.Notification;

public interface NotificationSender {

    /**
     * Envoie la notification.
     *
     * @throws NotificationDeliveryException si l'envoi echoue
     */
    void send(Notification notification);
}
