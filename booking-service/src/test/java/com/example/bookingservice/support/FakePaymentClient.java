package com.example.bookingservice.support;

import com.example.bookingservice.client.PaymentClient;
import com.example.bookingservice.client.dto.PaymentDto;
import com.example.bookingservice.client.dto.PaymentRequest;
import com.example.bookingservice.exception.RemoteServiceException;
import com.example.bookingservice.exception.ServiceUnavailableException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * payment-service en memoire : accepte si montant < 100, refuse sinon ; un seul remboursement par paiement.
 */
public class FakePaymentClient implements PaymentClient {

    private static final BigDecimal THRESHOLD = new BigDecimal("100");

    private final Map<Long, PaymentDto> payments = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private volatile boolean down;

    public void setDown(boolean down) {
        this.down = down;
    }

    public void reset() {
        payments.clear();
        down = false;
    }

    public List<PaymentDto> all() {
        return List.copyOf(payments.values());
    }

    @Override
    public PaymentDto processPayment(PaymentRequest request) {
        checkUp();
        long id = ids.incrementAndGet();
        boolean accepted = request.amount().compareTo(THRESHOLD) < 0;
        PaymentDto payment = new PaymentDto(id, "PAY-F" + id, request.bookingId(), request.amount(),
                accepted ? PaymentDto.SUCCESS : PaymentDto.FAILED,
                accepted ? null : "montant >= 100 EUR (simulation)");
        payments.put(id, payment);
        return payment;
    }

    @Override
    public PaymentDto getPaymentByBooking(Long bookingId) {
        checkUp();
        return payments.values().stream()
                .filter(p -> p.bookingId().equals(bookingId))
                .max(Comparator.comparing(PaymentDto::id))
                .orElseThrow(() -> new RemoteServiceException("payment-service", 404, "Aucun paiement"));
    }

    @Override
    public PaymentDto refund(Long paymentId) {
        checkUp();
        PaymentDto p = payments.get(paymentId);
        if (p == null || !p.isSuccess()) {
            throw new RemoteServiceException("payment-service", 409, "Remboursement impossible");
        }
        PaymentDto refunded = new PaymentDto(p.id(), p.paymentReference(), p.bookingId(), p.amount(),
                PaymentDto.REFUNDED, null);
        payments.put(paymentId, refunded);
        return refunded;
    }

    private void checkUp() {
        if (down) {
            throw new ServiceUnavailableException("payment-service est indisponible (simulation)", null);
        }
    }
}
