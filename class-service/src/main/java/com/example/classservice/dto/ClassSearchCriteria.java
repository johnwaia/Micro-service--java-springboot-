package com.example.classservice.dto;

import com.example.classservice.model.ClassCategory;
import com.example.classservice.model.ClassLevel;
import com.example.classservice.model.ClassStatus;

import java.time.LocalDate;

/**
 * Filtres optionnels de GET /api/classes et GET /api/classes/search.
 * dateFrom et dateTo sont inclusifs (jour entier).
 */
public record ClassSearchCriteria(
        ClassCategory category,
        ClassLevel level,
        LocalDate dateFrom,
        LocalDate dateTo,
        String location,
        String instructor,
        ClassStatus status) {
}
