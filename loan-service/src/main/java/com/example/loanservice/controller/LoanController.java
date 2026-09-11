package com.example.loanservice.controller;

import com.example.loanservice.dto.LoanRequest;
import com.example.loanservice.dto.LoanResponse;
import com.example.loanservice.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<LoanResponse> findAll() {
        return loanService.findAll().stream()
                .map(LoanResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public LoanResponse findById(@PathVariable("id") Long id) {
        return LoanResponse.from(loanService.findById(id));
    }

    @PostMapping
    public ResponseEntity<LoanResponse> create(@Valid @RequestBody LoanRequest request) {
        LoanResponse created = LoanResponse.from(loanService.createLoan(request));
        return ResponseEntity.created(URI.create("/api/loans/" + created.getId())).body(created);
    }

    @PatchMapping("/{id}/return")
    public LoanResponse returnLoan(@PathVariable("id") Long id) {
        return LoanResponse.from(loanService.returnLoan(id));
    }
}
