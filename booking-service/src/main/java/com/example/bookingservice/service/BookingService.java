package com.example.bookingservice.service;

import com.example.bookingservice.client.ClassClient;
import com.example.bookingservice.client.PaymentClient;
import com.example.bookingservice.client.dto.FitnessClassDto;
import com.example.bookingservice.client.dto.PaymentDto;
import com.example.bookingservice.client.dto.PaymentRequest;
import com.example.bookingservice.dto.BookingRequest;
import com.example.bookingservice.dto.ConfirmBookingRequest;
import com.example.bookingservice.exception.BookingNotFoundException;
import com.example.bookingservice.exception.CancellationNotAllowedException;
import com.example.bookingservice.exception.ClassNotBookableException;
import com.example.bookingservice.exception.FitnessClassNotFoundException;
import com.example.bookingservice.exception.InvalidBookingStateException;
import com.example.bookingservice.exception.NoSpotsAvailableException;
import com.example.bookingservice.exception.PaymentExpiredException;
import com.example.bookingservice.exception.PaymentFailedException;
import com.example.bookingservice.exception.RemoteServiceException;
import com.example.bookingservice.model.Booking;
import com.example.bookingservice.model.BookingStatus;
import com.example.bookingservice.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

/**
 * Orchestrateur du saga de reservation (booking-service -> class-service / payment-service / notification-service).
 *
 * <p>Les methodes ne sont volontairement pas @Transactional : chaque etape du saga est locale a un service,
 * et une transaction JPA ne peut pas "annuler" un appel HTTP deja effectue. Les echecs sont geres par
 * des actions de compensation explicites (liberer les places, re-reserver, etc.).</p>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final EnumSet<BookingStatus> CLOSED = EnumSet.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED,
            BookingStatus.NO_SHOW);

    private final BookingRepository repository;
    private final ClassClient classClient;
    private final PaymentClient paymentClient;
    private final BookingNotifier notifier;
    private final BookingReferenceGenerator referenceGenerator;
    private final Clock clock;
    private final Duration paymentTimeout;
    private final Duration cancellationNotice;

    public BookingService(BookingRepository repository, ClassClient classClient, PaymentClient paymentClient,
                          BookingNotifier notifier, BookingReferenceGenerator referenceGenerator, Clock clock,
                          @Value("${booking.payment-timeout:PT1H}") Duration paymentTimeout,
                          @Value("${booking.cancellation-notice:PT24H}") Duration cancellationNotice) {
        this.repository = repository;
        this.classClient = classClient;
        this.paymentClient = paymentClient;
        this.notifier = notifier;
        this.referenceGenerator = referenceGenerator;
        this.clock = clock;
        this.paymentTimeout = paymentTimeout;
        this.cancellationNotice = cancellationNotice;
    }

    // ------------------------------------------------------------------ lecture

    public List<Booking> findAll(BookingStatus status) {
        return status == null ? repository.findAll() : repository.findByStatus(status);
    }

    public Booking findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    public List<Booking> findByUserId(Long userId) {
        return repository.findByUserIdOrderByBookingDateDesc(userId);
    }

    public List<Booking> findExpiredPendingBookings() {
        return repository.findByStatusAndPaymentDeadlineBefore(BookingStatus.PENDING_PAYMENT, now());
    }

    // ------------------------------------------------------------------ Cas 1 & 2 : reservation

    public Booking createBooking(BookingRequest request) {
        int spots = request.getNumberOfSpots();

        // Etape 1 : verification du cours + snapshot
        FitnessClassDto fitnessClass = fetchClass(request.getClassId());
        if (!FitnessClassDto.SCHEDULED.equals(fitnessClass.status())) {
            throw new ClassNotBookableException("Le cours " + fitnessClass.id() + " n'est pas ouvert aux reservations (statut "
                    + fitnessClass.status() + ")");
        }
        LocalDateTime now = now();
        if (!fitnessClass.dateTime().isAfter(now)) {
            throw new ClassNotBookableException("Le cours " + fitnessClass.id() + " a deja commence ou est termine");
        }
        if (fitnessClass.availableSpots() < spots) {
            throw noSpots(fitnessClass.id());
        }

        // Etape 2 : reservation des places (verrouillage optimiste cote class-service)
        try {
            classClient.incrementParticipants(fitnessClass.id(), spots);
        } catch (RemoteServiceException ex) {
            if (ex.getStatus() == 409) {
                // Cas 2 : quelqu'un a reserve entre-temps. Compensation : rien a annuler.
                throw noSpots(fitnessClass.id());
            }
            throw ex;
        }

        // Etape 3 : creation de la reservation
        Booking booking = new Booking(referenceGenerator.next(), request.getUserId(), request.getUserEmail(),
                request.getUserName(), fitnessClass.id(), fitnessClass.name(), fitnessClass.dateTime(),
                fitnessClass.instructor(), fitnessClass.price(), spots, now, now.plus(paymentTimeout),
                fitnessClass.dateTime().minus(cancellationNotice));
        try {
            booking = repository.save(booking);
        } catch (RuntimeException ex) {
            // Compensation : les places ont ete prises mais la reservation n'existe pas
            releaseSpotsQuietly(fitnessClass.id(), spots, "echec de persistance de la reservation");
            throw ex;
        }

        // Etape 4 : notification (non bloquante)
        notifier.bookingPending(booking);
        return booking;
    }

    // ------------------------------------------------------------------ Cas 3 : paiement

    public Booking confirmBooking(Long id, ConfirmBookingRequest request) {
        Booking booking = findById(id);

        // Etape 1 : verifications
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException("La reservation " + booking.getBookingReference()
                    + " ne peut pas etre payee (statut " + booking.getStatus() + ")");
        }
        if (booking.isPaymentExpired(now())) {
            throw new PaymentExpiredException("Le delai de paiement de la reservation " + booking.getBookingReference()
                    + " est depasse (" + booking.getPaymentDeadline() + ")");
        }

        // Etape 2 : paiement. Si payment-service est indisponible (503), la reservation reste PENDING_PAYMENT.
        PaymentDto payment = paymentClient.processPayment(new PaymentRequest(booking.getId(),
                booking.getBookingReference(), booking.getUserId(), booking.getTotalAmount(),
                request.getPaymentMethod(), request.getCardLastFour(), request.getTransactionId()));

        // Etape 3 : mise a jour de la reservation
        if (payment.isSuccess()) {
            booking.confirm(payment.id(), payment.paymentReference());
            booking = repository.save(booking);
            notifier.paymentConfirmed(booking);
            return booking;
        }

        // Paiement refuse -> compensation : annulation et liberation des places
        booking.setPaymentId(payment.id());
        booking.setPaymentReference(payment.paymentReference());
        booking.cancel("Paiement refuse : " + payment.failureReason(), now());
        booking = repository.save(booking);
        releaseSpotsQuietly(booking.getClassId(), booking.getNumberOfSpots(), "paiement refuse");
        notifier.bookingCancelled(booking, "Motif : votre paiement a ete refuse. Aucun montant n'a ete debite.");
        throw new PaymentFailedException("Paiement refuse pour la reservation " + booking.getBookingReference()
                + " (" + payment.failureReason() + "). La reservation est annulee et les places liberees.");
    }

    // ------------------------------------------------------------------ Cas 4 : annulation

    public Booking cancelBooking(Long id) {
        Booking booking = findById(id);

        // Etape 1 : verifications
        if (CLOSED.contains(booking.getStatus())) {
            throw new InvalidBookingStateException("La reservation " + booking.getBookingReference()
                    + " ne peut pas etre annulee (statut " + booking.getStatus() + ")");
        }
        if (booking.isCancellationDeadlinePassed(now())) {
            throw new CancellationNotAllowedException("Annulation impossible : la limite d'annulation gratuite ("
                    + booking.getCancellationDeadline() + ", soit 24h avant le cours) est depassee");
        }

        // Etape 2 : liberation des places (si class-service est indisponible -> 503, rien n'a change)
        classClient.decrementParticipants(booking.getClassId(), booking.getNumberOfSpots());

        // Etape 3 : remboursement si la reservation etait payee
        boolean refunded = false;
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            try {
                refund(booking);
                refunded = true;
            } catch (RuntimeException ex) {
                // Compensation : on reprend les places liberees a l'etape 2, la reservation reste CONFIRMED
                reserveSpotsQuietly(booking.getClassId(), booking.getNumberOfSpots());
                throw ex;
            }
        }

        // Etape 4 : annulation + notification
        booking.cancel("Annulee par l'utilisateur", now());
        booking = repository.save(booking);
        notifier.bookingCancelled(booking, refunded
                ? "Le montant de " + booking.getTotalAmount() + " EUR vous sera rembourse."
                : "Aucun paiement n'avait ete effectue.");
        return booking;
    }

    public Booking completeBooking(Long id) {
        Booking booking = findById(id);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException("Seule une reservation CONFIRMED peut etre terminee (statut actuel "
                    + booking.getStatus() + ")");
        }
        booking.complete();
        return repository.save(booking);
    }

    // ------------------------------------------------------------------ scheduler

    /**
     * Annule une reservation dont le delai de paiement est depasse et libere ses places.
     * Si class-service est indisponible, l'exception remonte et la reservation reste PENDING_PAYMENT :
     * elle sera retraitee au prochain passage du scheduler.
     */
    public boolean expireBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || !booking.isPaymentExpired(now())) {
            return false;
        }
        classClient.decrementParticipants(booking.getClassId(), booking.getNumberOfSpots());
        booking.cancel("Delai de paiement depasse", now());
        Booking saved = repository.save(booking);
        notifier.bookingCancelled(saved, "Motif : le paiement n'a pas ete recu avant le "
                + saved.getPaymentDeadline() + ". Les places ont ete liberees.");
        return true;
    }

    /** Envoie un rappel pour les cours confirmes qui ont lieu dans les prochaines 24h (une seule fois). */
    public int sendReminders() {
        LocalDateTime now = now();
        List<Booking> upcoming = repository.findByStatusAndReminderSentFalseAndClassDateBetween(
                BookingStatus.CONFIRMED, now, now.plus(cancellationNotice));
        int sent = 0;
        for (Booking booking : upcoming) {
            if (notifier.reminder(booking)) {
                booking.setReminderSent(true);
                repository.save(booking);
                sent++;
            }
        }
        return sent;
    }

    // ------------------------------------------------------------------ helpers

    private FitnessClassDto fetchClass(Long classId) {
        try {
            return classClient.getFitnessClass(classId);
        } catch (RemoteServiceException ex) {
            if (ex.getStatus() == 404) {
                throw new FitnessClassNotFoundException(classId);
            }
            throw ex;
        }
    }

    private void refund(Booking booking) {
        Long paymentId = booking.getPaymentId() != null
                ? booking.getPaymentId()
                : paymentClient.getPaymentByBooking(booking.getId()).id();
        paymentClient.refund(paymentId);
    }

    private void releaseSpotsQuietly(Long classId, int spots, String reason) {
        try {
            classClient.decrementParticipants(classId, spots);
        } catch (RuntimeException ex) {
            log.error("COMPENSATION ECHOUEE ({}) : impossible de liberer {} place(s) du cours {} : {}",
                    reason, spots, classId, ex.getMessage());
        }
    }

    private void reserveSpotsQuietly(Long classId, int spots) {
        try {
            classClient.incrementParticipants(classId, spots);
        } catch (RuntimeException ex) {
            log.error("COMPENSATION ECHOUEE : impossible de re-reserver {} place(s) du cours {} : {}",
                    spots, classId, ex.getMessage());
        }
    }

    private NoSpotsAvailableException noSpots(Long classId) {
        return new NoSpotsAvailableException("Plus de places disponibles pour ce cours (id " + classId + ")");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
