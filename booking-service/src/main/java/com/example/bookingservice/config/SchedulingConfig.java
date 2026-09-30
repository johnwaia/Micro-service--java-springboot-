package com.example.bookingservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les taches planifiees (expiration des paiements, rappels). Desactivable pour les tests,
 * qui appellent directement BookingScheduler.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "booking.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
