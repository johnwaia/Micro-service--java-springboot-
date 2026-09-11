package com.example.loanservice.service;

import com.example.loanservice.client.BookClient;
import com.example.loanservice.dto.BookDto;
import com.example.loanservice.dto.LoanRequest;
import com.example.loanservice.exception.BookNotFoundException;
import com.example.loanservice.exception.BookUnavailableException;
import com.example.loanservice.exception.LoanAlreadyReturnedException;
import com.example.loanservice.model.Loan;
import com.example.loanservice.model.LoanStatus;
import com.example.loanservice.repository.LoanRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private BookClient bookClient;

    @InjectMocks
    private LoanService loanService;

    private BookDto bookWithAvailableCopies(long id, String title, int available) {
        BookDto book = new BookDto();
        book.setId(id);
        book.setTitle(title);
        book.setAuthor("Auteur");
        book.setIsbn("ISBN");
        book.setTotalCopies(5);
        book.setAvailableCopies(available);
        return book;
    }

    private FeignException feignExceptionWithStatus(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/books/1",
                Collections.emptyMap(), null, new RequestTemplate());
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Collections.emptyMap())
                .build();
        return FeignException.errorStatus("BookClient#call", response);
    }

    @Test
    void createLoan_whenBookDoesNotExist_throwsBookNotFoundException() {
        when(bookClient.getBook(99L)).thenThrow(feignExceptionWithStatus(404));

        LoanRequest request = new LoanRequest();
        request.setMemberName("Bob");
        request.setBookId(99L);

        assertThrows(BookNotFoundException.class, () -> loanService.createLoan(request));
        verify(bookClient, never()).decrementStock(anyLong());
        verify(loanRepository, never()).save(any());
    }

    @Test
    void createLoan_whenNoAvailableCopies_throwsBookUnavailable_withoutCallingDecrementStock() {
        when(bookClient.getBook(3L)).thenReturn(bookWithAvailableCopies(3L, "Dune", 0));

        LoanRequest request = new LoanRequest();
        request.setMemberName("Bob");
        request.setBookId(3L);

        assertThrows(BookUnavailableException.class, () -> loanService.createLoan(request));
        verify(bookClient, never()).decrementStock(anyLong());
        verify(loanRepository, never()).save(any());
    }

    @Test
    void createLoan_whenConcurrentRequestExhaustsStockBetweenCheckAndDecrement_throwsBookUnavailable() {
        when(bookClient.getBook(3L)).thenReturn(bookWithAvailableCopies(3L, "Dune", 1));
        when(bookClient.decrementStock(3L)).thenThrow(feignExceptionWithStatus(409));

        LoanRequest request = new LoanRequest();
        request.setMemberName("Bob");
        request.setBookId(3L);

        assertThrows(BookUnavailableException.class, () -> loanService.createLoan(request));
        verify(loanRepository, never()).save(any());
    }

    @Test
    void createLoan_whenSuccessful_createsActiveLoanWithDueDateInFourteenDays() {
        when(bookClient.getBook(3L)).thenReturn(bookWithAvailableCopies(3L, "Dune", 2));
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanRequest request = new LoanRequest();
        request.setMemberName("Bob");
        request.setBookId(3L);

        Loan created = loanService.createLoan(request);

        verify(bookClient).decrementStock(3L);
        ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
        verify(loanRepository).save(captor.capture());

        assertThat(created.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(created.getBookTitle()).isEqualTo("Dune");
        assertThat(created.getDueDate()).isEqualTo(created.getLoanDate().plusDays(14));
    }

    @Test
    void returnLoan_whenAlreadyReturned_throwsLoanAlreadyReturnedException() {
        Loan loan = new Loan("Bob", 3L, "Dune", LocalDate.now().minusDays(5), LocalDate.now().plusDays(9));
        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(LocalDate.now());
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        assertThrows(LoanAlreadyReturnedException.class, () -> loanService.returnLoan(1L));
        verify(bookClient, never()).incrementStock(anyLong());
    }

    @Test
    void returnLoan_whenActive_incrementsStockAndMarksReturned() {
        Loan loan = new Loan("Bob", 3L, "Dune", LocalDate.now().minusDays(5), LocalDate.now().plusDays(9));
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan returned = loanService.returnLoan(1L);

        verify(bookClient).incrementStock(3L);
        assertThat(returned.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(returned.getReturnDate()).isEqualTo(LocalDate.now());
    }
}
