package com.example.bookingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Evite qu'une confirmation et une annulation/expiration concurrentes s'ecrasent. */
    @Version
    private Long version;

    @Column(unique = true, nullable = false)
    private String bookingReference;

    // --- utilisateur (snapshot) ---
    private Long userId;
    private String userEmail;
    private String userName;

    // --- cours (snapshot pris au moment de la reservation) ---
    private Long classId;
    private String className;
    private LocalDateTime classDate;
    private String instructor;
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private Integer numberOfSpots;
    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    private LocalDateTime bookingDate;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private LocalDateTime paymentDeadline;
    private LocalDateTime cancellationDeadline;

    // --- suivi du saga ---
    private Long paymentId;
    private String paymentReference;
    private String cancellationReason;
    private LocalDateTime cancelledAt;
    private boolean reminderSent;

    protected Booking() {
    }

    public Booking(String bookingReference, Long userId, String userEmail, String userName, Long classId,
                   String className, LocalDateTime classDate, String instructor, BigDecimal price,
                   Integer numberOfSpots, LocalDateTime bookingDate, LocalDateTime paymentDeadline,
                   LocalDateTime cancellationDeadline) {
        this.bookingReference = bookingReference;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userName = userName;
        this.classId = classId;
        this.className = className;
        this.classDate = classDate;
        this.instructor = instructor;
        this.price = price;
        this.numberOfSpots = numberOfSpots;
        this.totalAmount = price.multiply(BigDecimal.valueOf(numberOfSpots));
        this.bookingDate = bookingDate;
        this.paymentDeadline = paymentDeadline;
        this.cancellationDeadline = cancellationDeadline;
        this.status = BookingStatus.PENDING_PAYMENT;
    }

    public void confirm(Long paymentId, String paymentReference) {
        this.paymentId = paymentId;
        this.paymentReference = paymentReference;
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel(String reason, LocalDateTime when) {
        this.status = BookingStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = when;
    }

    public void complete() {
        this.status = BookingStatus.COMPLETED;
    }

    public boolean isPaymentExpired(LocalDateTime now) {
        return now.isAfter(paymentDeadline);
    }

    public boolean isCancellationDeadlinePassed(LocalDateTime now) {
        return now.isAfter(cancellationDeadline);
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserName() {
        return userName;
    }

    public Long getClassId() {
        return classId;
    }

    public String getClassName() {
        return className;
    }

    public LocalDateTime getClassDate() {
        return classDate;
    }

    public String getInstructor() {
        return instructor;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getNumberOfSpots() {
        return numberOfSpots;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getBookingDate() {
        return bookingDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaymentDeadline() {
        return paymentDeadline;
    }

    public void setPaymentDeadline(LocalDateTime paymentDeadline) {
        this.paymentDeadline = paymentDeadline;
    }

    public LocalDateTime getCancellationDeadline() {
        return cancellationDeadline;
    }

    public void setCancellationDeadline(LocalDateTime cancellationDeadline) {
        this.cancellationDeadline = cancellationDeadline;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(boolean reminderSent) {
        this.reminderSent = reminderSent;
    }
}
