package com.example.bookingservice.client.dto;

import com.example.bookingservice.model.PaymentMethod;

import java.math.BigDecimal;

public record PaymentRequest(
        Long bookingId,
        String bookingReference,
        Long userId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String cardLastFour,
        String transactionId) {
}
