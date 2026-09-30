package com.example.classservice.controller;

import com.example.classservice.dto.ClassSearchCriteria;
import com.example.classservice.dto.FitnessClassRequest;
import com.example.classservice.dto.FitnessClassResponse;
import com.example.classservice.dto.PageResponse;
import com.example.classservice.model.ClassCategory;
import com.example.classservice.model.ClassLevel;
import com.example.classservice.model.ClassStatus;
import com.example.classservice.service.FitnessClassService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/classes")
@Validated
public class FitnessClassController {

    private final FitnessClassService service;

    public FitnessClassController(FitnessClassService service) {
        this.service = service;
    }

    /**
     * Liste paginee avec filtres optionnels, ex :
     * ?category=YOGA&level=BEGINNER&dateFrom=2026-09-10&dateTo=2026-09-20&location=Paris&page=0&size=10&sort=dateTime,asc
     */
    @GetMapping
    public PageResponse<FitnessClassResponse> findAll(
            @RequestParam(required = false) ClassCategory category,
            @RequestParam(required = false) ClassLevel level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String instructor,
            @RequestParam(required = false) ClassStatus status,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        ClassSearchCriteria criteria = new ClassSearchCriteria(category, level, dateFrom, dateTo, location,
                instructor, status);
        return PageResponse.from(service.search(criteria, pageable), FitnessClassResponse::from);
    }

    /**
     * Recherche de cours reservables : memes filtres que GET /api/classes, mais par defaut
     * seuls les cours SCHEDULED sont retournes.
     */
    @GetMapping("/search")
    public PageResponse<FitnessClassResponse> search(
            @RequestParam(required = false) ClassCategory category,
            @RequestParam(required = false) ClassLevel level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String instructor,
            @RequestParam(defaultValue = "SCHEDULED") ClassStatus status,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return findAll(category, level, dateFrom, dateTo, location, instructor, status, pageable);
    }

    @GetMapping("/{id}")
    public FitnessClassResponse findById(@PathVariable("id") Long id) {
        return FitnessClassResponse.from(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<FitnessClassResponse> create(@Valid @RequestBody FitnessClassRequest request) {
        FitnessClassResponse created = FitnessClassResponse.from(service.create(request));
        return ResponseEntity.created(URI.create("/api/classes/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public FitnessClassResponse update(@PathVariable("id") Long id, @Valid @RequestBody FitnessClassRequest request) {
        return FitnessClassResponse.from(service.update(id, request));
    }

    /** Annule le cours (statut CANCELLED) plutot que de le supprimer physiquement. */
    @DeleteMapping("/{id}")
    public FitnessClassResponse cancel(@PathVariable("id") Long id) {
        return FitnessClassResponse.from(service.cancel(id));
    }

    /** Appele par booking-service : 409 si currentParticipants + spots > maxParticipants. */
    @PatchMapping("/{id}/increment")
    public FitnessClassResponse increment(@PathVariable("id") Long id,
                                          @RequestParam(defaultValue = "1") @Min(1) @Max(30) int spots) {
        return FitnessClassResponse.from(service.incrementParticipants(id, spots));
    }

    /** Appele par booking-service lors d'une annulation ou d'une expiration de paiement. */
    @PatchMapping("/{id}/decrement")
    public FitnessClassResponse decrement(@PathVariable("id") Long id,
                                          @RequestParam(defaultValue = "1") @Min(1) @Max(30) int spots) {
        return FitnessClassResponse.from(service.decrementParticipants(id, spots));
    }
}
