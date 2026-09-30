package com.example.bookingservice.service;

import com.example.bookingservice.repository.BookingRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Genere une reference unique "BK-XXXXX" (sans caracteres ambigus 0/O, 1/I). */
@Component
public class BookingReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository repository;

    public BookingReferenceGenerator(BookingRepository repository) {
        this.repository = repository;
    }

    public String next() {
        String reference;
        do {
            StringBuilder sb = new StringBuilder("BK-");
            for (int i = 0; i < 5; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            reference = sb.toString();
        } while (repository.existsByBookingReference(reference));
        return reference;
    }
}
