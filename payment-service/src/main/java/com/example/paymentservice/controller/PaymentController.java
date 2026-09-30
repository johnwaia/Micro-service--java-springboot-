package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** Appele par booking-service. Repond 201 que le paiement soit SUCCESS ou FAILED (voir "status"). */
    @PostMapping
    public ResponseEntity<PaymentResponse> process(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse payment = PaymentResponse.from(service.processPayment(request));
        return ResponseEntity.created(URI.create("/api/payments/" + payment.id())).body(payment);
    }

    @GetMapping
    public List<PaymentResponse> findAll() {
        return service.findAll().stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable("id") Long id) {
        return PaymentResponse.from(service.findById(id));
    }

    @GetMapping("/booking/{bookingId}")
    public PaymentResponse findByBooking(@PathVariable("bookingId") Long bookingId) {
        return PaymentResponse.from(service.findByBookingId(bookingId));
    }

    @GetMapping("/user/{userId}")
    public List<PaymentResponse> findByUser(@PathVariable("userId") Long userId) {
        return service.findByUserId(userId).stream().map(PaymentResponse::from).toList();
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable("id") Long id) {
        return PaymentResponse.from(service.refund(id));
    }
}
