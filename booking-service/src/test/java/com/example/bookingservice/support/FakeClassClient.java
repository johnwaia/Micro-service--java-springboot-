package com.example.bookingservice.support;

import com.example.bookingservice.client.ClassClient;
import com.example.bookingservice.client.dto.FitnessClassDto;
import com.example.bookingservice.exception.RemoteServiceException;
import com.example.bookingservice.exception.ServiceUnavailableException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * class-service en memoire, avec les memes regles metier (capacite, statut) que le vrai service.
 */
public class FakeClassClient implements ClassClient {

    private static final class StoredClass {
        Long id;
        String name;
        LocalDateTime dateTime;
        BigDecimal price;
        int max;
        int current;
        String status = FitnessClassDto.SCHEDULED;
    }

    private final Map<Long, StoredClass> classes = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(100);
    private volatile boolean down;

    public Long addClass(String name, int maxParticipants, int currentParticipants, String price, LocalDateTime dateTime) {
        StoredClass c = new StoredClass();
        c.id = ids.incrementAndGet();
        c.name = name;
        c.max = maxParticipants;
        c.current = currentParticipants;
        c.price = new BigDecimal(price);
        c.dateTime = dateTime;
        classes.put(c.id, c);
        return c.id;
    }

    public int participants(Long classId) {
        return classes.get(classId).current;
    }

    public void cancelClass(Long classId) {
        classes.get(classId).status = "CANCELLED";
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    public void reset() {
        classes.clear();
        down = false;
    }

    @Override
    public FitnessClassDto getFitnessClass(Long id) {
        return toDto(get(id));
    }

    @Override
    public synchronized FitnessClassDto incrementParticipants(Long id, int spots) {
        StoredClass c = get(id);
        if (!FitnessClassDto.SCHEDULED.equals(c.status) || c.current + spots > c.max) {
            throw new RemoteServiceException("class-service", 409, "Plus de places disponibles");
        }
        c.current += spots;
        return toDto(c);
    }

    @Override
    public synchronized FitnessClassDto decrementParticipants(Long id, int spots) {
        StoredClass c = get(id);
        if (c.current - spots < 0) {
            throw new RemoteServiceException("class-service", 409, "Participants negatifs");
        }
        c.current -= spots;
        return toDto(c);
    }

    private StoredClass get(Long id) {
        if (down) {
            throw new ServiceUnavailableException("class-service est indisponible (simulation)", null);
        }
        StoredClass c = classes.get(id);
        if (c == null) {
            throw new RemoteServiceException("class-service", 404, "Cours introuvable avec l'id " + id);
        }
        return c;
    }

    private FitnessClassDto toDto(StoredClass c) {
        return new FitnessClassDto(c.id, c.name, "Marie Dupont", "Paris", c.dateTime, c.price, c.max, c.current,
                c.status);
    }
}
