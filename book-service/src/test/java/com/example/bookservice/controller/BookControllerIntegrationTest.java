package com.example.bookservice.controller;

import com.example.bookservice.model.Book;
import com.example.bookservice.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void createBook_initializesAvailableCopiesToTotalCopies() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Fondation",
                "author", "Isaac Asimov",
                "isbn", "9782070415811",
                "totalCopies", 4
        ));

        mockMvc.perform(post("/api/books")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCopies").value(4))
                .andExpect(jsonPath("$.availableCopies").value(4));
    }

    @Test
    void decrementStock_whenNoCopiesAvailable_returns409() throws Exception {
        Book book = bookRepository.save(new Book("Livre epuise", "Auteur", "ISBN-0", 1, 0));

        mockMvc.perform(patch("/api/books/{id}/decrement-stock", book.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void decrementStock_whenCopyAvailable_returns200AndDecrements() throws Exception {
        Book book = bookRepository.save(new Book("Livre disponible", "Auteur", "ISBN-1", 2, 2));

        mockMvc.perform(patch("/api/books/{id}/decrement-stock", book.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCopies").value(1));
    }

    @Test
    void getBook_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/books/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void createBook_withBlankTitle_returns400() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "",
                "author", "Auteur",
                "isbn", "ISBN-2",
                "totalCopies", 2
        ));

        mockMvc.perform(post("/api/books")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}
