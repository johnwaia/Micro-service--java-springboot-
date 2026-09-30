package com.example.bookingservice.repository;

import com.example.bookingservice.model.Booking;
import com.example.bookingservice.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByBookingReference(String bookingReference);

    List<Booking> findByUserIdOrderByBookingDateDesc(Long userId);

    List<Booking> findByStatus(BookingStatus status);

    /** Reservations en attente dont le delai de paiement est depasse. */
    List<Booking> findByStatusAndPaymentDeadlineBefore(BookingStatus status, LocalDateTime now);

    /** Reservations confirmees dont le cours a lieu dans la fenetre donnee et pas encore rappelees. */
    List<Booking> findByStatusAndReminderSentFalseAndClassDateBetween(BookingStatus status, LocalDateTime from,
                                                                       LocalDateTime to);
}
