package com.example.classservice.controller;

import com.example.classservice.model.ClassCategory;
import com.example.classservice.model.ClassLevel;
import com.example.classservice.model.FitnessClass;
import com.example.classservice.repository.FitnessClassRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FitnessClassControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FitnessClassRepository repository;

    private final LocalDateTime nextWeek = LocalDateTime.now().plusDays(7).truncatedTo(ChronoUnit.HOURS);

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Yoga Vinyasa");
        payload.put("description", "Flow dynamique");
        payload.put("instructor", "Marie Dupont");
        payload.put("gymLocation", "Paris - Bastille");
        payload.put("category", "YOGA");
        payload.put("level", "INTERMEDIATE");
        payload.put("durationMinutes", 60);
        payload.put("maxParticipants", 10);
        payload.put("price", 20.00);
        payload.put("dateTime", nextWeek.toString());
        return payload;
    }

    private FitnessClass saveClass(String name, ClassCategory category, ClassLevel level, String location,
                                   String instructor, LocalDateTime dateTime, int max) {
        return repository.save(new FitnessClass(name, "desc", instructor, location, category, level, 60, max,
                new BigDecimal("15.00"), dateTime));
    }

    @Test
    void create_withValidPayload_returns201WithZeroParticipants() throws Exception {
        mockMvc.perform(post("/api/classes").contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.currentParticipants").value(0))
                .andExpect(jsonPath("$.availableSpots").value(10));
    }

    @Test
    void create_withInvalidFields_returns400() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("name", "Yo");
        payload.put("durationMinutes", 50);
        payload.put("maxParticipants", 40);
        payload.put("price", 3.00);
        payload.put("dateTime", LocalDateTime.now().minusDays(1).toString());

        mockMvc.perform(post("/api/classes").contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name")))
                .andExpect(jsonPath("$.message", containsString("durationMinutes doit valoir")))
                .andExpect(jsonPath("$.message", containsString("maxParticipants")))
                .andExpect(jsonPath("$.message", containsString("price")))
                .andExpect(jsonPath("$.message", containsString("dateTime")));
    }

    @Test
    void findById_whenMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/classes/{id}", 9999))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_filtersByCategoryAndLevel_andPaginates() throws Exception {
        saveClass("Yoga 1", ClassCategory.YOGA, ClassLevel.BEGINNER, "Paris", "Marie", nextWeek.plusHours(2), 10);
        saveClass("Yoga 2", ClassCategory.YOGA, ClassLevel.BEGINNER, "Paris", "Marie", nextWeek.plusHours(1), 10);
        saveClass("Yoga 3", ClassCategory.YOGA, ClassLevel.BEGINNER, "Lyon", "Paul", nextWeek.plusHours(3), 10);
        saveClass("Yoga avance", ClassCategory.YOGA, ClassLevel.ADVANCED, "Paris", "Marie", nextWeek, 10);
        saveClass("Boxe", ClassCategory.BOXING, ClassLevel.BEGINNER, "Paris", "Marc", nextWeek, 10);

        mockMvc.perform(get("/api/classes")
                        .param("category", "YOGA").param("level", "BEGINNER")
                        .param("page", "0").param("size", "2").param("sort", "dateTime,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Yoga 2"))
                .andExpect(jsonPath("$.content[1].name").value("Yoga 1"));
    }

    @Test
    void findAll_filtersByDateRangeLocationAndInstructor() throws Exception {
        LocalDateTime day1 = nextWeek.withHour(10);
        saveClass("Dans la plage", ClassCategory.ZUMBA, ClassLevel.BEGINNER, "Paris - Bastille", "Marie Dupont", day1, 10);
        saveClass("Hors plage", ClassCategory.ZUMBA, ClassLevel.BEGINNER, "Paris - Bastille", "Marie Dupont", day1.plusDays(10), 10);
        saveClass("Autre ville", ClassCategory.ZUMBA, ClassLevel.BEGINNER, "Lyon", "Marie Dupont", day1, 10);
        saveClass("Autre coach", ClassCategory.ZUMBA, ClassLevel.BEGINNER, "Paris - Bastille", "Paul", day1, 10);

        mockMvc.perform(get("/api/classes")
                        .param("dateFrom", day1.toLocalDate().toString())
                        .param("dateTo", day1.toLocalDate().plusDays(1).toString())
                        .param("location", "paris")
                        .param("instructor", "Marie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Dans la plage"));
    }

    @Test
    void search_returnsOnlyScheduledClassesByDefault() throws Exception {
        saveClass("Ouvert", ClassCategory.PILATES, ClassLevel.BEGINNER, "Paris", "Marie", nextWeek, 10);
        FitnessClass cancelled = saveClass("Annule", ClassCategory.PILATES, ClassLevel.BEGINNER, "Paris", "Marie", nextWeek, 10);
        mockMvc.perform(delete("/api/classes/{id}", cancelled.getId())).andExpect(status().isOk());

        mockMvc.perform(get("/api/classes/search").param("category", "PILATES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Ouvert"));
    }

    @Test
    void increment_thenDecrement_updatesParticipants() throws Exception {
        FitnessClass fc = saveClass("Spinning", ClassCategory.SPINNING, ClassLevel.BEGINNER, "Paris", "Marie", nextWeek, 10);

        mockMvc.perform(patch("/api/classes/{id}/increment", fc.getId()).param("spots", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(3))
                .andExpect(jsonPath("$.availableSpots").value(7));

        mockMvc.perform(patch("/api/classes/{id}/decrement", fc.getId()).param("spots", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(1));
    }

    @Test
    void increment_beyondCapacity_returns409() throws Exception {
        FitnessClass fc = saveClass("Spinning", ClassCategory.SPINNING, ClassLevel.BEGINNER, "Paris", "Marie", nextWeek, 5);
        mockMvc.perform(patch("/api/classes/{id}/increment", fc.getId()).param("spots", "4"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/classes/{id}/increment", fc.getId()).param("spots", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Plus de places disponibles")));
    }

    @Test
    void increment_withInvalidSpots_returns400() throws Exception {
        FitnessClass fc = saveClass("Spinning", ClassCategory.SPINNING, ClassLevel.BEGINNER, "Paris", "Marie", nextWeek, 5);

        mockMvc.perform(patch("/api/classes/{id}/increment", fc.getId()).param("spots", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void increment_onCancelledClass_returns409() throws Exception {
        FitnessClass fc = saveClass("Boxe", ClassCategory.BOXING, ClassLevel.BEGINNER, "Paris", "Marc", nextWeek, 10);
        mockMvc.perform(delete("/api/classes/{id}", fc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(patch("/api/classes/{id}/increment", fc.getId()).param("spots", "1"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_cannotShrinkCapacityBelowCurrentParticipants() throws Exception {
        FitnessClass fc = saveClass("Yoga", ClassCategory.YOGA, ClassLevel.BEGINNER, "Paris", "Marie", nextWeek, 10);
        fc.incrementParticipants(8);
        repository.save(fc);

        Map<String, Object> payload = validPayload();
        payload.put("maxParticipants", 5);
        mockMvc.perform(put("/api/classes/{id}", fc.getId()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());

        payload.put("maxParticipants", 12);
        mockMvc.perform(put("/api/classes/{id}", fc.getId()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxParticipants").value(12))
                .andExpect(jsonPath("$.currentParticipants").value(8));
    }
}
