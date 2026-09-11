package com.example.bookservice.controller;

import com.example.bookservice.dto.BookRequest;
import com.example.bookservice.dto.BookResponse;
import com.example.bookservice.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public List<BookResponse> findAll() {
        return bookService.findAll().stream()
                .map(BookResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public BookResponse findById(@PathVariable("id") Long id) {
        return BookResponse.from(bookService.findById(id));
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = BookResponse.from(bookService.create(request));
        return ResponseEntity.created(URI.create("/api/books/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable("id") Long id, @Valid @RequestBody BookRequest request) {
        return BookResponse.from(bookService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/decrement-stock")
    public BookResponse decrementStock(@PathVariable("id") Long id) {
        return BookResponse.from(bookService.decrementStock(id));
    }

    @PatchMapping("/{id}/increment-stock")
    public BookResponse incrementStock(@PathVariable("id") Long id) {
        return BookResponse.from(bookService.incrementStock(id));
    }
}
