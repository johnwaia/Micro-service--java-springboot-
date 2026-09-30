package com.example.classservice.repository;

import com.example.classservice.dto.ClassSearchCriteria;
import com.example.classservice.model.FitnessClass;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class FitnessClassSpecifications {

    private FitnessClassSpecifications() {
    }

    public static Specification<FitnessClass> matching(ClassSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.category() != null) {
                predicates.add(cb.equal(root.get("category"), criteria.category()));
            }
            if (criteria.level() != null) {
                predicates.add(cb.equal(root.get("level"), criteria.level()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateTime"), criteria.dateFrom().atStartOfDay()));
            }
            if (criteria.dateTo() != null) {
                predicates.add(cb.lessThan(root.get("dateTime"), criteria.dateTo().plusDays(1).atStartOfDay()));
            }
            if (hasText(criteria.location())) {
                predicates.add(cb.like(cb.lower(root.get("gymLocation")), containsPattern(criteria.location())));
            }
            if (hasText(criteria.instructor())) {
                predicates.add(cb.like(cb.lower(root.get("instructor")), containsPattern(criteria.instructor())));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String containsPattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
