package com.example.loanservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String memberName;

    private Long bookId;

    private String bookTitle;

    private LocalDate loanDate;

    private LocalDate dueDate;

    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    private LoanStatus status;

    protected Loan() {
    }

    public Loan(String memberName, Long bookId, String bookTitle, LocalDate loanDate, LocalDate dueDate) {
        this.memberName = memberName;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = null;
        this.status = LoanStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getMemberName() {
        return memberName;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public LocalDate getLoanDate() {
        return loanDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }
}
