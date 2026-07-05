package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LivestockBirthService {

    private static final Logger log = LoggerFactory.getLogger(LivestockBirthService.class);

    private final LivestockBirthRepository birthRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockOffspringRepository offspringRepository;
    private final LivestockBreedingRepository breedingRepository;
    private final LivestockCategoryRepository categoryRepository;

    public LivestockBirthService(
            LivestockBirthRepository birthRepository,
            LivestockRepository livestockRepository,
            LivestockOffspringRepository offspringRepository,
            LivestockBreedingRepository breedingRepository,
            LivestockCategoryRepository categoryRepository) {
        this.birthRepository = birthRepository;
        this.livestockRepository = livestockRepository;
        this.offspringRepository = offspringRepository;
        this.breedingRepository = breedingRepository;
        this.categoryRepository = categoryRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Create a new birth record
     */
    public LivestockBirth createBirth(LivestockBirth birth) {
        log.debug("Creating new birth record");

        // Validate
        if (birth.getLivestockId() == null) {
            throw new IllegalArgumentException("Mother livestock ID is required");
        }

        // Set default values
        if (birth.getIsDeleted() == null) {
            birth.setIsDeleted(false);
        }
        if (birth.getOffspringCount() == null) {
            birth.setOffspringCount(0);
        }

        // Save birth
        LivestockBirth savedBirth = birthRepository.save(birth);

        // Update mother's last birth date
        livestockRepository.findById(birth.getLivestockId()).ifPresent(mother -> {
            mother.setLastBirthDate(birth.getBirthDate());
            if (mother.getOffspringCount() == null) {
                mother.setOffspringCount(0);
            }
            mother.setOffspringCount(mother.getOffspringCount() + 1);
            livestockRepository.save(mother);
        });

        // Update breeding record if exists
        if (birth.getBreedingId() != null) {
            breedingRepository.findById(birth.getBreedingId()).ifPresent(breeding -> {
                breeding.setStatus(LivestockBreeding.STATUS_COMPLETED);
                breeding.setNotes((breeding.getNotes() != null ? breeding.getNotes() + " " : "") +
                        "Birth recorded on " + birth.getBirthDate());
                breedingRepository.save(breeding);
            });
        }

        return savedBirth;
    }

    /**
     * Controller-facing entry point for registering a birth + optionally
     * linking already-registered children in one step.
     *
     * Resolves the transient {@code livestockIdValue} (set by the register/edit
     * forms) into the real {@code livestockId} FK before delegating to
     * {@link #createBirth(LivestockBirth)}.
     */
    public LivestockBirth addNew(LivestockBirth birth, List<UUID> linkedChildIds) {
        log.debug("Registering new birth (addNew)");

        resolveAndSetLivestock(birth);

        LivestockBirth saved = createBirth(birth);

        if (linkedChildIds != null) {
            for (UUID childId : linkedChildIds) {
                try {
                    addOffspring(saved.getId(), childId, 1);
                } catch (Exception e) {
                    log.warn("Could not link child {} to birth {}: {}", childId, saved.getId(), e.getMessage());
                }
            }
        }

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Get all non-deleted birth records
     */
    public List<LivestockBirth> getAll() {
        log.debug("Getting all birth records");
        return birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();
    }

    /**
     * Paged view of all non-deleted birth records, newest first.
     * Implemented in-memory (rather than a repository query) so no new
     * repository method is required.
     */
    public Page<LivestockBirth> getPaged(int page, int size) {
        log.debug("Getting paged birth records: page={}, size={}", page, size);

        List<LivestockBirth> all = getAll();
        int total = all.size();
        int start = Math.min(page * size, total);
        int end = Math.min(start + size, total);

        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(all.subList(start, end), pageable, total);
    }

    /**
     * Get all births for a specific mother
     */
    public List<LivestockBirth> getBirthsByMother(UUID motherId) {
        log.debug("Getting births for mother: {}", motherId);
        return birthRepository.findByLivestockIdAndIsDeletedFalse(motherId);
    }

    /**
     * Alias of {@link #getBirthsByMother(UUID)} for controller readability
     * (used by the family-tree view).
     */
    public List<LivestockBirth> getByLivestockId(UUID livestockId) {
        return getBirthsByMother(livestockId);
    }

    /**
     * Get a birth by ID
     */
    public Optional<LivestockBirth> getBirthById(UUID birthId) {
        log.debug("Getting birth by ID: {}", birthId);
        return birthRepository.findById(birthId);
    }

    /**
     * Alias of {@link #getBirthById(UUID)} for controller readability.
     */
    public Optional<LivestockBirth> getById(UUID birthId) {
        return getBirthById(birthId);
    }

    /**
     * Direct (first-generation) children of an animal — i.e. any livestock
     * whose {@code mother} points to this animal.
     */
    public List<Livestock> getDirectChildren(UUID livestockId) {
        log.debug("Getting direct children for livestock: {}", livestockId);
        return livestockRepository.findAll().stream()
                .filter(l -> l.getMother() != null && livestockId.equals(l.getMother().getId()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .collect(Collectors.toList());
    }

    /**
     * Whether an animal has at least one direct child on record.
     */
    public boolean hasChildren(UUID livestockId) {
        return !getDirectChildren(livestockId).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Update a birth record
     */
    public LivestockBirth updateBirth(UUID birthId, LivestockBirth updatedBirth) {
        log.debug("Updating birth: {}", birthId);

        LivestockBirth existingBirth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        // Update fields
        if (updatedBirth.getLivestockId() != null) {
            existingBirth.setLivestockId(updatedBirth.getLivestockId());
        }
        if (updatedBirth.getBirthDate() != null) {
            existingBirth.setBirthDate(updatedBirth.getBirthDate());
        }
        if (updatedBirth.getOffspringCount() != null) {
            existingBirth.setOffspringCount(updatedBirth.getOffspringCount());
        }
        if (updatedBirth.getOffspringGender() != null) {
            existingBirth.setOffspringGender(updatedBirth.getOffspringGender());
        }
        if (updatedBirth.getWeaningDate() != null) {
            existingBirth.setWeaningDate(updatedBirth.getWeaningDate());
        }
        if (updatedBirth.getNextBreedingDate() != null) {
            existingBirth.setNextBreedingDate(updatedBirth.getNextBreedingDate());
        }
        if (updatedBirth.getNotes() != null) {
            existingBirth.setNotes(updatedBirth.getNotes());
        }
        if (updatedBirth.getSourceLocation() != null) {
            existingBirth.setSourceLocation(updatedBirth.getSourceLocation());
        }
        if (updatedBirth.getIsExternalBirth() != null) {
            existingBirth.setIsExternalBirth(updatedBirth.getIsExternalBirth());
        }

        return birthRepository.save(existingBirth);
    }

    /**
     * Controller-facing entry point for editing a birth. Resolves the
     * transient {@code livestockIdValue} into {@code livestockId} before
     * delegating to {@link #updateBirth(UUID, LivestockBirth)}.
     */
    public LivestockBirth update(UUID birthId, LivestockBirth birth) {
        log.debug("Updating birth (update): {}", birthId);
        resolveAndSetLivestock(birth);
        return updateBirth(birthId, birth);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Delete a birth record (soft delete)
     */
    public void deleteBirth(UUID birthId) {
        log.debug("Soft deleting birth: {}", birthId);

        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        birth.setIsDeleted(true);
        birthRepository.save(birth);

        // Also soft delete all offspring
        List<LivestockOffspring> offspring = offspringRepository.findByBirthEventId(birthId);
        for (LivestockOffspring child : offspring) {
            child.setIsDeleted(true);
            offspringRepository.save(child);
        }
    }

    /**
     * Alias of {@link #deleteBirth(UUID)} for controller readability.
     */
    public void delete(UUID birthId) {
        deleteBirth(birthId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CHILD LINKING
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Add a child/offspring to a birth event
     */
    public LivestockOffspring addOffspring(UUID birthId, UUID childLivestockId, Integer generation) {
        log.debug("Adding offspring to birth: {}", birthId);

        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        Livestock child = livestockRepository.findById(childLivestockId)
                .orElseThrow(() -> new RuntimeException("Child livestock not found"));

        // Verify child is not already linked to another birth
        List<LivestockOffspring> existing = offspringRepository.findByChildLivestockIdAndIsDeletedFalse(childLivestockId);
        if (!existing.isEmpty()) {
            throw new RuntimeException("This livestock is already linked to a birth event");
        }

        LivestockOffspring offspring = new LivestockOffspring();
        offspring.setBirthEvent(birth);
        offspring.setChildLivestock(child);
        offspring.setGeneration(generation != null ? generation : 1);
        offspring.setIsAlive(true);
        offspring.setIsDeleted(false);

        // Update child's mother and status
        Livestock mother = birth.getLivestockId() != null
                ? livestockRepository.findById(birth.getLivestockId()).orElse(birth.getLivestock())
                : birth.getLivestock();
        child.setMother(mother);
        child.setBirthDate(birth.getBirthDate());
        child.setStatus(Livestock.STATUS_ACTIVE);
        livestockRepository.save(child);

        // Update birth's offspring count
        birth.setOffspringCount((birth.getOffspringCount() != null ? birth.getOffspringCount() : 0) + 1);
        birthRepository.save(birth);

        return offspringRepository.save(offspring);
    }

    /**
     * Controller-facing alias for linking an already-registered animal as a
     * child of a birth event.
     */
    public LivestockOffspring linkChild(UUID birthId, UUID childLivestockId) {
        return addOffspring(birthId, childLivestockId, 1);
    }

    /**
     * Remove an offspring from a birth event
     */
    public void removeOffspring(UUID offspringId) {
        log.debug("Removing offspring: {}", offspringId);

        LivestockOffspring offspring = offspringRepository.findById(offspringId)
                .orElseThrow(() -> new RuntimeException("Offspring record not found"));

        // Soft delete
        offspring.setIsDeleted(true);
        offspringRepository.save(offspring);

        // Update birth's offspring count
        LivestockBirth birth = offspring.getBirthEvent();
        if (birth != null && birth.getOffspringCount() != null && birth.getOffspringCount() > 0) {
            birth.setOffspringCount(birth.getOffspringCount() - 1);
            birthRepository.save(birth);
        }
    }

    /**
     * Controller-facing alias: unlink a child animal from whichever birth
     * event it is currently attached to (looked up by the child's own ID,
     * since the "Unlink" button on the children page only knows the child).
     */
    public void unlinkChild(UUID childLivestockId) {
        log.debug("Unlinking child livestock: {}", childLivestockId);

        List<LivestockOffspring> links = offspringRepository
                .findByChildLivestockIdAndIsDeletedFalse(childLivestockId);

        for (LivestockOffspring link : links) {
            removeOffspring(link.getId());
        }

        // Clear the mother reference on the child so it becomes available again
        livestockRepository.findById(childLivestockId).ifPresent(child -> {
            child.setMother(null);
            livestockRepository.save(child);
        });
    }

    /**
     * Get all offspring for a birth event
     */
    public List<LivestockOffspring> getOffspringByBirth(UUID birthId) {
        log.debug("Getting offspring for birth: {}", birthId);
        return offspringRepository.findByBirthEventIdAndIsDeletedFalse(birthId);
    }

    /**
     * Count live offspring for a birth event
     */
    public long countLiveOffspring(UUID birthId) {
        log.debug("Counting live offspring for birth: {}", birthId);
        return offspringRepository.countLiveOffspringByBirthEventId(birthId);
    }

    /**
     * Get birth by child livestock ID
     * This method finds the birth event that produced the given livestock
     */
    public Optional<LivestockBirth> getBirthByChildLivestock(UUID childLivestockId) {
        log.debug("Finding birth by child livestock ID: {}", childLivestockId);

        List<LivestockOffspring> offspring = offspringRepository.findByChildLivestockIdAndIsDeletedFalse(childLivestockId);

        if (offspring.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(offspring.get(0).getBirthEvent());
    }

    /**
     * Check if a livestock is linked to a birth event as an offspring
     */
    public boolean isChildLivestock(UUID livestockId) {
        log.debug("Checking if livestock is a child: {}", livestockId);
        List<LivestockOffspring> offspring = offspringRepository.findByChildLivestockIdAndIsDeletedFalse(livestockId);
        return !offspring.isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // REPORTS / QUERIES
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Get all births in a date range
     */
    public List<LivestockBirth> getBirthsByDateRange(LocalDate startDate, LocalDate endDate) {
        log.debug("Getting births between {} and {}", startDate, endDate);
        return birthRepository.findByBirthDateBetween(startDate, endDate);
    }

    /**
     * Get all births by category
     */
    public Map<String, List<LivestockBirth>> getBirthsByCategory() {
        log.debug("Getting births grouped by category");

        List<LivestockBirth> births = birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();
        Map<String, List<LivestockBirth>> birthsByCategory = new HashMap<>();

        for (LivestockBirth birth : births) {
            if (birth.getLivestock() != null && birth.getLivestock().getLivestockCategory() != null) {
                String categoryName = birth.getLivestock().getLivestockCategory().getName();
                birthsByCategory.computeIfAbsent(categoryName, k -> new ArrayList<>()).add(birth);
            } else {
                birthsByCategory.computeIfAbsent("Unknown", k -> new ArrayList<>()).add(birth);
            }
        }

        return birthsByCategory;
    }

    /**
     * Get birth statistics summary
     */
    public BirthStatistics getBirthStatistics() {
        log.debug("Getting birth statistics");

        BirthStatistics stats = new BirthStatistics();

        List<LivestockBirth> births = birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();
        stats.setTotalBirths(births.size());

        int totalOffspring = 0;
        int liveOffspring = 0;
        int stillborn = 0;

        for (LivestockBirth birth : births) {
            totalOffspring += birth.getOffspringCount() != null ? birth.getOffspringCount() : 0;

            List<LivestockOffspring> offspring = offspringRepository.findByBirthEventIdAndIsDeletedFalse(birth.getId());
            for (LivestockOffspring child : offspring) {
                if (child.isAlive()) {
                    liveOffspring++;
                } else {
                    stillborn++;
                }
            }
        }

        stats.setTotalOffspring(totalOffspring);
        stats.setLiveOffspring(liveOffspring);
        stats.setStillborn(stillborn);

        if (births.size() > 0) {
            stats.setAverageOffspringPerBirth((double) totalOffspring / births.size());
        }

        // Count by year
        Map<Integer, Integer> birthsByYear = new TreeMap<>(Collections.reverseOrder());
        for (LivestockBirth birth : births) {
            if (birth.getBirthDate() != null) {
                int year = birth.getBirthDate().getYear();
                birthsByYear.merge(year, 1, Integer::sum);
            }
        }
        stats.setBirthsByYear(birthsByYear);

        return stats;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADMIN — repair
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Backfills missing {@code mother} / {@code birthDate} links on child
     * animals whose birth event is known but whose own record fell out of
     * sync (e.g. imported data, or a link created before this fix existed).
     *
     * @return number of livestock records that were fixed
     */
    public int repairMissingMotherLinks() {
        log.debug("Repairing missing mother links");

        int fixed = 0;
        List<LivestockBirth> allBirths = getAll();

        for (LivestockBirth birth : allBirths) {
            if (birth.getLivestockId() == null) continue;

            Livestock mother = livestockRepository.findById(birth.getLivestockId()).orElse(null);
            if (mother == null) continue;

            List<LivestockOffspring> offspring =
                    offspringRepository.findByBirthEventIdAndIsDeletedFalse(birth.getId());

            for (LivestockOffspring o : offspring) {
                Livestock child = o.getChildLivestock();
                if (child == null) continue;

                boolean motherMissing = child.getMother() == null
                        || !mother.getId().equals(child.getMother().getId());
                boolean birthDateMissing = child.getBirthDate() == null && birth.getBirthDate() != null;

                if (motherMissing || birthDateMissing) {
                    child.setMother(mother);
                    if (birthDateMissing) {
                        child.setBirthDate(birth.getBirthDate());
                    }
                    livestockRepository.save(child);
                    fixed++;
                }
            }
        }

        log.debug("Repaired {} livestock record(s)", fixed);
        return fixed;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the transient {@code livestockIdValue} (a String coming from
     * the registration/edit form's dropdown) into the real {@code livestockId}
     * FK field, so it actually gets persisted.
     */
    private void resolveAndSetLivestock(LivestockBirth birth) {
        String idStr = birth.getLivestockIdValue();
        if (idStr != null && !idStr.isBlank()) {
            birth.setLivestockId(UUID.fromString(idStr));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // INNER CLASSES
    // ─────────────────────────────────────────────────────────────────────────────

    public static class BirthStatistics {
        private int totalBirths;
        private int totalOffspring;
        private int liveOffspring;
        private int stillborn;
        private double averageOffspringPerBirth;
        private Map<Integer, Integer> birthsByYear = new HashMap<>();

        public int getTotalBirths() { return totalBirths; }
        public void setTotalBirths(int totalBirths) { this.totalBirths = totalBirths; }

        public int getTotalOffspring() { return totalOffspring; }
        public void setTotalOffspring(int totalOffspring) { this.totalOffspring = totalOffspring; }

        public int getLiveOffspring() { return liveOffspring; }
        public void setLiveOffspring(int liveOffspring) { this.liveOffspring = liveOffspring; }

        public int getStillborn() { return stillborn; }
        public void setStillborn(int stillborn) { this.stillborn = stillborn; }

        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double averageOffspringPerBirth) {
            this.averageOffspringPerBirth = averageOffspringPerBirth;
        }

        public Map<Integer, Integer> getBirthsByYear() { return birthsByYear; }
        public void setBirthsByYear(Map<Integer, Integer> birthsByYear) { this.birthsByYear = birthsByYear; }
    }
}