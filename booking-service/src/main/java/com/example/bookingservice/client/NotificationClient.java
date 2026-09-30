package com.example.bookingservice.client;

import com.example.bookingservice.client.dto.NotificationDto;
import com.example.bookingservice.client.dto.NotificationRequest;
import com.example.bookingservice.client.fallback.NotificationClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", fallbackFactory = NotificationClientFallbackFactory.class)
public interface NotificationClient {

    @PostMapping("/api/notifications")
    NotificationDto send(@RequestBody NotificationRequest request);
}
