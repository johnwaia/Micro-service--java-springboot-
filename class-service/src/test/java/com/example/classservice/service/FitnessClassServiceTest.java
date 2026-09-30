package com.example.classservice.service;

import com.example.classservice.exception.ClassNotBookableException;
import com.example.classservice.exception.FitnessClassNotFoundException;
import com.example.classservice.exception.NoSpotsAvailableException;
import com.example.classservice.model.ClassCategory;
import com.example.classservice.model.ClassLevel;
import com.example.classservice.model.ClassStatus;
import com.example.classservice.model.FitnessClass;
import com.example.classservice.repository.FitnessClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitnessClassServiceTest {

    @Mock
    private FitnessClassRepository repository;

    private FitnessClassService service;

    @BeforeEach
    void setUp() {
        service = new FitnessClassService(repository, mock(PlatformTransactionManager.class));
    }

    private FitnessClass classWith(int max, int current) {
        FitnessClass fc = new FitnessClass("Yoga", "Doux", "Marie", "Paris", ClassCategory.YOGA,
                ClassLevel.BEGINNER, 60, max, new BigDecimal("15.00"), LocalDateTime.now().plusDays(2));
        fc.setCurrentParticipants(current);
        return fc;
    }

    @Test
    void incrementParticipants_whenSpotsAvailable_savesUpdatedClass() {
        FitnessClass fc = classWith(10, 5);
        when(repository.findById(1L)).thenReturn(Optional.of(fc));
        when(repository.saveAndFlush(any(FitnessClass.class))).thenAnswer(inv -> inv.getArgument(0));

        FitnessClass updated = service.incrementParticipants(1L, 2);

        assertThat(updated.getCurrentParticipants()).isEqualTo(7);
    }

    @Test
    void incrementParticipants_whenNoSpotsAvailable_throwsAndDoesNotSave() {
        when(repository.findById(1L)).thenReturn(Optional.of(classWith(10, 9)));

        assertThrows(NoSpotsAvailableException.class, () -> service.incrementParticipants(1L, 2));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void incrementParticipants_whenClassCancelled_throwsClassNotBookable() {
        FitnessClass fc = classWith(10, 0);
        fc.setStatus(ClassStatus.CANCELLED);
        when(repository.findById(1L)).thenReturn(Optional.of(fc));

        assertThrows(ClassNotBookableException.class, () -> service.incrementParticipants(1L, 1));
    }

    @Test
    void incrementParticipants_whenClassDoesNotExist_throwsNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(FitnessClassNotFoundException.class, () -> service.incrementParticipants(42L, 1));
    }

    @Test
    void incrementParticipants_whenVersionConflictOnce_retriesWithFreshState() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(classWith(10, 5)))
                .thenReturn(Optional.of(classWith(10, 6))); // une autre requete a reserve entre-temps
        when(repository.saveAndFlush(any(FitnessClass.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(FitnessClass.class, 1L))
                .thenAnswer(inv -> inv.getArgument(0));

        FitnessClass updated = service.incrementParticipants(1L, 2);

        assertThat(updated.getCurrentParticipants()).isEqualTo(8);
        verify(repository, times(2)).findById(1L);
    }

    @Test
    void incrementParticipants_whenVersionConflictPersists_givesUpAfterMaxAttempts() {
        when(repository.findById(1L)).thenAnswer(inv -> Optional.of(classWith(10, 5)));
        when(repository.saveAndFlush(any(FitnessClass.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(FitnessClass.class, 1L));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.incrementParticipants(1L, 1));
        verify(repository, times(FitnessClassService.MAX_ATTEMPTS)).saveAndFlush(any());
    }

    @Test
    void cancel_setsStatusCancelled() {
        FitnessClass fc = classWith(10, 3);
        when(repository.findById(1L)).thenReturn(Optional.of(fc));
        when(repository.save(any(FitnessClass.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.cancel(1L).getStatus()).isEqualTo(ClassStatus.CANCELLED);
    }
}
