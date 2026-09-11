package com.example.loanservice.service;

import com.example.loanservice.client.BookClient;
import com.example.loanservice.dto.BookDto;
import com.example.loanservice.dto.LoanRequest;
import com.example.loanservice.exception.BookNotFoundException;
import com.example.loanservice.exception.BookUnavailableException;
import com.example.loanservice.exception.LoanAlreadyReturnedException;
import com.example.loanservice.exception.LoanNotFoundException;
import com.example.loanservice.model.Loan;
import com.example.loanservice.model.LoanStatus;
import com.example.loanservice.repository.LoanRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class LoanService {

    private static final int LOAN_DURATION_DAYS = 14;

    private final LoanRepository loanRepository;
    private final BookClient bookClient;

    public LoanService(LoanRepository loanRepository, BookClient bookClient) {
        this.loanRepository = loanRepository;
        this.bookClient = bookClient;
    }

    public List<Loan> findAll() {
        return loanRepository.findAll();
    }

    public Loan findById(Long id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new LoanNotFoundException(id));
    }

    public Loan createLoan(LoanRequest request) {
        BookDto book = fetchBook(request.getBookId());

        if (book.getAvailableCopies() == null || book.getAvailableCopies() == 0) {
            throw new BookUnavailableException(request.getBookId());
        }

        decrementStock(request.getBookId());

        LocalDate loanDate = LocalDate.now();
        Loan loan = new Loan(request.getMemberName(), request.getBookId(), book.getTitle(),
                loanDate, loanDate.plusDays(LOAN_DURATION_DAYS));
        return loanRepository.save(loan);
    }

    public Loan returnLoan(Long id) {
        Loan loan = findById(id);

        if (loan.getStatus() == LoanStatus.RETURNED) {
            throw new LoanAlreadyReturnedException(id);
        }

        bookClient.incrementStock(loan.getBookId());

        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(LocalDate.now());
        return loanRepository.save(loan);
    }

    private BookDto fetchBook(Long bookId) {
        try {
            return bookClient.getBook(bookId);
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                throw new BookNotFoundException(bookId);
            }
            throw ex;
        }
    }

    private void decrementStock(Long bookId) {
        try {
            bookClient.decrementStock(bookId);
        } catch (FeignException ex) {
            if (ex.status() == 409) {
                throw new BookUnavailableException(bookId);
            }
            throw ex;
        }
    }
}
