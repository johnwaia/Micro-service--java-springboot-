package com.example.classservice.model;

import com.example.classservice.exception.InvalidParticipantsException;
import com.example.classservice.exception.NoSpotsAvailableException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FitnessClassTest {

    private FitnessClass classWith(int maxParticipants, int currentParticipants) {
        FitnessClass fc = new FitnessClass("Yoga", "Doux", "Marie", "Paris", ClassCategory.YOGA,
                ClassLevel.BEGINNER, 60, maxParticipants, new BigDecimal("15.00"),
                LocalDateTime.now().plusDays(2));
        fc.setCurrentParticipants(currentParticipants);
        return fc;
    }

    @Test
    void incrementParticipants_whenSpotsAvailable_addsSpots() {
        FitnessClass fc = classWith(10, 5);

        fc.incrementParticipants(2);

        assertThat(fc.getCurrentParticipants()).isEqualTo(7);
        assertThat(fc.getAvailableSpots()).isEqualTo(3);
    }

    @Test
    void incrementParticipants_upToExactCapacity_isAllowed() {
        FitnessClass fc = classWith(10, 8);

        fc.incrementParticipants(2);

        assertThat(fc.getCurrentParticipants()).isEqualTo(10);
    }

    @Test
    void incrementParticipants_whenNotEnoughSpots_throwsNoSpotsAvailable() {
        FitnessClass fc = classWith(10, 9);

        assertThrows(NoSpotsAvailableException.class, () -> fc.incrementParticipants(2));
        assertThat(fc.getCurrentParticipants()).isEqualTo(9);
    }

    @Test
    void decrementParticipants_releasesSpots() {
        FitnessClass fc = classWith(10, 7);

        fc.decrementParticipants(2);

        assertThat(fc.getCurrentParticipants()).isEqualTo(5);
    }

    @Test
    void decrementParticipants_belowZero_isRejected() {
        FitnessClass fc = classWith(10, 1);

        assertThrows(InvalidParticipantsException.class, () -> fc.decrementParticipants(2));
        assertThat(fc.getCurrentParticipants()).isEqualTo(1);
    }

    @Test
    void newClass_isScheduledWithNoParticipants() {
        FitnessClass fc = new FitnessClass("Yoga", "Doux", "Marie", "Paris", ClassCategory.YOGA,
                ClassLevel.BEGINNER, 60, 10, new BigDecimal("15.00"), LocalDateTime.now().plusDays(2));

        assertThat(fc.getStatus()).isEqualTo(ClassStatus.SCHEDULED);
        assertThat(fc.getCurrentParticipants()).isZero();
    }
}
