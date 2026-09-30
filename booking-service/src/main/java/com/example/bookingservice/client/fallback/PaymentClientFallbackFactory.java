package com.example.bookingservice.client.fallback;

import com.example.bookingservice.client.PaymentClient;
import com.example.bookingservice.client.dto.PaymentDto;
import com.example.bookingservice.client.dto.PaymentRequest;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Pas de repli possible pour un paiement : la reservation reste PENDING_PAYMENT et le client peut reessayer.
 */
@Component
public class PaymentClientFallbackFactory implements FallbackFactory<PaymentClient> {

    private static final String SERVICE = "payment-service";

    @Override
    public PaymentClient create(Throwable cause) {
        return new PaymentClient() {
            @Override
            public PaymentDto processPayment(PaymentRequest request) {
                throw RemoteErrors.translate(SERVICE, cause);
            }

            @Override
            public PaymentDto getPaymentByBooking(Long bookingId) {
                throw RemoteErrors.translate(SERVICE, cause);
            }

            @Override
            public PaymentDto refund(Long paymentId) {
                throw RemoteErrors.translate(SERVICE, cause);
            }
        };
    }
}
