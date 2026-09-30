package com.example.bookingservice.service;

import com.example.bookingservice.client.ClassClient;
import com.example.bookingservice.client.dto.FitnessClassDto;
import com.example.bookingservice.client.dto.NotificationRequest.NotificationType;
import com.example.bookingservice.client.dto.PaymentDto;
import com.example.bookingservice.dto.BookingRequest;
import com.example.bookingservice.dto.ConfirmBookingRequest;
import com.example.bookingservice.exception.CancellationNotAllowedException;
import com.example.bookingservice.exception.ClassNotBookableException;
import com.example.bookingservice.exception.FitnessClassNotFoundException;
import com.example.bookingservice.exception.InvalidBookingStateException;
import com.example.bookingservice.exception.NoSpotsAvailableException;
import com.example.bookingservice.exception.PaymentExpiredException;
import com.example.bookingservice.exception.PaymentFailedException;
import com.example.bookingservice.exception.RemoteServiceException;
import com.example.bookingservice.exception.ServiceUnavailableException;
import com.example.bookingservice.model.Booking;
import com.example.bookingservice.model.BookingStatus;
import com.example.bookingservice.model.PaymentMethod;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.support.FakeClassClient;
import com.example.bookingservice.support.FakeNotificationClient;
import com.example.bookingservice.support.FakePaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du saga : la base est simulee par un mock Mockito, les services distants par des fakes en memoire.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private BookingRepository repository;

    private final Map<Long, Booking> store = new HashMap<>();
    private long nextId = 1;

    private FakeClassClient classClient;
    private FakePaymentClient paymentClient;
    private FakeNotificationClient notificationClient;
    private BookingService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                ReflectionTestUtils.setField(b, "id", nextId++);
            }
            store.put(b.getId(), b);
            return b;
        });
        lenient().when(repository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));

        classClient = new FakeClassClient();
        paymentClient = new FakePaymentClient();
        notificationClient = new FakeNotificationClient();
        service = newService(classClient);
    }

    private BookingService newService(ClassClient classClientToUse) {
        return new BookingService(repository, classClientToUse, paymentClient, new BookingNotifier(notificationClient),
                new BookingReferenceGenerator(repository), Clock.systemDefaultZone(), Duration.ofHours(1),
                Duration.ofHours(24));
    }

    private BookingRequest request(Long classId, int spots) {
        return new BookingRequest(USER_ID, "john@example.com", "John Doe", classId, spots);
    }

    private ConfirmBookingRequest validCard() {
        return new ConfirmBookingRequest(PaymentMethod.CREDIT_CARD, "1234", "txn_123456");
    }

    private Long classInOneWeek(int max, int current, String price) {
        return classClient.addClass("Yoga Vinyasa", max, current, price, LocalDateTime.now().plusDays(7));
    }

    // ---------------------------------------------------------------- tests exiges par le sujet

    @Test
    void shouldCreateBooking_whenSpotsAvailable() {
        // Given: class with 10 spots, 5 current participants
        Long classId = classInOneWeek(10, 5, "20.00");

        // When: booking 2 spots
        Booking booking = service.createBooking(request(classId, 2));

        // Then: status = PENDING_PAYMENT, spots = 7
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(classClient.participants(classId)).isEqualTo(7);

        assertThat(booking.getBookingReference()).matches("BK-[A-Z0-9]{5}");
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("40.00");
        assertThat(booking.getClassName()).isEqualTo("Yoga Vinyasa");
        assertThat(booking.getPaymentDeadline()).isEqualTo(booking.getBookingDate().plusHours(1));
        assertThat(booking.getCancellationDeadline()).isEqualTo(booking.getClassDate().minusHours(24));
        assertThat(notificationClient.sent()).hasSize(1);
        assertThat(notificationClient.sent().get(0).type()).isEqualTo(NotificationType.BOOKING_CONFIRMATION);
        assertThat(notificationClient.sent().get(0).content()).contains("en attente de paiement", "Payez avant");
    }

    @Test
    void shouldThrowException_whenNoSpotsAvailable() {
        // Given: class with 10 spots, 9 current participants
        Long classId = classInOneWeek(10, 9, "20.00");

        // When: booking 2 spots / Then: NoSpotsAvailableException
        assertThrows(NoSpotsAvailableException.class, () -> service.createBooking(request(classId, 2)));
        assertThat(classClient.participants(classId)).isEqualTo(9);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCancelBookingAndRefund_whenWithinDeadline() {
        // Given: confirmed booking, cancellationDeadline in future
        Long classId = classInOneWeek(10, 5, "20.00");
        Booking booking = service.createBooking(request(classId, 2));
        service.confirmBooking(booking.getId(), validCard());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(classClient.participants(classId)).isEqualTo(7);

        // When: cancel booking
        Booking cancelled = service.cancelBooking(booking.getId());

        // Then: status = CANCELLED, payment refunded, spots decreased
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(paymentClient.all()).singleElement()
                .extracting(PaymentDto::status).isEqualTo(PaymentDto.REFUNDED);
        assertThat(classClient.participants(classId)).isEqualTo(5);
        assertThat(notificationClient.typesSentTo(USER_ID)).containsExactly(NotificationType.BOOKING_CONFIRMATION,
                NotificationType.PAYMENT_CONFIRMATION, NotificationType.BOOKING_CANCELLED);
    }

    // ---------------------------------------------------------------- Cas 1 / 2 : reservation

    @Test
    void createBooking_whenSomeoneTakesTheLastSpotsConcurrently_throwsNoSpotsWithoutSaving() {
        // getFitnessClass voit encore 5 places, mais increment repond 409 (reservation concurrente)
        ClassClient racingClassClient = mock(ClassClient.class);
        when(racingClassClient.getFitnessClass(42L)).thenReturn(new FitnessClassDto(42L, "CrossFit", "Karim",
                "Paris", LocalDateTime.now().plusDays(2), new BigDecimal("25.00"), 10, 5, "SCHEDULED"));
        when(racingClassClient.incrementParticipants(42L, 2))
                .thenThrow(new RemoteServiceException("class-service", 409, "Plus de places"));

        NoSpotsAvailableException ex = assertThrows(NoSpotsAvailableException.class,
                () -> newService(racingClassClient).createBooking(request(42L, 2)));

        assertThat(ex.getMessage()).contains("Plus de places disponibles");
        verify(repository, never()).save(any());
        verify(racingClassClient, never()).decrementParticipants(anyLong(), anyInt());
        assertThat(notificationClient.sent()).isEmpty();
    }

    @Test
    void createBooking_whenClassDoesNotExist_throwsNotFound() {
        assertThrows(FitnessClassNotFoundException.class, () -> service.createBooking(request(999L, 1)));
    }

    @Test
    void createBooking_whenClassCancelled_throwsNotBookable() {
        Long classId = classInOneWeek(10, 0, "20.00");
        classClient.cancelClass(classId);

        assertThrows(ClassNotBookableException.class, () -> service.createBooking(request(classId, 1)));
    }

    @Test
    void createBooking_whenClassServiceDown_propagatesUnavailable() {
        Long classId = classInOneWeek(10, 0, "20.00");
        classClient.setDown(true);

        assertThrows(ServiceUnavailableException.class, () -> service.createBooking(request(classId, 1)));
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------- Cas 3 : paiement

    @Test
    void confirmBooking_whenPaymentAccepted_confirmsAndNotifies() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 2));

        Booking confirmed = service.confirmBooking(booking.getId(), validCard());

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.getPaymentId()).isNotNull();
        assertThat(confirmed.getPaymentReference()).startsWith("PAY-");
        assertThat(notificationClient.typesSentTo(USER_ID)).contains(NotificationType.PAYMENT_CONFIRMATION);
    }

    @Test
    void confirmBooking_whenPaymentDeadlinePassed_throwsPaymentExpired() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 1));
        booking.setPaymentDeadline(LocalDateTime.now().minusMinutes(1));

        assertThrows(PaymentExpiredException.class, () -> service.confirmBooking(booking.getId(), validCard()));
        assertThat(paymentClient.all()).isEmpty();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void confirmBooking_whenAlreadyConfirmed_throwsInvalidState() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 1));
        service.confirmBooking(booking.getId(), validCard());

        assertThrows(InvalidBookingStateException.class, () -> service.confirmBooking(booking.getId(), validCard()));
        assertThat(paymentClient.all()).hasSize(1);
    }

    @Test
    void confirmBooking_whenPaymentRefused_cancelsBookingAndReleasesSpots() {
        // 2 x 60 EUR = 120 EUR >= 100 EUR -> refuse par la simulation
        Long classId = classInOneWeek(10, 3, "60.00");
        Booking booking = service.createBooking(request(classId, 2));
        assertThat(classClient.participants(classId)).isEqualTo(5);

        assertThrows(PaymentFailedException.class, () -> service.confirmBooking(booking.getId(), validCard()));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).contains("Paiement refuse");
        assertThat(classClient.participants(classId)).isEqualTo(3);
        assertThat(notificationClient.typesSentTo(USER_ID)).last().isEqualTo(NotificationType.BOOKING_CANCELLED);
    }

    @Test
    void confirmBooking_whenPaymentServiceDown_keepsBookingPendingForRetry() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 2));
        paymentClient.setDown(true);

        assertThrows(ServiceUnavailableException.class, () -> service.confirmBooking(booking.getId(), validCard()));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(classClient.participants(classId)).isEqualTo(2);

        paymentClient.setDown(false);
        assertThat(service.confirmBooking(booking.getId(), validCard()).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ---------------------------------------------------------------- Cas 4 : annulation

    @Test
    void cancelBooking_pendingPayment_releasesSpotsWithoutRefund() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 3));

        service.cancelBooking(booking.getId());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(classClient.participants(classId)).isZero();
        assertThat(paymentClient.all()).isEmpty();
    }

    @Test
    void cancelBooking_lessThan24hBeforeClass_isRefused() {
        Long classId = classClient.addClass("Boxe", 10, 0, "20.00", LocalDateTime.now().plusHours(5));
        Booking booking = service.createBooking(request(classId, 1));
        service.confirmBooking(booking.getId(), validCard());

        assertThrows(CancellationNotAllowedException.class, () -> service.cancelBooking(booking.getId()));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(classClient.participants(classId)).isEqualTo(1);
        assertThat(paymentClient.all()).singleElement().extracting(PaymentDto::status).isEqualTo(PaymentDto.SUCCESS);
    }

    @Test
    void cancelBooking_alreadyCancelled_isRefused() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 1));
        service.cancelBooking(booking.getId());

        assertThrows(InvalidBookingStateException.class, () -> service.cancelBooking(booking.getId()));
        assertThat(classClient.participants(classId)).isZero();
    }

    @Test
    void cancelBooking_whenRefundFails_compensatesByReReservingSpots() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 2));
        service.confirmBooking(booking.getId(), validCard());
        paymentClient.setDown(true);

        assertThrows(ServiceUnavailableException.class, () -> service.cancelBooking(booking.getId()));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(classClient.participants(classId)).isEqualTo(2);
    }

    @Test
    void completeBooking_onlyFromConfirmed() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 1));

        assertThrows(InvalidBookingStateException.class, () -> service.completeBooking(booking.getId()));

        service.confirmBooking(booking.getId(), validCard());
        assertThat(service.completeBooking(booking.getId()).getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- scheduler

    @Test
    void expireBooking_cancelsAndReleasesSpots() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 2));
        booking.setPaymentDeadline(LocalDateTime.now().minusMinutes(5));

        assertThat(service.expireBooking(booking)).isTrue();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(classClient.participants(classId)).isZero();
    }

    @Test
    void expireBooking_whenClassServiceDown_leavesBookingPendingForNextRun() {
        Long classId = classInOneWeek(10, 0, "20.00");
        Booking booking = service.createBooking(request(classId, 2));
        booking.setPaymentDeadline(LocalDateTime.now().minusMinutes(5));
        classClient.setDown(true);

        assertThrows(ServiceUnavailableException.class, () -> service.expireBooking(booking));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void sendReminders_sendsOncePerBooking() {
        Long classId = classClient.addClass("Pilates", 10, 0, "20.00", LocalDateTime.now().plusHours(20));
        Booking booking = service.createBooking(request(classId, 1));
        service.confirmBooking(booking.getId(), validCard());
        when(repository.findByStatusAndReminderSentFalseAndClassDateBetween(any(), any(), any()))
                .thenReturn(List.of(booking));

        assertThat(service.sendReminders()).isEqualTo(1);

        assertThat(booking.isReminderSent()).isTrue();
        assertThat(notificationClient.typesSentTo(USER_ID)).last().isEqualTo(NotificationType.BOOKING_REMINDER);
    }
}
