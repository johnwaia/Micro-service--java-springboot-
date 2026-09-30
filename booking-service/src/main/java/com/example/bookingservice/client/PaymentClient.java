package com.example.bookingservice.client;

import com.example.bookingservice.client.dto.PaymentDto;
import com.example.bookingservice.client.dto.PaymentRequest;
import com.example.bookingservice.client.fallback.PaymentClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", fallbackFactory = PaymentClientFallbackFactory.class)
public interface PaymentClient {

    @PostMapping("/api/payments")
    PaymentDto processPayment(@RequestBody PaymentRequest request);

    @GetMapping("/api/payments/booking/{bookingId}")
    PaymentDto getPaymentByBooking(@PathVariable("bookingId") Long bookingId);

    @PostMapping("/api/payments/{id}/refund")
    PaymentDto refund(@PathVariable("id") Long paymentId);
}
