package com.example.classservice.service;

import com.example.classservice.dto.ClassSearchCriteria;
import com.example.classservice.dto.FitnessClassRequest;
import com.example.classservice.exception.ClassNotBookableException;
import com.example.classservice.exception.FitnessClassNotFoundException;
import com.example.classservice.exception.InvalidClassUpdateException;
import com.example.classservice.model.ClassStatus;
import com.example.classservice.model.FitnessClass;
import com.example.classservice.repository.FitnessClassRepository;
import com.example.classservice.repository.FitnessClassSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Service
public class FitnessClassService {

    /** Nombre de tentatives en cas de conflit de version (verrouillage optimiste). */
    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(FitnessClassService.class);

    private final FitnessClassRepository repository;
    private final TransactionTemplate transactionTemplate;

    public FitnessClassService(FitnessClassRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Page<FitnessClass> search(ClassSearchCriteria criteria, Pageable pageable) {
        return repository.findAll(FitnessClassSpecifications.matching(criteria), pageable);
    }

    public FitnessClass findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new FitnessClassNotFoundException(id));
    }

    public FitnessClass create(FitnessClassRequest request) {
        FitnessClass fitnessClass = new FitnessClass(request.getName(), request.getDescription(),
                request.getInstructor(), request.getGymLocation(), request.getCategory(), request.getLevel(),
                request.getDurationMinutes(), request.getMaxParticipants(), request.getPrice(),
                request.getDateTime());
        return repository.save(fitnessClass);
    }

    public FitnessClass update(Long id, FitnessClassRequest request) {
        FitnessClass fitnessClass = findById(id);
        if (request.getMaxParticipants() < fitnessClass.getCurrentParticipants()) {
            throw new InvalidClassUpdateException("maxParticipants (" + request.getMaxParticipants()
                    + ") ne peut pas etre inferieur au nombre de participants deja inscrits ("
                    + fitnessClass.getCurrentParticipants() + ")");
        }
        fitnessClass.setName(request.getName());
        fitnessClass.setDescription(request.getDescription());
        fitnessClass.setInstructor(request.getInstructor());
        fitnessClass.setGymLocation(request.getGymLocation());
        fitnessClass.setCategory(request.getCategory());
        fitnessClass.setLevel(request.getLevel());
        fitnessClass.setDurationMinutes(request.getDurationMinutes());
        fitnessClass.setMaxParticipants(request.getMaxParticipants());
        fitnessClass.setPrice(request.getPrice());
        fitnessClass.setDateTime(request.getDateTime());
        return repository.save(fitnessClass);
    }

    /**
     * Annulation logique : le cours reste consultable (les reservations y font reference)
     * mais n'accepte plus de nouvelles inscriptions.
     */
    public FitnessClass cancel(Long id) {
        FitnessClass fitnessClass = findById(id);
        fitnessClass.setStatus(ClassStatus.CANCELLED);
        return repository.save(fitnessClass);
    }

    public FitnessClass incrementParticipants(Long id, int spots) {
        return retryOnVersionConflict(() -> {
            FitnessClass fitnessClass = findById(id);
            if (fitnessClass.getStatus() != ClassStatus.SCHEDULED) {
                throw new ClassNotBookableException("Le cours (id " + id
                        + ") n'est plus ouvert aux reservations (statut " + fitnessClass.getStatus() + ")");
            }
            fitnessClass.incrementParticipants(spots);
            // flush immediat : le conflit de version est detecte ici, dans la transaction courante
            return repository.saveAndFlush(fitnessClass);
        });
    }

    public FitnessClass decrementParticipants(Long id, int spots) {
        return retryOnVersionConflict(() -> {
            FitnessClass fitnessClass = findById(id);
            fitnessClass.decrementParticipants(spots);
            return repository.saveAndFlush(fitnessClass);
        });
    }

    /**
     * Chaque tentative relit le cours dans une nouvelle transaction : si une autre requete l'a modifie
     * entre-temps, on repart de la version a jour (et la regle "plus de places" est reevaluee).
     */
    private FitnessClass retryOnVersionConflict(Supplier<FitnessClass> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> operation.get());
            } catch (OptimisticLockingFailureException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                log.info("Conflit de version detecte (tentative {}/{}), nouvel essai", attempt, MAX_ATTEMPTS);
            }
        }
    }
}
