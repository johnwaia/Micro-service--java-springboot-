package com.example.classservice.dto;

import com.example.classservice.model.ClassCategory;
import com.example.classservice.model.ClassLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public class FitnessClassRequest {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(30, 45, 60, 90);

    @NotBlank
    @Size(min = 3, message = "doit contenir au moins 3 caracteres")
    private String name;

    @NotBlank
    private String description;

    @NotBlank
    private String instructor;

    @NotBlank
    private String gymLocation;

    @NotNull
    private ClassCategory category;

    @NotNull
    private ClassLevel level;

    @NotNull
    private Integer durationMinutes;

    @NotNull
    @Min(5)
    @Max(30)
    private Integer maxParticipants;

    @NotNull
    @DecimalMin("5.00")
    private BigDecimal price;

    @NotNull
    @Future(message = "doit etre dans le futur")
    private LocalDateTime dateTime;

    @JsonIgnore
    @AssertTrue(message = "durationMinutes doit valoir 30, 45, 60 ou 90")
    public boolean isDurationAllowed() {
        return durationMinutes == null || ALLOWED_DURATIONS.contains(durationMinutes);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getGymLocation() {
        return gymLocation;
    }

    public void setGymLocation(String gymLocation) {
        this.gymLocation = gymLocation;
    }

    public ClassCategory getCategory() {
        return category;
    }

    public void setCategory(ClassCategory category) {
        this.category = category;
    }

    public ClassLevel getLevel() {
        return level;
    }

    public void setLevel(ClassLevel level) {
        this.level = level;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }
}
