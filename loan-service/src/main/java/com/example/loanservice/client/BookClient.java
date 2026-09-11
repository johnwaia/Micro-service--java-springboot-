package com.example.loanservice.client;

import com.example.loanservice.dto.BookDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;

@FeignClient(name = "book-service")
public interface BookClient {

    @GetMapping("/api/books/{id}")
    BookDto getBook(@PathVariable("id") Long id);

    @PatchMapping("/api/books/{id}/decrement-stock")
    BookDto decrementStock(@PathVariable("id") Long id);

    @PatchMapping("/api/books/{id}/increment-stock")
    BookDto incrementStock(@PathVariable("id") Long id);
}
