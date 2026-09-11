package com.example.loanservice.controller;

import com.example.loanservice.client.BookClient;
import com.example.loanservice.dto.BookDto;
import com.example.loanservice.model.Loan;
import com.example.loanservice.model.LoanStatus;
import com.example.loanservice.repository.LoanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoanRepository loanRepository;

    @MockBean
    private BookClient bookClient;

    private BookDto bookDto(long id, String title, int totalCopies, int availableCopies) {
        BookDto book = new BookDto();
        book.setId(id);
        book.setTitle(title);
        book.setAuthor("Auteur");
        book.setIsbn("ISBN");
        book.setTotalCopies(totalCopies);
        book.setAvailableCopies(availableCopies);
        return book;
    }

    private FeignException notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/books/999",
                Collections.emptyMap(), null, new RequestTemplate());
        Response response = Response.builder().status(404).reason("Not Found").request(request)
                .headers(Collections.emptyMap()).build();
        return FeignException.errorStatus("BookClient#getBook", response);
    }

    @Test
    void createLoan_whenBookDoesNotExist_returns400() throws Exception {
        when(bookClient.getBook(999L)).thenThrow(notFound());

        String payload = objectMapper.writeValueAsString(Map.of("memberName", "Alice", "bookId", 999));

        mockMvc.perform(post("/api/loans").contentType("application/json").content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLoan_whenNoStockAvailable_returns409AndDoesNotDecrementStock() throws Exception {
        when(bookClient.getBook(5L)).thenReturn(bookDto(5L, "Dune", 1, 0));

        String payload = objectMapper.writeValueAsString(Map.of("memberName", "Alice", "bookId", 5));

        mockMvc.perform(post("/api/loans").contentType("application/json").content(payload))
                .andExpect(status().isConflict());

        verify(bookClient, never()).decrementStock(anyLong());
    }

    @Test
    void createLoan_whenStockAvailable_returns201WithDueDateInFourteenDays() throws Exception {
        when(bookClient.getBook(7L)).thenReturn(bookDto(7L, "Fondation", 3, 2));

        String payload = objectMapper.writeValueAsString(Map.of("memberName", "Alice", "bookId", 7));

        mockMvc.perform(post("/api/loans").contentType("application/json").content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bookTitle").value("Fondation"))
                .andExpect(jsonPath("$.dueDate").value(LocalDate.now().plusDays(14).toString()));

        verify(bookClient).decrementStock(7L);
    }

    @Test
    void returnLoan_whenAlreadyReturned_returns409() throws Exception {
        Loan loan = new Loan("Bob", 1L, "Dune", LocalDate.now().minusDays(20), LocalDate.now().minusDays(6));
        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(LocalDate.now().minusDays(3));
        loan = loanRepository.save(loan);

        mockMvc.perform(patch("/api/loans/{id}/return", loan.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void returnLoan_whenActive_returns200AndIncrementsStock() throws Exception {
        Loan loan = loanRepository.save(new Loan("Bob", 2L, "1984", LocalDate.now().minusDays(1), LocalDate.now().plusDays(13)));

        mockMvc.perform(patch("/api/loans/{id}/return", loan.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));

        verify(bookClient).incrementStock(2L);
    }
}
