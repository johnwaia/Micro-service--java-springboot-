package com.example.bookingservice.controller;

import com.example.bookingservice.client.ClassClient;
import com.example.bookingservice.client.NotificationClient;
import com.example.bookingservice.client.PaymentClient;
import com.example.bookingservice.client.dto.NotificationRequest.NotificationType;
import com.example.bookingservice.client.dto.PaymentDto;
import com.example.bookingservice.model.Booking;
import com.example.bookingservice.model.BookingStatus;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.scheduler.BookingScheduler;
import com.example.bookingservice.support.FakeClassClient;
import com.example.bookingservice.support.FakeNotificationClient;
import com.example.bookingservice.support.FakePaymentClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du saga via l'API REST de booking-service et une vraie base H2.
 * Les clients Feign sont remplaces par des fakes en memoire qui appliquent les regles des vrais services.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest {

    private static final long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingScheduler bookingScheduler;

    @MockBean
    private ClassClient classClient;

    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private NotificationClient notificationClient;

    private final FakeClassClient fakeClassService = new FakeClassClient();
    private final FakePaymentClient fakePaymentService = new FakePaymentClient();
    private final FakeNotificationClient fakeNotificationService = new FakeNotificationClient();

    @BeforeEach
    void wireFakes() {
        bookingRepository.deleteAll();
        doAnswer(delegatesTo(fakeClassService)).when(classClient).getFitnessClass(any());
        doAnswer(delegatesTo(fakeClassService)).when(classClient).incrementParticipants(any(), anyInt());
        doAnswer(delegatesTo(fakeClassService)).when(classClient).decrementParticipants(any(), anyInt());
        doAnswer(delegatesTo(fakePaymentService)).when(paymentClient).processPayment(any());
        doAnswer(delegatesTo(fakePaymentService)).when(paymentClient).getPaymentByBooking(any());
        doAnswer(delegatesTo(fakePaymentService)).when(paymentClient).refund(any());
        doAnswer(delegatesTo(fakeNotificationService)).when(notificationClient).send(any());
    }

    private String bookingPayload(Long classId, int spots) throws Exception {
        return objectMapper.writeValueAsString(Map.of("userId", USER_ID, "userEmail", "john@example.com",
                "userName", "John Doe", "classId", classId, "numberOfSpots", spots));
    }

    private String cardPayment() throws Exception {
        return objectMapper.writeValueAsString(Map.of("paymentMethod", "CREDIT_CARD", "cardLastFour", "1234",
                "transactionId", "txn_123456"));
    }

    private long createBooking(Long classId, int spots) throws Exception {
        String body = mockMvc.perform(post("/api/bookings").contentType("application/json")
                        .content(bookingPayload(classId, spots)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    // ---------------------------------------------------------------- tests exiges par le sujet

    @Test
    @Transactional
    void shouldCompleteFullBookingFlow() throws Exception {
        // 1. Create class
        Long classId = fakeClassService.addClass("Yoga Vinyasa", 10, 0, "20.00", LocalDateTime.now().plusDays(7));

        // 2. Create booking
        String created = mockMvc.perform(post("/api/bookings").contentType("application/json")
                        .content(bookingPayload(classId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.bookingReference").value(org.hamcrest.Matchers.matchesPattern("BK-[A-Z0-9]{5}")))
                .andExpect(jsonPath("$.totalAmount").value(40.0))
                .andExpect(jsonPath("$.className").value("Yoga Vinyasa"))
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(created).get("id").asLong();

        // 3. Confirm payment
        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId).contentType("application/json")
                        .content(cardPayment()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReference").exists());

        // 4. Verify booking status = CONFIRMED
        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 5. Verify spots decreased (places disponibles : 10 -> 8)
        assertThat(fakeClassService.getFitnessClass(classId).availableSpots()).isEqualTo(8);

        // 6. Verify notification sent
        assertThat(fakeNotificationService.typesSentTo(USER_ID))
                .containsExactly(NotificationType.BOOKING_CONFIRMATION, NotificationType.PAYMENT_CONFIRMATION);
    }

    @Test
    void shouldCancelExpiredBookings() throws Exception {
        // 1. Create booking with paymentDeadline in past
        Long classId = fakeClassService.addClass("CrossFit", 10, 0, "25.00", LocalDateTime.now().plusDays(3));
        long bookingId = createBooking(classId, 3);
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setPaymentDeadline(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(booking);
        assertThat(fakeClassService.participants(classId)).isEqualTo(3);
        mockMvc.perform(get("/api/bookings/expired"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(bookingId));

        // 2. Run scheduler
        int cancelled = bookingScheduler.cancelExpiredBookings();

        // 3. Verify status = CANCELLED
        assertThat(cancelled).isEqualTo(1);
        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Delai de paiement depasse"));

        // 4. Verify spots restored
        assertThat(fakeClassService.participants(classId)).isZero();
        assertThat(fakeNotificationService.typesSentTo(USER_ID)).last().isEqualTo(NotificationType.BOOKING_CANCELLED);
        mockMvc.perform(get("/api/bookings/expired")).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------------------------------------------------------------- scenarios de la collection Postman

    @Test
    void cancelWithinDeadline_refundsAndRestoresSpots() throws Exception {
        Long classId = fakeClassService.addClass("Zumba", 10, 4, "15.00", LocalDateTime.now().plusDays(5));
        long bookingId = createBooking(classId, 2);
        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId).contentType("application/json")
                .content(cardPayment())).andExpect(status().isOk());

        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(fakePaymentService.all()).singleElement().extracting(PaymentDto::status).isEqualTo(PaymentDto.REFUNDED);
        assertThat(fakeClassService.participants(classId)).isEqualTo(4);
    }

    @Test
    void overbooking_returns409() throws Exception {
        Long classId = fakeClassService.addClass("Spinning", 5, 0, "12.00", LocalDateTime.now().plusDays(2));
        createBooking(classId, 4);

        mockMvc.perform(post("/api/bookings").contentType("application/json").content(bookingPayload(classId, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Plus de places disponibles")));
        assertThat(fakeClassService.participants(classId)).isEqualTo(4);
    }

    @Test
    void confirmAfterPaymentDeadline_returns409() throws Exception {
        Long classId = fakeClassService.addClass("Pilates", 10, 0, "20.00", LocalDateTime.now().plusDays(2));
        long bookingId = createBooking(classId, 1);
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setPaymentDeadline(LocalDateTime.now().minusSeconds(1));
        bookingRepository.save(booking);

        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId).contentType("application/json")
                        .content(cardPayment()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("delai de paiement")));
        assertThat(fakePaymentService.all()).isEmpty();
    }

    @Test
    void cancelLessThan24hBeforeClass_returns409() throws Exception {
        Long classId = fakeClassService.addClass("Boxe", 10, 0, "30.00", LocalDateTime.now().plusHours(3));
        long bookingId = createBooking(classId, 1);

        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Annulation impossible")));
        assertThat(fakeClassService.participants(classId)).isEqualTo(1);
    }

    @Test
    void refusedPayment_returns402_cancelsBookingAndReleasesSpots() throws Exception {
        Long classId = fakeClassService.addClass("CrossFit Pro", 10, 0, "55.00", LocalDateTime.now().plusDays(4));
        long bookingId = createBooking(classId, 2); // 110 EUR >= 100 -> refuse

        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId).contentType("application/json")
                        .content(cardPayment()))
                .andExpect(status().isPaymentRequired());

        mockMvc.perform(get("/api/bookings/{id}", bookingId)).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(fakeClassService.participants(classId)).isZero();
    }

    @Test
    void invalidRequests_return400() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType("application/json").content(bookingPayload(1L, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("numberOfSpots")));

        Long classId = fakeClassService.addClass("Yoga", 10, 0, "20.00", LocalDateTime.now().plusDays(2));
        long bookingId = createBooking(classId, 1);
        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId).contentType("application/json")
                        .content("{\"paymentMethod\":\"CREDIT_CARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cardLastFour")));
    }

    @Test
    void unknownBookingOrClass_returns404() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}", 12345)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bookings").contentType("application/json").content(bookingPayload(999L, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void remindersAreSentOnceForConfirmedBookingsWithin24h() throws Exception {
        Long soon = fakeClassService.addClass("Yoga demain", 10, 0, "20.00", LocalDateTime.now().plusHours(20));
        Long later = fakeClassService.addClass("Yoga semaine pro", 10, 0, "20.00", LocalDateTime.now().plusDays(6));
        for (Long classId : new Long[]{soon, later}) {
            long id = createBooking(classId, 1);
            mockMvc.perform(patch("/api/bookings/{id}/confirm", id).contentType("application/json")
                    .content(cardPayment())).andExpect(status().isOk());
        }

        assertThat(bookingScheduler.sendClassReminders()).isEqualTo(1);
        assertThat(bookingScheduler.sendClassReminders()).isZero();
        assertThat(fakeNotificationService.typesSentTo(USER_ID)).filteredOn(t -> t == NotificationType.BOOKING_REMINDER)
                .hasSize(1);
    }

    @Test
    void userBookingsAndStatusFilter() throws Exception {
        Long classId = fakeClassService.addClass("Zumba", 20, 0, "10.00", LocalDateTime.now().plusDays(2));
        long first = createBooking(classId, 1);
        createBooking(classId, 1);
        mockMvc.perform(patch("/api/bookings/{id}/confirm", first).contentType("application/json")
                .content(cardPayment())).andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/user/{userId}", USER_ID)).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/bookings").param("status", BookingStatus.CONFIRMED.name()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(first));
    }
}
