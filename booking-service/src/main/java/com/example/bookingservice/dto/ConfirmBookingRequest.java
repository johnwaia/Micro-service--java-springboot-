package com.example.bookingservice.dto;

import com.example.bookingservice.model.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public class ConfirmBookingRequest {

    @NotNull
    private PaymentMethod paymentMethod;

    private String cardLastFour;

    private String transactionId;

    public ConfirmBookingRequest() {
    }

    public ConfirmBookingRequest(PaymentMethod paymentMethod, String cardLastFour, String transactionId) {
        this.paymentMethod = paymentMethod;
        this.cardLastFour = cardLastFour;
        this.transactionId = transactionId;
    }

    @JsonIgnore
    @AssertTrue(message = "cardLastFour (4 chiffres) est obligatoire pour un paiement par carte")
    public boolean isCardLastFourValid() {
        if (paymentMethod == null || !paymentMethod.isCard()) {
            return true;
        }
        return cardLastFour != null && cardLastFour.matches("\\d{4}");
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
