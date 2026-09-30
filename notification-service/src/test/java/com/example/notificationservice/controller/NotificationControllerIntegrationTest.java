package com.example.notificationservice.controller;

import com.example.notificationservice.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private String payload(long userId, String email, String type) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("email", email);
        payload.put("type", type);
        payload.put("subject", "Sujet");
        payload.put("content", "Contenu");
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    void send_thenListByUser() throws Exception {
        mockMvc.perform(post("/api/notifications").contentType("application/json")
                        .content(payload(1, "john@example.com", "BOOKING_CONFIRMATION")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentDate").exists());
        mockMvc.perform(post("/api/notifications").contentType("application/json")
                        .content(payload(1, "john@example.com", "PAYMENT_CONFIRMATION")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/notifications").contentType("application/json")
                        .content(payload(2, "jane@example.com", "BOOKING_REMINDER")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/notifications/user/{userId}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("PAYMENT_CONFIRMATION"));
    }

    @Test
    void failedNotification_appearsInPending_andCanBeRetried() throws Exception {
        String body = mockMvc.perform(post("/api/notifications").contentType("application/json")
                        .content(payload(3, "invalide", "BOOKING_CANCELLED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get("/api/notifications/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(patch("/api/notifications/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(2));
    }

    @Test
    void retry_onSentNotification_returns409() throws Exception {
        String body = mockMvc.perform(post("/api/notifications").contentType("application/json")
                        .content(payload(4, "ok@example.com", "BOOKING_CONFIRMATION")))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(patch("/api/notifications/{id}/retry", id))
                .andExpect(status().isConflict());
    }

    @Test
    void send_withMissingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications").contentType("application/json").content("{\"userId\":1}"))
                .andExpect(status().isBadRequest());
    }
}
