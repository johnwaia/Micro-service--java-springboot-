package com.example.paymentservice.dto;

import com.example.paymentservice.model.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PaymentRequest {

    @NotNull
    private Long bookingId;

    private String bookingReference;

    @NotNull
    private Long userId;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal amount;

    @NotNull
    private PaymentMethod paymentMethod;

    private String cardLastFour;

    private String transactionId;

    @JsonIgnore
    @AssertTrue(message = "cardLastFour (4 chiffres) est obligatoire pour un paiement par carte")
    public boolean isCardLastFourValid() {
        if (paymentMethod == null || !paymentMethod.isCard()) {
            return true;
        }
        return cardLastFour != null && cardLastFour.matches("\\d{4}");
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public void setCardLastFour(String cardLastFour) {
        this.cardLastFour = cardLastFour;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}
