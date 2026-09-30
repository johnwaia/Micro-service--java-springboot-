package com.example.paymentservice.dto;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentMethod;
import com.example.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        String paymentReference,
        Long bookingId,
        String bookingReference,
        Long userId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String cardLastFour,
        String transactionId,
        LocalDateTime paymentDate,
        PaymentStatus status,
        String failureReason,
        LocalDateTime refundDate) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentReference(),
                payment.getBookingId(),
                payment.getBookingReference(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getCardLastFour(),
                payment.getTransactionId(),
                payment.getPaymentDate(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getRefundDate()
        );
    }
}
