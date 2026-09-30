package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.exception.PaymentAlreadyCompletedException;
import com.example.paymentservice.exception.RefundNotAllowedException;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentMethod;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository repository;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, new PaymentGatewaySimulator(new BigDecimal("100.00")));
        lenient().when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PaymentRequest request(String amount, PaymentMethod method) {
        PaymentRequest request = new PaymentRequest();
        request.setBookingId(1L);
        request.setBookingReference("BK-ABCDE");
        request.setUserId(7L);
        request.setAmount(new BigDecimal(amount));
        request.setPaymentMethod(method);
        request.setCardLastFour("1234");
        return request;
    }

    @Test
    void processPayment_belowThreshold_isAccepted() {
        Payment payment = service.processPayment(request("99.99", PaymentMethod.CREDIT_CARD));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaymentReference()).matches("PAY-[A-Z0-9]{5}");
        assertThat(payment.getCardLastFour()).isEqualTo("1234");
        assertThat(payment.getTransactionId()).startsWith("txn_");
        assertThat(payment.getPaymentDate()).isNotNull();
    }

    @Test
    void processPayment_atOrAboveThreshold_isRejected() {
        Payment exactly100 = service.processPayment(request("100.00", PaymentMethod.PAYPAL));
        Payment above = service.processPayment(request("150.00", PaymentMethod.STRIPE));

        assertThat(exactly100.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(exactly100.getFailureReason()).contains("refuse");
        assertThat(above.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void processPayment_nonCardMethod_doesNotKeepCardDigits() {
        Payment payment = service.processPayment(request("20.00", PaymentMethod.PAYPAL));

        assertThat(payment.getCardLastFour()).isNull();
    }

    @Test
    void processPayment_whenBookingAlreadyPaid_throwsConflict() {
        when(repository.existsByBookingIdAndStatus(1L, PaymentStatus.SUCCESS)).thenReturn(true);

        assertThrows(PaymentAlreadyCompletedException.class,
                () -> service.processPayment(request("20.00", PaymentMethod.CREDIT_CARD)));
        verify(repository, never()).save(any());
    }

    @Test
    void refund_successfulPayment_marksRefunded() {
        Payment payment = service.processPayment(request("40.00", PaymentMethod.CREDIT_CARD));
        when(repository.findById(5L)).thenReturn(Optional.of(payment));

        Payment refunded = service.refund(5L);

        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getRefundDate()).isNotNull();
    }

    @Test
    void refund_failedOrAlreadyRefundedPayment_isRejected() {
        Payment failed = service.processPayment(request("120.00", PaymentMethod.CREDIT_CARD));
        when(repository.findById(5L)).thenReturn(Optional.of(failed));

        assertThrows(RefundNotAllowedException.class, () -> service.refund(5L));
    }
}
