package com.example.bookservice.service;

import com.example.bookservice.dto.BookRequest;
import com.example.bookservice.exception.BookNotFoundException;
import com.example.bookservice.exception.StockExhaustedException;
import com.example.bookservice.model.Book;
import com.example.bookservice.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    void create_initializesAvailableCopiesToTotalCopies() {
        BookRequest request = new BookRequest();
        request.setTitle("Dune");
        request.setAuthor("Frank Herbert");
        request.setIsbn("9782266233200");
        request.setTotalCopies(5);

        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book created = bookService.create(request);

        assertThat(created.getTotalCopies()).isEqualTo(5);
        assertThat(created.getAvailableCopies()).isEqualTo(5);
    }

    @Test
    void decrementStock_whenCopiesAvailable_decrements() {
        Book book = new Book("Dune", "Frank Herbert", "9782266233200", 5, 3);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book updated = bookService.decrementStock(1L);

        assertThat(updated.getAvailableCopies()).isEqualTo(2);
    }

    @Test
    void decrementStock_whenNoCopiesAvailable_throwsStockExhausted() {
        Book book = new Book("Dune", "Frank Herbert", "9782266233200", 5, 0);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThrows(StockExhaustedException.class, () -> bookService.decrementStock(1L));
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void decrementStock_whenBookDoesNotExist_throwsBookNotFound() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BookNotFoundException.class, () -> bookService.decrementStock(99L));
    }

    @Test
    void incrementStock_neverExceedsTotalCopies() {
        Book book = new Book("Dune", "Frank Herbert", "9782266233200", 5, 5);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book updated = bookService.incrementStock(1L);

        assertThat(updated.getAvailableCopies()).isEqualTo(5);
    }

    @Test
    void incrementStock_whenBelowTotal_increments() {
        Book book = new Book("Dune", "Frank Herbert", "9782266233200", 5, 2);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book updated = bookService.incrementStock(1L);

        assertThat(updated.getAvailableCopies()).isEqualTo(3);
    }
}
