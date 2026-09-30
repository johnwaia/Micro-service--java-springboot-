package com.example.bookingservice.client.fallback;

import com.example.bookingservice.client.ClassClient;
import com.example.bookingservice.client.dto.FitnessClassDto;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * class-service est indispensable au saga : pas de valeur de repli, on signale l'erreur
 * (metier 4xx ou indisponibilite 503) a BookingService qui decide de la compensation.
 */
@Component
public class ClassClientFallbackFactory implements FallbackFactory<ClassClient> {

    private static final String SERVICE = "class-service";

    @Override
    public ClassClient create(Throwable cause) {
        return new ClassClient() {
            @Override
            public FitnessClassDto getFitnessClass(Long id) {
                throw RemoteErrors.translate(SERVICE, cause);
            }

            @Override
            public FitnessClassDto incrementParticipants(Long id, int spots) {
                throw RemoteErrors.translate(SERVICE, cause);
            }

            @Override
            public FitnessClassDto decrementParticipants(Long id, int spots) {
                throw RemoteErrors.translate(SERVICE, cause);
            }
        };
    }
}
