package com.example.bookingservice.service;

import com.example.bookingservice.client.NotificationClient;
import com.example.bookingservice.client.dto.NotificationDto;
import com.example.bookingservice.client.dto.NotificationRequest;
import com.example.bookingservice.client.dto.NotificationRequest.NotificationType;
import com.example.bookingservice.model.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Construit et envoie les notifications du saga. L'envoi est "best effort" : un echec est journalise
 * mais ne remet jamais en cause la reservation (notification-service rejoue les envois en echec).
 */
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private final NotificationClient notificationClient;

    public BookingNotifier(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    public boolean bookingPending(Booking b) {
        return send(b, NotificationType.BOOKING_CONFIRMATION,
                "FitConnect - Reservation " + b.getBookingReference() + " en attente de paiement",
                "Bonjour " + b.getUserName() + ",\n"
                        + "Votre reservation est en attente de paiement. Payez avant " + FORMAT.format(b.getPaymentDeadline()) + ".\n"
                        + "Cours : " + b.getClassName() + " avec " + b.getInstructor() + " le " + FORMAT.format(b.getClassDate()) + "\n"
                        + "Places : " + b.getNumberOfSpots() + " - Montant : " + b.getTotalAmount() + " EUR");
    }

    public boolean paymentConfirmed(Booking b) {
        return send(b, NotificationType.PAYMENT_CONFIRMATION,
                "FitConnect - Paiement recu, reservation " + b.getBookingReference() + " confirmee",
                "Bonjour " + b.getUserName() + ",\n"
                        + "Votre paiement de " + b.getTotalAmount() + " EUR (" + b.getPaymentReference() + ") est confirme.\n"
                        + "Rendez-vous le " + FORMAT.format(b.getClassDate()) + " pour " + b.getClassName() + ".\n"
                        + "Annulation gratuite jusqu'au " + FORMAT.format(b.getCancellationDeadline()) + ".");
    }

    public boolean bookingCancelled(Booking b, String details) {
        return send(b, NotificationType.BOOKING_CANCELLED,
                "FitConnect - Reservation " + b.getBookingReference() + " annulee",
                "Bonjour " + b.getUserName() + ",\n"
                        + "Votre reservation pour " + b.getClassName() + " du " + FORMAT.format(b.getClassDate())
                        + " a ete annulee.\n" + details);
    }

    public boolean reminder(Booking b) {
        return send(b, NotificationType.BOOKING_REMINDER,
                "FitConnect - Rappel : " + b.getClassName() + " demain",
                "Bonjour " + b.getUserName() + ",\n"
                        + "Petit rappel : votre cours " + b.getClassName() + " avec " + b.getInstructor()
                        + " a lieu le " + FORMAT.format(b.getClassDate()) + " (" + b.getNumberOfSpots() + " place(s)).");
    }

    private boolean send(Booking b, NotificationType type, String subject, String content) {
        try {
            NotificationDto result = notificationClient.send(
                    new NotificationRequest(b.getUserId(), b.getUserEmail(), type, subject, content));
            return result != null;
        } catch (RuntimeException ex) {
            log.warn("Notification {} non envoyee pour la reservation {} : {}", type, b.getBookingReference(),
                    ex.getMessage());
            return false;
        }
    }
}
