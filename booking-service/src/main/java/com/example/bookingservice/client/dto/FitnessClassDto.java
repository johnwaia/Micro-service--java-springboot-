package com.example.bookingservice.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Vue d'un cours telle que renvoyee par class-service (seuls les champs utiles au saga). */
public record FitnessClassDto(
        Long id,
        String name,
        String instructor,
        String gymLocation,
        LocalDateTime dateTime,
        BigDecimal price,
        Integer maxParticipants,
        Integer currentParticipants,
        String status) {

    public static final String SCHEDULED = "SCHEDULED";

    public int availableSpots() {
        return maxParticipants - currentParticipants;
    }
}
