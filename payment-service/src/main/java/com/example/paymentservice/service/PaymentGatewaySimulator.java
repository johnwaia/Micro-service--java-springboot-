package com.example.paymentservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Simule la banque / le PSP : tout paiement strictement inferieur au seuil (100 EUR par defaut) est accepte,
 * tout paiement superieur ou egal est refuse.
 */
@Component
public class PaymentGatewaySimulator {

    private final BigDecimal rejectionThreshold;

    public PaymentGatewaySimulator(@Value("${payment.simulation.rejection-threshold:100.00}") BigDecimal rejectionThreshold) {
        this.rejectionThreshold = rejectionThreshold;
    }

    public boolean authorize(BigDecimal amount) {
        return amount.compareTo(rejectionThreshold) < 0;
    }

    public BigDecimal getRejectionThreshold() {
        return rejectionThreshold;
    }
}
