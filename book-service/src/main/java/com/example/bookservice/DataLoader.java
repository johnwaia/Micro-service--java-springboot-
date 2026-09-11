package com.example.bookservice;

import com.example.bookservice.model.Book;
import com.example.bookservice.repository.BookRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final BookRepository bookRepository;

    public DataLoader(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public void run(String... args) {
        bookRepository.save(new Book("Le Petit Prince", "Antoine de Saint-Exupery", "9782070408504", 3, 3));
        bookRepository.save(new Book("1984", "George Orwell", "9782070368228", 2, 2));
        bookRepository.save(new Book("L'Etranger", "Albert Camus", "9782070360024", 1, 1));
    }
}
