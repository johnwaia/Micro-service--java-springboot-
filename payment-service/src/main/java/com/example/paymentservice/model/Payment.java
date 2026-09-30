package com.example.paymentservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String paymentReference;

    private Long bookingId;

    private String bookingReference;

    private Long userId;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String cardLastFour;

    private String transactionId;

    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String failureReason;

    private LocalDateTime refundDate;

    protected Payment() {
    }

    public Payment(String paymentReference, Long bookingId, String bookingReference, Long userId, BigDecimal amount,
                   PaymentMethod paymentMethod, String cardLastFour, String transactionId) {
        this.paymentReference = paymentReference;
        this.bookingId = bookingId;
        this.bookingReference = bookingReference;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.cardLastFour = cardLastFour;
        this.transactionId = transactionId;
        this.status = PaymentStatus.PENDING;
    }

    public void markSuccess(LocalDateTime date) {
        this.status = PaymentStatus.SUCCESS;
        this.paymentDate = date;
        this.failureReason = null;
    }

    public void markFailed(LocalDateTime date, String reason) {
        this.status = PaymentStatus.FAILED;
        this.paymentDate = date;
        this.failureReason = reason;
    }

    public void markRefunded(LocalDateTime date) {
        this.status = PaymentStatus.REFUNDED;
        this.refundDate = date;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getRefundDate() {
        return refundDate;
    }
}
