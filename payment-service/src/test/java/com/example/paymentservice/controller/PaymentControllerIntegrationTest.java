package com.example.paymentservice.controller;

import com.example.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private Map<String, Object> payload(long bookingId, double amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", bookingId);
        payload.put("bookingReference", "BK-TEST" + bookingId);
        payload.put("userId", 1);
        payload.put("amount", amount);
        payload.put("paymentMethod", "CREDIT_CARD");
        payload.put("cardLastFour", "1234");
        payload.put("transactionId", "txn_123456");
        return payload;
    }

    private JsonNode pay(Map<String, Object> payload) throws Exception {
        String body = mockMvc.perform(post("/api/payments").contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void processPayment_under100_isSuccess_andCanBeRefundedOnce() throws Exception {
        JsonNode payment = pay(payload(10, 50.0));
        long paymentId = payment.get("id").asLong();
        assertThat(payment.get("status").asText()).isEqualTo("SUCCESS");

        mockMvc.perform(get("/api/payments/booking/{bookingId}", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.transactionId").value("txn_123456"));

        mockMvc.perform(post("/api/payments/{id}/refund", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(post("/api/payments/{id}/refund", paymentId))
                .andExpect(status().isConflict());
    }

    @Test
    void processPayment_of100OrMore_isRecordedAsFailed() throws Exception {
        mockMvc.perform(post("/api/payments").contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(11, 100.0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason", containsString("refuse")));
    }

    @Test
    void processPayment_twiceForSameBooking_returns409() throws Exception {
        pay(payload(12, 30.0));

        mockMvc.perform(post("/api/payments").contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload(12, 30.0))))
                .andExpect(status().isConflict());
    }

    @Test
    void processPayment_cardWithoutLastFour_returns400() throws Exception {
        Map<String, Object> payload = payload(13, 30.0);
        payload.remove("cardLastFour");

        mockMvc.perform(post("/api/payments").contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cardLastFour")));
    }

    @Test
    void findByUser_returnsPaymentHistory() throws Exception {
        pay(payload(20, 10.0));
        pay(payload(21, 120.0));

        mockMvc.perform(get("/api/payments/user/{userId}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void findByBooking_whenNoPayment_returns404() throws Exception {
        mockMvc.perform(get("/api/payments/booking/{bookingId}", 999))
                .andExpect(status().isNotFound());
    }
}
