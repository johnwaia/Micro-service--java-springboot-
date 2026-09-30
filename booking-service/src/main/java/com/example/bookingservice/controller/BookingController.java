package com.example.bookingservice.controller;

import com.example.bookingservice.dto.BookingRequest;
import com.example.bookingservice.dto.BookingResponse;
import com.example.bookingservice.dto.ConfirmBookingRequest;
import com.example.bookingservice.model.BookingStatus;
import com.example.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping
    public List<BookingResponse> findAll(@RequestParam(required = false) BookingStatus status) {
        return service.findAll(status).stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/{id}")
    public BookingResponse findById(@PathVariable("id") Long id) {
        return BookingResponse.from(service.findById(id));
    }

    @GetMapping("/user/{userId}")
    public List<BookingResponse> findByUser(@PathVariable("userId") Long userId) {
        return service.findByUserId(userId).stream().map(BookingResponse::from).toList();
    }

    /** Reservations PENDING_PAYMENT dont le delai de paiement est depasse (traitees par le scheduler). */
    @GetMapping("/expired")
    public List<BookingResponse> findExpired() {
        return service.findExpiredPendingBookings().stream().map(BookingResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        BookingResponse created = BookingResponse.from(service.createBooking(request));
        return ResponseEntity.created(URI.create("/api/bookings/" + created.id())).body(created);
    }

    @PatchMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable("id") Long id, @Valid @RequestBody ConfirmBookingRequest request) {
        return BookingResponse.from(service.confirmBooking(id, request));
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable("id") Long id) {
        return BookingResponse.from(service.cancelBooking(id));
    }

    @PatchMapping("/{id}/complete")
    public BookingResponse complete(@PathVariable("id") Long id) {
        return BookingResponse.from(service.completeBooking(id));
    }
}
