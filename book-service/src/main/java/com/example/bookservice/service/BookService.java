package com.example.bookservice.service;

import com.example.bookservice.dto.BookRequest;
import com.example.bookservice.exception.BookNotFoundException;
import com.example.bookservice.exception.StockExhaustedException;
import com.example.bookservice.model.Book;
import com.example.bookservice.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    public Book findById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    public Book create(BookRequest request) {
        Book book = new Book(
                request.getTitle(),
                request.getAuthor(),
                request.getIsbn(),
                request.getTotalCopies(),
                request.getTotalCopies()
        );
        return bookRepository.save(book);
    }

    public Book update(Long id, BookRequest request) {
        Book existing = findById(id);
        existing.setTitle(request.getTitle());
        existing.setAuthor(request.getAuthor());
        existing.setIsbn(request.getIsbn());
        existing.setTotalCopies(request.getTotalCopies());
        if (existing.getAvailableCopies() > request.getTotalCopies()) {
            existing.setAvailableCopies(request.getTotalCopies());
        }
        return bookRepository.save(existing);
    }

    public void delete(Long id) {
        Book existing = findById(id);
        bookRepository.delete(existing);
    }

    @Transactional
    public Book decrementStock(Long id) {
        Book book = findById(id);
        if (book.getAvailableCopies() <= 0) {
            throw new StockExhaustedException(id);
        }
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        return bookRepository.save(book);
    }

    @Transactional
    public Book incrementStock(Long id) {
        Book book = findById(id);
        if (book.getAvailableCopies() < book.getTotalCopies()) {
            book.setAvailableCopies(book.getAvailableCopies() + 1);
        }
        return bookRepository.save(book);
    }
}
