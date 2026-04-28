package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
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

    public LivestockBreedingService(LivestockBreedingRepository breedingRepository,
                                    VeterinarianRepository veterinarianRepository) {
        this.breedingRepository     = breedingRepository;
        this.veterinarianRepository = veterinarianRepository;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<LivestockBreeding> getAll() {
        return breedingRepository.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .collect(Collectors.toList());
    }

    // NEW: Paginated method
    public Page<LivestockBreeding> getPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return breedingRepository.findByIsDeletedFalse(pageable);
    }

    public Optional<LivestockBreeding> getById(UUID id) {
        return breedingRepository.findById(id)
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()));
    }

    public LivestockBreeding addNew(LivestockBreeding breeding) {
        breeding.setIsDeleted(false);
        if (breeding.getStatus() == null || breeding.getStatus().isBlank()) {
            breeding.setStatus(LivestockBreeding.STATUS_PENDING);
        }
        return breedingRepository.save(breeding);
    }

    public LivestockBreeding update(UUID id, LivestockBreeding updated) {
        LivestockBreeding existing = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        existing.setLivestock(updated.getLivestock());
        existing.setMaleLivestock(updated.getMaleLivestock());
        existing.setBreedingDate(updated.getBreedingDate());
        existing.setBreedingMethod(updated.getBreedingMethod());
        existing.setStatus(updated.getStatus());
        existing.setExpectedPregnancyCheckDate(updated.getExpectedPregnancyCheckDate());
        existing.setExpectedDueDate(updated.getExpectedDueDate());
        existing.setNotes(updated.getNotes());
        existing.setVeterinarian(updated.getVeterinarian());

        return breedingRepository.save(existing);
    }

    public void delete(UUID id) {
        LivestockBreeding b = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
        b.setIsDeleted(true);
        breedingRepository.save(b);
    }

    // ── DASHBOARD HELPERS ─────────────────────────────────────────────────────

    /** Records whose pregnancy-check date has passed but are still PENDING */
    public List<LivestockBreeding> getDueForPregnancyCheck() {
        LocalDate today = LocalDate.now();
        return getAll().stream()
                .filter(b -> LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .filter(b -> b.getExpectedPregnancyCheckDate() != null
                        && b.getExpectedPregnancyCheckDate().isBefore(today))
                .collect(Collectors.toList());
    }

    /** Confirmed-pregnant records due within the next 30 days */
    public List<LivestockBreeding> getApproachingDueDate() {
        LocalDate today = LocalDate.now();
        LocalDate in30  = today.plusDays(30);
        return getAll().stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
                .filter(b -> b.getExpectedDueDate() != null
                        && !b.getExpectedDueDate().isBefore(today)
                        && !b.getExpectedDueDate().isAfter(in30))
                .collect(Collectors.toList());
    }

    /** 10 most recent breeding records */
    public List<LivestockBreeding> getRecentBreedings() {
        return getAll().stream()
                .filter(b -> b.getBreedingDate() != null)
                .sorted((a, b) -> b.getBreedingDate().compareTo(a.getBreedingDate()))
                .limit(10)
                .collect(Collectors.toList());
    }

    /** Success rate = CONFIRMED_PREGNANT / total active records (%) */
    public double getSuccessRate() {
        List<LivestockBreeding> all = getAll();
        if (all.isEmpty()) return 0.0;
        long confirmed = all.stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
                .count();
        return (confirmed * 100.0) / all.size();
    }

    public long countByStatus(String status) {
        return getAll().stream()
                .filter(b -> status.equals(b.getStatus()))
                .count();
    }
}