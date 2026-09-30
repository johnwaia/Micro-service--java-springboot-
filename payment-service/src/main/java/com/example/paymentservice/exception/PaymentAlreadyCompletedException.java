package com.example.paymentservice.exception;

public class PaymentAlreadyCompletedException extends RuntimeException {

    public PaymentAlreadyCompletedException(Long bookingId) {
        super("Un paiement reussi existe deja pour la reservation " + bookingId);
    }
}
