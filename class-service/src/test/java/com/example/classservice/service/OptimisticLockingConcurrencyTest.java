package com.example.classservice.service;

import com.example.classservice.model.ClassCategory;
import com.example.classservice.model.ClassLevel;
import com.example.classservice.model.FitnessClass;
import com.example.classservice.repository.FitnessClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie avec une vraie base H2 que des reservations concurrentes ne provoquent jamais de surreservation.
 */
@SpringBootTest
class OptimisticLockingConcurrencyTest {

    private static final int CAPACITY = 10;
    private static final int CONCURRENT_REQUESTS = 25;

    @Autowired
    private FitnessClassService service;

    @Autowired
    private FitnessClassRepository repository;

    @Test
    void concurrentIncrements_neverExceedMaxParticipants() throws Exception {
        FitnessClass fc = repository.save(new FitnessClass("CrossFit", "WOD", "Karim", "Paris",
                ClassCategory.CROSSFIT, ClassLevel.ADVANCED, 45, CAPACITY, new BigDecimal("25.00"),
                LocalDateTime.now().plusDays(3)));
        Long id = fc.getId();

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.incrementParticipants(id, 1);
                    return true;
                } catch (RuntimeException rejected) { // plus de place, ou conflit de version persistant
                    return false;
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long accepted = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                accepted++;
            }
        }

        FitnessClass reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getCurrentParticipants()).isLessThanOrEqualTo(CAPACITY);
        assertThat(reloaded.getCurrentParticipants()).isEqualTo((int) accepted);
        assertThat(reloaded.getVersion()).isEqualTo(accepted);
    }
}
