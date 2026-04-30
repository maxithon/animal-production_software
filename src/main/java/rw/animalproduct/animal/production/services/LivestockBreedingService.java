package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LivestockBreedingService {

    private final LivestockBreedingRepository breedingRepository;
    private final VeterinarianRepository      veterinarianRepository;
    private final LivestockRepository         livestockRepository;  // ADDED
    private final LifecycleEmailService       emailService;        // ADDED

    public LivestockBreedingService(LivestockBreedingRepository breedingRepository,
                                    VeterinarianRepository veterinarianRepository,
                                    LivestockRepository livestockRepository,
                                    LifecycleEmailService emailService) {
        this.breedingRepository = breedingRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.livestockRepository = livestockRepository;
        this.emailService = emailService;
    }

    // ── CRUD WITH EMAIL NOTIFICATIONS ─────────────────────────────────────────

    public List<LivestockBreeding> getAll() {
        return breedingRepository.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .collect(Collectors.toList());
    }

    public Page<LivestockBreeding> getPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return breedingRepository.findByIsDeletedFalse(pageable);
    }

    public Optional<LivestockBreeding> getById(UUID id) {
        return breedingRepository.findById(id)
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()));
    }

    @Transactional
    public LivestockBreeding addNew(LivestockBreeding breeding) {
        breeding.setIsDeleted(false);
        if (breeding.getStatus() == null || breeding.getStatus().isBlank()) {
            breeding.setStatus(LivestockBreeding.STATUS_PENDING);
        }
        LivestockBreeding saved = breedingRepository.save(breeding);

        // Send email notification
        emailService.sendBreedingStartedNotification(saved);

        return saved;
    }

    @Transactional
    public LivestockBreeding update(UUID id, LivestockBreeding updated) {
        LivestockBreeding existing = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        String oldStatus = existing.getStatus();

        existing.setLivestock(updated.getLivestock());
        existing.setMaleLivestock(updated.getMaleLivestock());
        existing.setBreedingDate(updated.getBreedingDate());
        existing.setBreedingMethod(updated.getBreedingMethod());
        existing.setStatus(updated.getStatus());
        existing.setExpectedPregnancyCheckDate(updated.getExpectedPregnancyCheckDate());
        existing.setExpectedDueDate(updated.getExpectedDueDate());
        existing.setNotes(updated.getNotes());
        existing.setVeterinarian(updated.getVeterinarian());

        LivestockBreeding saved = breedingRepository.save(existing);

        // Send notification when status changes to CONFIRMED_PREGNANT
        if (!oldStatus.equals(saved.getStatus()) &&
                LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(saved.getStatus())) {
            emailService.sendPregnancyConfirmedNotification(saved);

            // Update the livestock status
            if (saved.getLivestock() != null) {
                Livestock animal = saved.getLivestock();
                animal.setStatus(Livestock.STATUS_PREGNANT);
                animal.setIsPregnant(true);
                animal.setExpectedDueDate(saved.getExpectedDueDate());
                livestockRepository.save(animal);
            }
        }

        return saved;
    }

    @Transactional
    public LivestockBreeding confirmPregnancy(UUID id, LocalDate expectedDueDate) {
        LivestockBreeding breeding = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        breeding.setStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
        breeding.setExpectedDueDate(expectedDueDate);
        LivestockBreeding saved = breedingRepository.save(breeding);

        // Send pregnancy confirmation email
        emailService.sendPregnancyConfirmedNotification(saved);

        // Update the livestock
        if (saved.getLivestock() != null) {
            Livestock animal = saved.getLivestock();
            animal.setStatus(Livestock.STATUS_PREGNANT);
            animal.setIsPregnant(true);
            animal.setExpectedDueDate(expectedDueDate);
            livestockRepository.save(animal);
        }

        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        LivestockBreeding b = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
        b.setIsDeleted(true);
        breedingRepository.save(b);
    }

    // ── DASHBOARD HELPERS ─────────────────────────────────────────────────────

    public List<LivestockBreeding> getDueForPregnancyCheck() {
        LocalDate today = LocalDate.now();
        return getAll().stream()
                .filter(b -> LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .filter(b -> b.getExpectedPregnancyCheckDate() != null
                        && b.getExpectedPregnancyCheckDate().isBefore(today))
                .collect(Collectors.toList());
    }

    public List<LivestockBreeding> getApproachingDueDate() {
        LocalDate today = LocalDate.now();
        LocalDate in30  = today.plusDays(30);
        return getAll().stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus()))
                .filter(b -> b.getExpectedDueDate() != null
                        && !b.getExpectedDueDate().isBefore(today)
                        && !b.getExpectedDueDate().isAfter(in30))
                .collect(Collectors.toList());
    }

    public List<LivestockBreeding> getRecentBreedings() {
        return getAll().stream()
                .filter(b -> b.getBreedingDate() != null)
                .sorted((a, b) -> b.getBreedingDate().compareTo(a.getBreedingDate()))
                .limit(10)
                .collect(Collectors.toList());
    }

    public double getSuccessRate() {
        List<LivestockBreeding> all = getAll();
        if (all.isEmpty()) return 0.0;
        long confirmed = all.stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus()))
                .count();
        return (confirmed * 100.0) / all.size();
    }

    public long countByStatus(String status) {
        return getAll().stream()
                .filter(b -> status.equals(b.getStatus()))
                .count();
    }
}