package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByBookingIdOrderByIdDesc(Long bookingId);

    boolean existsByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    boolean existsByPaymentReference(String paymentReference);

    List<Payment> findByUserIdOrderByIdDesc(Long userId);
}
