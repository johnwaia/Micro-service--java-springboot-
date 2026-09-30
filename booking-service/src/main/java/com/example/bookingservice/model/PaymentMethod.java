package com.example.bookingservice.model;

public enum PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    PAYPAL,
    STRIPE;

    public boolean isCard() {
        return this == CREDIT_CARD || this == DEBIT_CARD;
    }
}
