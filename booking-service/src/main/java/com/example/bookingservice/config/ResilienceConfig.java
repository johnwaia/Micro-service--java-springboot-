package com.example.bookingservice.config;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration par defaut des circuit breakers des clients Feign
 * (active par spring.cloud.openfeign.circuitbreaker.enabled=true).
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer(
            @Value("${booking.resilience.timeout:PT5S}") Duration timeout,
            @Value("${booking.resilience.wait-in-open-state:PT10S}") Duration waitInOpenState) {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(timeout)
                        .build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(waitInOpenState)
                        .permittedNumberOfCallsInHalfOpenState(3)
                        // un 4xx (409 "plus de places", 404...) est une reponse metier normale,
                        // pas une panne : il ne doit pas faire ouvrir le circuit
                        .ignoreExceptions(FeignException.FeignClientException.class)
                        .build())
                .build());
    }
}
