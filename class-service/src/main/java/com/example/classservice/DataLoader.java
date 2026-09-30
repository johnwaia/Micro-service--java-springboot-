package com.example.classservice;

import com.example.classservice.model.ClassCategory;
import com.example.classservice.model.ClassLevel;
import com.example.classservice.model.FitnessClass;
import com.example.classservice.repository.FitnessClassRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(name = "fitconnect.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DataLoader implements CommandLineRunner {

    private final FitnessClassRepository repository;

    public DataLoader(FitnessClassRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        LocalDateTime today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        repository.save(new FitnessClass("Yoga du matin", "Vinyasa doux pour bien commencer la journee",
                "Marie Dupont", "Paris - Bastille", ClassCategory.YOGA, ClassLevel.BEGINNER, 60, 20,
                new BigDecimal("15.00"), today.plusDays(3).withHour(8)));
        repository.save(new FitnessClass("CrossFit WOD", "Entrainement fonctionnel haute intensite",
                "Karim Benali", "Paris - Republique", ClassCategory.CROSSFIT, ClassLevel.ADVANCED, 45, 12,
                new BigDecimal("25.00"), today.plusDays(4).withHour(18)));
        repository.save(new FitnessClass("Zumba Party", "Cardio dansant sur rythmes latinos",
                "Lucia Gomez", "Lyon - Part-Dieu", ClassCategory.ZUMBA, ClassLevel.INTERMEDIATE, 60, 30,
                new BigDecimal("12.00"), today.plusDays(5).withHour(19)));
        repository.save(new FitnessClass("Boxe anglaise", "Technique, sac et sparring leger",
                "Marc Leroy", "Paris - Bastille", ClassCategory.BOXING, ClassLevel.INTERMEDIATE, 90, 10,
                new BigDecimal("30.00"), today.plusDays(6).withHour(20)));
    }
}
