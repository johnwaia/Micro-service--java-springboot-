package com.example.bookingservice.client;

import com.example.bookingservice.repository.BookingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie la vraie chaine Feign (+ feign-hc5 pour PATCH) + circuit breaker Resilience4j + fallback factories :
 * class-service est simule par un petit serveur HTTP, payment-service et notification-service sont injoignables.
 */
@SpringBootTest(properties = {
        "spring.cloud.openfeign.circuitbreaker.enabled=true",
        "spring.cloud.openfeign.client.config.payment-service.url=http://localhost:1",
        "spring.cloud.openfeign.client.config.notification-service.url=http://localhost:1",
        "booking.resilience.wait-in-open-state=PT60S"
})
@AutoConfigureMockMvc
class FeignCircuitBreakerIntegrationTest {

    private static HttpServer classServiceStub;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    @DynamicPropertySource
    static void classServiceUrl(DynamicPropertyRegistry registry) throws IOException {
        classServiceStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        classServiceStub.createContext("/api/classes/", FeignCircuitBreakerIntegrationTest::handleClassService);
        classServiceStub.start();
        registry.add("spring.cloud.openfeign.client.config.class-service.url",
                () -> "http://localhost:" + classServiceStub.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        classServiceStub.stop(0);
    }

    /**
     * /api/classes/1 : cours complet cote serveur (increment -> 409)
     * /api/classes/2 : cours avec places (increment -> 200)
     * /api/classes/404 : 404, /api/classes/500 : panne serveur
     */
    private static void handleClassService(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String classJson = "{\"id\":%s,\"name\":\"Stub\",\"instructor\":\"Marie\",\"gymLocation\":\"Paris\","
                + "\"dateTime\":\"" + LocalDateTime.now().plusDays(3).withNano(0) + "\",\"price\":20.00,"
                + "\"maxParticipants\":10,\"currentParticipants\":5,\"status\":\"SCHEDULED\"}";
        if (path.equals("/api/classes/1/increment") && method.equals("PATCH")) {
            respond(exchange, 409, "{\"status\":409,\"message\":\"Plus de places disponibles pour ce cours (id 1)\"}");
        } else if (path.matches("/api/classes/2/(increment|decrement)") && method.equals("PATCH")) {
            respond(exchange, 200, classJson.formatted(2));
        } else if (path.matches("/api/classes/[12]") && method.equals("GET")) {
            respond(exchange, 200, classJson.formatted(path.substring(path.lastIndexOf('/') + 1)));
        } else if (path.equals("/api/classes/500")) {
            respond(exchange, 500, "{\"message\":\"boom\"}");
        } else {
            respond(exchange, 404, "{\"status\":404,\"message\":\"Cours introuvable\"}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @BeforeEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
    }

    private String bookingPayload(long classId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("userId", 1, "userEmail", "john@example.com",
                "userName", "John Doe", "classId", classId, "numberOfSpots", 2));
    }

    @Test
    void conflictFromClassService_isTranslatedTo409_andDoesNotOpenTheCircuit() throws Exception {
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(post("/api/bookings").contentType("application/json").content(bookingPayload(1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("Plus de places disponibles")));
        }
        assertThat(breakerFor("incrementParticipants"))
                .hasValueSatisfying(cb -> assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED));
    }

    @Test
    void unknownClass_isTranslatedTo404() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType("application/json").content(bookingPayload(404)))
                .andExpect(status().isNotFound());
    }

    @Test
    void serverErrorFromClassService_isTranslatedTo503() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType("application/json").content(bookingPayload(500)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("class-service est indisponible")));
    }

    @Test
    void bookingSucceedsThroughPatch_evenIfNotificationServiceIsDown_andPaymentOutageOpensTheCircuit() throws Exception {
        String body = mockMvc.perform(post("/api/bookings").contentType("application/json").content(bookingPayload(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(body).get("id").asLong();

        String payment = "{\"paymentMethod\":\"PAYPAL\"}";
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId).contentType("application/json").content(payment))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message", containsString("payment-service est indisponible")));
        }

        assertThat(breakerFor("processPayment"))
                .hasValueSatisfying(cb -> assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN));
        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    private Optional<CircuitBreaker> breakerFor(String methodName) {
        return circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().stream()
                .filter(cb -> cb.getName().contains(methodName))
                .findFirst();
    }
}
