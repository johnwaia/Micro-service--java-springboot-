package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.exception.PaymentAlreadyCompletedException;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.exception.RefundNotAllowedException;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository repository;
    private final PaymentGatewaySimulator gateway;

    public PaymentService(PaymentRepository repository, PaymentGatewaySimulator gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    /**
     * Traite un paiement. Le paiement est toujours enregistre (SUCCESS ou FAILED) : un refus de la banque
     * n'est pas une erreur HTTP, c'est a l'appelant (booking-service) de lire le statut.
     */
    public Payment processPayment(PaymentRequest request) {
        if (repository.existsByBookingIdAndStatus(request.getBookingId(), PaymentStatus.SUCCESS)) {
            throw new PaymentAlreadyCompletedException(request.getBookingId());
        }

        String transactionId = request.getTransactionId() != null && !request.getTransactionId().isBlank()
                ? request.getTransactionId()
                : "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String cardLastFour = request.getPaymentMethod().isCard() ? request.getCardLastFour() : null;

        Payment payment = new Payment(generateReference(), request.getBookingId(), request.getBookingReference(),
                request.getUserId(), request.getAmount(), request.getPaymentMethod(), cardLastFour, transactionId);

        LocalDateTime now = LocalDateTime.now();
        if (gateway.authorize(request.getAmount())) {
            payment.markSuccess(now);
        } else {
            payment.markFailed(now, "Paiement refuse par la banque (simulation : montant >= "
                    + gateway.getRejectionThreshold() + " EUR)");
        }
        return repository.save(payment);
    }

    public Payment findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Paiement introuvable avec l'id " + id));
    }

    /** Dernier paiement (le plus recent) associe a une reservation. */
    public Payment findByBookingId(Long bookingId) {
        return repository.findFirstByBookingIdOrderByIdDesc(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException("Aucun paiement pour la reservation " + bookingId));
    }

    public List<Payment> findByUserId(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId);
    }

    public List<Payment> findAll() {
        return repository.findAll();
    }

    /**
     * Rembourse un paiement reussi. La regle "annulation au moins 24h avant le cours" est verifiee par
     * booking-service, qui connait la date du cours ; payment-service garantit qu'on ne rembourse
     * qu'une seule fois un paiement effectivement encaisse.
     */
    public Payment refund(Long id) {
        Payment payment = findById(id);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new RefundNotAllowedException("Seul un paiement SUCCESS peut etre rembourse (paiement "
                    + payment.getPaymentReference() + " au statut " + payment.getStatus() + ")");
        }
        payment.markRefunded(LocalDateTime.now());
        return repository.save(payment);
    }

    private String generateReference() {
        String reference;
        do {
            StringBuilder sb = new StringBuilder("PAY-");
            for (int i = 0; i < 5; i++) {
                sb.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
            }
            reference = sb.toString();
        } while (repository.existsByPaymentReference(reference));
        return reference;
    }
}
