package com.example.bookingservice.client.fallback;

import com.example.bookingservice.client.NotificationClient;
import com.example.bookingservice.client.dto.NotificationDto;
import com.example.bookingservice.client.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Une notification perdue ne doit pas faire echouer une reservation ou un paiement : degradation gracieuse.
 */
@Component
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationClientFallbackFactory.class);

    @Override
    public NotificationClient create(Throwable cause) {
        return request -> {
            log.warn("notification-service indisponible, notification {} non envoyee a {} : {}",
                    request.type(), request.email(), cause == null ? "?" : cause.toString());
            return null;
        };
    }
}
