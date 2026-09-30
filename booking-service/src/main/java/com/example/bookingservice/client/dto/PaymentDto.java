package com.example.bookingservice.client.dto;

import java.math.BigDecimal;

public record PaymentDto(
        Long id,
        String paymentReference,
        Long bookingId,
        BigDecimal amount,
        String status,
        String failureReason) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String REFUNDED = "REFUNDED";

    public boolean isSuccess() {
        return SUCCESS.equals(status);
    }
}
