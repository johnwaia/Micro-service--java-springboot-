package com.example.notificationservice.controller;

import com.example.notificationservice.dto.NotificationRequest;
import com.example.notificationservice.dto.NotificationResponse;
import com.example.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Appele par les autres services. 201 meme si l'envoi echoue (status FAILED, rejouable). */
    @PostMapping
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody NotificationRequest request) {
        NotificationResponse created = NotificationResponse.from(service.send(request));
        return ResponseEntity.created(URI.create("/api/notifications/" + created.id())).body(created);
    }

    @GetMapping("/user/{userId}")
    public List<NotificationResponse> findByUser(@PathVariable("userId") Long userId) {
        return service.findByUserId(userId).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/pending")
    public List<NotificationResponse> findPending() {
        return service.findPending().stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public NotificationResponse findById(@PathVariable("id") Long id) {
        return NotificationResponse.from(service.findById(id));
    }

    @PatchMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable("id") Long id) {
        return NotificationResponse.from(service.retry(id));
    }
}
