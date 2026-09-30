package com.example.classservice.dto;

import com.example.classservice.model.ClassCategory;
import com.example.classservice.model.ClassLevel;
import com.example.classservice.model.ClassStatus;
import com.example.classservice.model.FitnessClass;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FitnessClassResponse(
        Long id,
        String name,
        String description,
        String instructor,
        String gymLocation,
        ClassCategory category,
        ClassLevel level,
        Integer durationMinutes,
        Integer maxParticipants,
        Integer currentParticipants,
        Integer availableSpots,
        BigDecimal price,
        LocalDateTime dateTime,
        ClassStatus status,
        Long version) {

    public static FitnessClassResponse from(FitnessClass fc) {
        return new FitnessClassResponse(
                fc.getId(),
                fc.getName(),
                fc.getDescription(),
                fc.getInstructor(),
                fc.getGymLocation(),
                fc.getCategory(),
                fc.getLevel(),
                fc.getDurationMinutes(),
                fc.getMaxParticipants(),
                fc.getCurrentParticipants(),
                fc.getAvailableSpots(),
                fc.getPrice(),
                fc.getDateTime(),
                fc.getStatus(),
                fc.getVersion()
        );
    }
}
