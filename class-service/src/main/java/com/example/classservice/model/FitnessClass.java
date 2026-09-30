package com.example.classservice.model;

import com.example.classservice.exception.InvalidParticipantsException;
import com.example.classservice.exception.NoSpotsAvailableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class FitnessClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Verrouillage optimiste : Hibernate ajoute "where version = ?" a chaque UPDATE.
     * Si deux reservations modifient le meme cours en parallele, la seconde echoue
     * (ObjectOptimisticLockingFailureException) au lieu d'ecraser la premiere.
     */
    @Version
    private Long version;

    private String name;

    @Column(length = 1000)
    private String description;

    private String instructor;

    private String gymLocation;

    @Enumerated(EnumType.STRING)
    private ClassCategory category;

    @Enumerated(EnumType.STRING)
    private ClassLevel level;

    private Integer durationMinutes;

    private Integer maxParticipants;

    private Integer currentParticipants;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    private ClassStatus status;

    protected FitnessClass() {
    }

    public FitnessClass(String name, String description, String instructor, String gymLocation,
                        ClassCategory category, ClassLevel level, Integer durationMinutes,
                        Integer maxParticipants, BigDecimal price, LocalDateTime dateTime) {
        this.name = name;
        this.description = description;
        this.instructor = instructor;
        this.gymLocation = gymLocation;
        this.category = category;
        this.level = level;
        this.durationMinutes = durationMinutes;
        this.maxParticipants = maxParticipants;
        this.currentParticipants = 0;
        this.price = price;
        this.dateTime = dateTime;
        this.status = ClassStatus.SCHEDULED;
    }

    public void incrementParticipants(int spots) {
        if (spots <= 0) {
            throw new InvalidParticipantsException("Le nombre de places doit etre strictement positif");
        }
        if (this.currentParticipants + spots > this.maxParticipants) {
            throw new NoSpotsAvailableException("Plus de places disponibles pour ce cours (id " + id + ") : "
                    + getAvailableSpots() + " place(s) restante(s), " + spots + " demandee(s)");
        }
        this.currentParticipants += spots;
    }

    public void decrementParticipants(int spots) {
        if (spots <= 0) {
            throw new InvalidParticipantsException("Le nombre de places doit etre strictement positif");
        }
        if (this.currentParticipants - spots < 0) {
            throw new InvalidParticipantsException("Impossible de liberer " + spots + " place(s) : seulement "
                    + currentParticipants + " participant(s) inscrit(s) au cours (id " + id + ")");
        }
        this.currentParticipants -= spots;
    }

    public int getAvailableSpots() {
        return maxParticipants - currentParticipants;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
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

    public Integer getCurrentParticipants() {
        return currentParticipants;
    }

    public void setCurrentParticipants(Integer currentParticipants) {
        this.currentParticipants = currentParticipants;
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

    public ClassStatus getStatus() {
        return status;
    }

    public void setStatus(ClassStatus status) {
        this.status = status;
    }
}
