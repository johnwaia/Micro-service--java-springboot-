package com.example.bookingservice.client;

import com.example.bookingservice.client.dto.FitnessClassDto;
import com.example.bookingservice.client.fallback.ClassClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "class-service", fallbackFactory = ClassClientFallbackFactory.class)
public interface ClassClient {

    @GetMapping("/api/classes/{id}")
    FitnessClassDto getFitnessClass(@PathVariable("id") Long id);

    @PatchMapping("/api/classes/{id}/increment")
    FitnessClassDto incrementParticipants(@PathVariable("id") Long id, @RequestParam("spots") int spots);

    @PatchMapping("/api/classes/{id}/decrement")
    FitnessClassDto decrementParticipants(@PathVariable("id") Long id, @RequestParam("spots") int spots);
}
