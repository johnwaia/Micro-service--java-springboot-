package com.example.notificationservice.sender;

import com.example.notificationservice.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulation d'un fournisseur email/SMS : l'envoi est trace dans les logs.
 * Il echoue si l'adresse du destinataire est absente ou invalide, ce qui permet de tester les relances.
 */
@Component
public class LoggingEmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(Notification notification) {
        String email = notification.getEmail();
        if (email == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new NotificationDeliveryException("Adresse email invalide ou absente : " + email);
        }
        log.info("[EMAIL] to={} type={} subject=\"{}\"\n{}", email, notification.getType(),
                notification.getSubject(), notification.getContent());
    }
}
