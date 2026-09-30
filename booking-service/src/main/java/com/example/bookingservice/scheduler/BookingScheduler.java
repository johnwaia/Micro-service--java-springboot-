package com.example.bookingservice.scheduler;

import com.example.bookingservice.model.Booking;
import com.example.bookingservice.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Taches planifiees de booking-service (declenchees seulement si booking.scheduler.enabled=true,
 * cf. SchedulingConfig) ; les methodes restent appelables directement, notamment par les tests.
 */
@Component
public class BookingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduler.class);

    private final BookingService bookingService;

    public BookingScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Toutes les 5 minutes : annule les reservations PENDING_PAYMENT dont paymentDeadline est depasse. */
    @Scheduled(fixedRateString = "${booking.scheduler.expiration-rate:PT5M}",
            initialDelayString = "${booking.scheduler.initial-delay:PT30S}")
    public int cancelExpiredBookings() {
        List<Booking> expired = bookingService.findExpiredPendingBookings();
        int cancelled = 0;
        for (Booking booking : expired) {
            try {
                if (bookingService.expireBooking(booking)) {
                    cancelled++;
                }
            } catch (RuntimeException ex) {
                log.warn("Expiration de la reservation {} reportee au prochain passage : {}",
                        booking.getBookingReference(), ex.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("Scheduler expiration : {} reservation(s) expiree(s), {} annulee(s)", expired.size(), cancelled);
        }
        return cancelled;
    }

    /** Rappel envoye une fois pour chaque cours confirme ayant lieu dans les prochaines 24h. */
    @Scheduled(fixedRateString = "${booking.scheduler.reminder-rate:PT15M}",
            initialDelayString = "${booking.scheduler.initial-delay:PT30S}")
    public int sendClassReminders() {
        int sent = bookingService.sendReminders();
        if (sent > 0) {
            log.info("Scheduler rappels : {} rappel(s) envoye(s)", sent);
        }
        return sent;
    }
}
