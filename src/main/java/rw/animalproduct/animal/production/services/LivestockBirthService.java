package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.dto.BirthPerformanceAnalyticsDto;
import rw.animalproduct.animal.production.dto.OffspringView;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LivestockBirthService {

    private static final Logger log = LoggerFactory.getLogger(LivestockBirthService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Africa/Kigali");

    private static final double GESTATION_TOLERANCE_FRACTION = 0.15;
    private static final int EXPECTED_DUE_DATE_FALLBACK_TOLERANCE_DAYS = 21;
    private static final int CHILD_BIRTH_DATE_TOLERANCE_DAYS = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String LOCKED_MESSAGE =
            "This birth record is locked (verified/completed). Amend (reopen) it before making changes.";

    private final LivestockBirthRepository birthRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockOffspringRepository offspringRepository;
    private final LivestockBreedingRepository breedingRepository;
    private final LivestockCategoryRepository categoryRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final LifecycleEmailService emailService;
    private final AuditLogService auditLogService;

    public LivestockBirthService(LivestockBirthRepository birthRepository,
                                 LivestockRepository livestockRepository,
                                 LivestockOffspringRepository offspringRepository,
                                 LivestockBreedingRepository breedingRepository,
                                 LivestockCategoryRepository categoryRepository,
                                 VeterinarianRepository veterinarianRepository,
                                 LifecycleEmailService emailService,
                                 AuditLogService auditLogService) {
        this.birthRepository        = birthRepository;
        this.livestockRepository    = livestockRepository;
        this.offspringRepository    = offspringRepository;
        this.breedingRepository     = breedingRepository;
        this.categoryRepository     = categoryRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.emailService           = emailService;
        this.auditLogService        = auditLogService;
    }

    // ══════════════════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<LivestockBirth> getAll() { return birthRepository.findAllByOrderByBirthDateDesc(); }

    @Transactional(readOnly = true)
    public Page<LivestockBirth> getPaged(int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "birthDate"));
        return birthRepository.findAllByOrderByBirthDateDesc(pageable);
    }

    @Transactional(readOnly = true)
    public long countPendingVerification() { return birthRepository.countByCompletedAtIsNull(); }

    @Transactional(readOnly = true)
    public long countLocked() { return birthRepository.countByCompletedAtIsNotNull(); }

    @Transactional(readOnly = true)
    public Map<String, CategoryBirthStats> getCategoryBirthStats() {
        Map<String, CategoryBirthStats> result = new TreeMap<>();
        for (Object[] row : birthRepository.aggregateCategoryBirthStats()) {
            String name = (String) row[0];
            long total  = ((Number) row[1]).longValue();
            long locked = ((Number) row[2]).longValue();
            CategoryBirthStats s = new CategoryBirthStats(name);
            s.addTotals(total, locked);
            result.put(name, s);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LivestockBirth> getBirthsByMother(UUID motherId) { return birthRepository.findByLivestockId(motherId); }

    @Transactional(readOnly = true)
    public List<LivestockBirth> getByLivestockId(UUID id) { return getBirthsByMother(id); }

    @Transactional(readOnly = true)
    public Map<UUID, LivestockBirth> getBirthMapForIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new HashMap<>();
        return birthRepository.findByLivestockIdIn(ids).stream()
                .collect(Collectors.toMap(LivestockBirth::getLivestockId, b -> b, (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public Optional<LivestockBirth> getBirthById(UUID id) { return birthRepository.findById(id); }

    @Transactional(readOnly = true)
    public Optional<LivestockBirth> getById(UUID id) { return getBirthById(id); }

    @Transactional(readOnly = true)
    public Optional<LivestockBirth> getWithChildren(UUID id) {
        return birthRepository.findWithChildrenById(id);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Livestock> getMothersForBirths(List<LivestockBirth> births) {
        List<UUID> ids = births.stream()
                .map(LivestockBirth::getLivestockId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        return livestockRepository.findAllWithCategoryByIdIn(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(Livestock::getId, l -> l));
    }

    @Transactional(readOnly = true)
    public List<Livestock> getDirectChildren(UUID livestockId) {
        if (livestockId == null) return Collections.emptyList();
        return livestockRepository.findByMotherIdAndStatusIn(livestockId,
                List.of(Livestock.STATUS_ACTIVE, Livestock.STATUS_SOLD, Livestock.STATUS_TRANSFERRED));
    }

    @Transactional(readOnly = true)
    public boolean hasChildren(UUID livestockId) { return !getDirectChildren(livestockId).isEmpty(); }

    @Transactional(readOnly = true)
    public Livestock getCommonFatherForBirth(UUID birthId) {
        Set<Livestock> fathers = offspringRepository.findByBirthEventId(birthId).stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .map(Livestock::getFather)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (fathers.size() > 1) {
            log.warn("Birth {} has offspring with multiple recorded fathers — data inconsistency.", birthId);
            return null;
        }
        return fathers.stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public Set<UUID> getLinkedChildIds() {
        return offspringRepository.findLinkedChildIds();
    }

    @Transactional(readOnly = true)
    public List<OffspringView> getOffspringViews(UUID birthId) {
        LivestockBirth birth = birthRepository.findWithChildrenById(birthId).orElse(null);
        if (birth == null) return Collections.emptyList();
        Livestock mother = birth.getLivestock();
        return birth.getChildren().stream()
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .filter(o -> o.getChildLivestock() != null)
                .sorted(Comparator.comparing(
                        o -> o.getChildLivestock().getTagNumber(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(o -> {
                    Livestock c = o.getChildLivestock();
                    return OffspringView.builder()
                            .offspringLinkId(o.getId())
                            .childId(c.getId())
                            .tagNumber(c.getTagNumber())
                            .gender(c.getGender())
                            .categoryName(c.getLivestockCategory() != null
                                    ? c.getLivestockCategory().getName() : null)
                            .birthDate(c.getBirthDate())
                            .status(c.getStatus())
                            .draft(Boolean.TRUE.equals(c.getIsDraft()))
                            .outcome(o.getOutcome())
                            .generation(o.getGeneration())
                            .birthWeightKg(o.getBirthWeightKg())
                            .motherId(mother != null ? mother.getId() : null)
                            .motherTag(mother != null ? mother.getTagNumber() : null)
                            .motherCategoryName(mother != null && mother.getLivestockCategory() != null
                                    ? mother.getLivestockCategory().getName() : null)
                            .fatherId(c.getFather() != null ? c.getFather().getId() : null)
                            .fatherTag(c.getFather() != null ? c.getFather().getTagNumber() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════════════
    // CREATE
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockBirth addNew(LivestockBirth birth, List<UUID> linkedChildIds, UUID fatherId) {
        resolveAndSetLivestock(birth);
        resolveAndSetVeterinarian(birth);

        List<Livestock> children = loadAndValidateChildren(linkedChildIds, birth);
        if (!children.isEmpty()) {
            birth.setOffspringCount(children.size());
            birth.setOffspringGender(buildGenderSummary(children));
        }

        LivestockBirth saved = createBirth(birth, !children.isEmpty());
        auditLogService.logCreate("livestock_birth", saved.getId(), saved, "system");

        for (Livestock child : children) {
            addOffspring(saved.getId(), child.getId(), 1,
                    LivestockOffspring.OUTCOME_ALIVE, null, fatherId);
            auditLogService.logCreate("livestock_offspring", saved.getId(),
                    Map.of("birthId", saved.getId(), "childId", child.getId()), "system");
        }
        return saved;
    }

    @Transactional
    public LivestockBirth createBirth(LivestockBirth birth) { return createBirth(birth, false); }

    private LivestockBirth createBirth(LivestockBirth birth, boolean hasLinked) {
        if (birth.getBirthDate() == null) {
            throw new IllegalArgumentException("Birth date is required");
        }
        if (birth.getBirthDate().isAfter(LocalDate.now(APP_ZONE))) {
            throw new IllegalStateException("Birth date cannot be in the future");
        }
        if (birth.getLivestockId() != null) {
            validateBirthEligibility(birth.getLivestockId(), birth.getBirthDate());
        }
        if (birth.getNumberBornAlive() != null && birth.getNumberBornAlive() < 0) {
            throw new IllegalArgumentException("Number born alive cannot be negative");
        }
        if (birth.getNumberStillborn() != null && birth.getNumberStillborn() < 0) {
            throw new IllegalArgumentException("Number stillborn cannot be negative");
        }

        if (birth.getOffspringCount() == null)     birth.setOffspringCount(0);
        if (birth.getAssistanceRequired() == null) birth.setAssistanceRequired(false);
        if (birth.getNumberStillborn() == null)    birth.setNumberStillborn(0);

        validateOffspringCountConsistency(birth, hasLinked);

        if (birth.getLivestockId() != null) {
            Livestock mother = livestockRepository.findById(birth.getLivestockId()).orElse(null);
            LivestockCategory cat = mother != null ? mother.getLivestockCategory() : null;
            if (birth.getWeaningDate() == null && cat != null) {
                birth.setWeaningDate(calculateRealWeaningDate(birth.getBirthDate(), cat));
            }
            if (birth.getNextBreedingDate() == null && cat != null) {
                birth.setNextBreedingDate(calculateRealNextBreedingDate(birth.getBirthDate(), cat));
            }
        }

        LivestockBirth saved = birthRepository.save(birth);

        final UUID motherId = birth.getLivestockId();
        if (motherId != null) {
            livestockRepository.findById(motherId).ifPresent(m -> {
                m.setLastBirthDate(birth.getBirthDate());
                m.setOffspringCount(m.getOffspringCount() == null ? 1 : m.getOffspringCount() + 1);
                m.setReproductiveStatus(Livestock.REPRO_OPEN);
                m.setExpectedDueDate(null);
                livestockRepository.save(m);
            });
        }

        if (birth.getBreedingId() != null) {
            breedingRepository.findById(birth.getBreedingId()).ifPresent(br -> {
                br.setStatus(LivestockBreeding.STATUS_COMPLETED);
                br.setNotes((br.getNotes() != null ? br.getNotes() + " " : "")
                        + "Birth recorded on " + birth.getBirthDate());
                breedingRepository.save(br);
            });
        } else if (motherId != null) {
            breedingRepository.findActiveByLivestockId(motherId,
                            List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT))
                    .forEach(br -> {
                        br.setStatus(LivestockBreeding.STATUS_COMPLETED);
                        br.setNotes((br.getNotes() != null ? br.getNotes() + " " : "")
                                + "Birth recorded on " + birth.getBirthDate());
                        breedingRepository.save(br);
                    });
        }
        return saved;
    }

    // ══════════════════════════════════════════════════════════════════════
    // UPDATE
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockBirth update(UUID birthId, LivestockBirth birth, UUID fatherId) {
        resolveAndSetLivestock(birth);
        resolveAndSetVeterinarian(birth);
        LivestockBirth saved = updateBirth(birthId, birth);
        if (fatherId != null) applyFatherToLinkedChildren(birthId, fatherId);
        return saved;
    }

    @Transactional
    public LivestockBirth update(UUID birthId, LivestockBirth birth) { return update(birthId, birth, null); }

    @Transactional
    public LivestockBirth updateBirth(UUID birthId, LivestockBirth updated) {
        LivestockBirth existing = birthRepository.findWithChildrenById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));
        assertNotLocked(existing);

        String beforeSnapshot = auditLogService.snapshot(existing);

        UUID effLivestock = updated.getLivestockId() != null ? updated.getLivestockId() : existing.getLivestockId();
        LocalDate effDate = updated.getBirthDate() != null ? updated.getBirthDate() : existing.getBirthDate();
        if (effLivestock != null && effDate != null) {
            validateBirthEligibility(effLivestock, effDate);
        }

        if (updated.getLivestockId() != null) existing.setLivestockId(updated.getLivestockId());
        if (updated.getBirthDate() != null)   existing.setBirthDate(updated.getBirthDate());
        existing.setVeterinarianId(updated.getVeterinarianId());

        boolean hasLinked = existing.getChildren() != null && existing.getChildren().stream()
                .anyMatch(o -> !Boolean.TRUE.equals(o.getIsDeleted()));
        if (!hasLinked) {
            if (updated.getOffspringCount() != null)  existing.setOffspringCount(updated.getOffspringCount());
            if (updated.getOffspringGender() != null) existing.setOffspringGender(updated.getOffspringGender());
        }
        if (updated.getWeaningDate() != null)      existing.setWeaningDate(updated.getWeaningDate());
        if (updated.getNextBreedingDate() != null) existing.setNextBreedingDate(updated.getNextBreedingDate());
        if (updated.getNotes() != null)            existing.setNotes(updated.getNotes());
        if (updated.getSourceLocation() != null)   existing.setSourceLocation(updated.getSourceLocation());
        if (updated.getIsExternalBirth() != null)  existing.setIsExternalBirth(updated.getIsExternalBirth());
        if (updated.getBirthType() != null)        existing.setBirthType(updated.getBirthType());
        if (updated.getAssistanceRequired() != null) existing.setAssistanceRequired(updated.getAssistanceRequired());
        if (updated.getNumberBornAlive() != null) {
            if (updated.getNumberBornAlive() < 0) throw new IllegalArgumentException("Number born alive cannot be negative");
            existing.setNumberBornAlive(updated.getNumberBornAlive());
        }
        if (updated.getNumberStillborn() != null) {
            if (updated.getNumberStillborn() < 0) throw new IllegalArgumentException("Number stillborn cannot be negative");
            existing.setNumberStillborn(updated.getNumberStillborn());
        }
        if (updated.getAttendantName() != null) existing.setAttendantName(updated.getAttendantName());

        validateOffspringCountConsistency(existing, hasLinked);

        if (existing.getWeaningDate() == null && existing.getBirthDate() != null
                && existing.getLivestockId() != null) {
            livestockRepository.findById(existing.getLivestockId()).ifPresent(m -> {
                if (m.getLivestockCategory() != null) {
                    existing.setWeaningDate(calculateRealWeaningDate(existing.getBirthDate(), m.getLivestockCategory()));
                }
            });
        }

        LivestockBirth saved = birthRepository.save(existing);
        String afterSnapshot = auditLogService.snapshot(saved);
        auditLogService.logUpdate("livestock_birth", saved.getId(), beforeSnapshot, afterSnapshot, "system");
        emailService.sendBirthUpdatedNotification(saved, "Fields updated");

        return saved;
    }

    // ══════════════════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public void delete(UUID birthId) { deleteBirth(birthId); }

    @Transactional
    public void deleteBirth(UUID birthId) {
        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));
        assertNotLocked(birth);

        String snapshot = auditLogService.snapshot(birth);

        for (LivestockOffspring o : offspringRepository.findByBirthEventIdAndIsDeletedFalse(birthId)) {
            if (o.getChildLivestock() != null) {
                Livestock child = o.getChildLivestock();
                child.setMother(null);
                livestockRepository.save(child);
            }
        }

        auditLogService.logDelete("livestock_birth", birthId, snapshot, "system");
        birthRepository.delete(birth);
        emailService.sendBirthDeletedNotification(birth);
    }

    // ══════════════════════════════════════════════════════════════════════
    // LOCK / REOPEN
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockBirth complete(UUID birthId) {
        LivestockBirth b = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));
        if (b.isLocked()) throw new IllegalStateException("This birth record is already completed.");
        b.setCompletedAt(LocalDateTime.now(APP_ZONE));
        b.setNotes((b.getNotes() != null ? b.getNotes() + " " : "")
                + "[Verified/Completed on " + LocalDate.now(APP_ZONE) + "]");
        return birthRepository.save(b);
    }

    @Transactional
    public LivestockBirth reopen(UUID birthId) {
        LivestockBirth b = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));
        if (!b.isLocked()) throw new IllegalStateException("This birth record is not currently locked.");
        b.setCompletedAt(null);
        b.setNotes((b.getNotes() != null ? b.getNotes() + " " : "")
                + "[Reopened/Amended on " + LocalDate.now(APP_ZONE) + " for correction]");
        return birthRepository.save(b);
    }

    // ══════════════════════════════════════════════════════════════════════
    // CHILD LINKING
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockOffspring addOffspring(UUID birthId, UUID childId, Integer generation,
                                           String outcome, BigDecimal weightKg, UUID fatherId) {
        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));
        assertNotLocked(birth);

        Livestock child = livestockRepository.findById(childId)
                .orElseThrow(() -> new RuntimeException("Child livestock not found"));
        Livestock mother = birth.getLivestockId() != null
                ? livestockRepository.findById(birth.getLivestockId()).orElse(null) : null;

        validateChildLinkage(birth, mother, child);

        String resolved = (outcome != null && !outcome.isBlank())
                ? outcome : LivestockOffspring.OUTCOME_ALIVE;

        LivestockOffspring o = new LivestockOffspring();
        o.setBirthEvent(birth);
        o.setChildLivestock(child);
        o.setGeneration(generation != null ? generation : 1);
        o.setOutcome(resolved);
        o.setBirthWeightKg(weightKg);
        o.setIsDeleted(false);
        o.setIsAlive(!LivestockOffspring.OUTCOME_STILLBORN.equalsIgnoreCase(resolved));

        if (mother != null) {
            child.setMother(mother);
            // ✅ FIXED: Livestock exposes the `draftBirthEvent` association, not `draftBirthId`.
            if (child.getDraftBirthEvent() != null
                    && child.getDraftBirthEvent().getId() != null
                    && child.getDraftBirthEvent().getId().equals(birthId)) {
                child.setDraftBirthEvent(null);
                child.setIsDraft(false);
            }
        }
        if (fatherId != null) livestockRepository.findById(fatherId).ifPresent(child::setFather);
        if (child.getBirthDate() == null) child.setBirthDate(birth.getBirthDate());
        if (!Boolean.TRUE.equals(o.getIsAlive())) {
            child.setStatus(Livestock.STATUS_DEAD);
        } else if (child.getStatus() == null) {
            child.setStatus(Livestock.STATUS_ACTIVE);
        }
        livestockRepository.save(child);

        LivestockOffspring saved = offspringRepository.save(o);
        recalcOffspringSummary(birthId);
        return saved;
    }

    @Transactional
    public LivestockOffspring linkChild(UUID birthId, UUID childId, String outcome, BigDecimal w, UUID fatherId) {
        return addOffspring(birthId, childId, 1, outcome, w, fatherId);
    }

    @Transactional
    public LivestockOffspring linkChild(UUID birthId, UUID childId) {
        return addOffspring(birthId, childId, 1, LivestockOffspring.OUTCOME_ALIVE, null, null);
    }

    @Transactional
    public void removeOffspring(UUID offspringId) {
        LivestockOffspring o = offspringRepository.findById(offspringId)
                .orElseThrow(() -> new RuntimeException("Offspring record not found"));
        if (o.getBirthEvent() != null) assertNotLocked(o.getBirthEvent());
        o.setIsDeleted(true);
        offspringRepository.save(o);
        if (o.getChildLivestock() != null) {
            Livestock child = o.getChildLivestock();
            child.setMother(null);
            livestockRepository.save(child);
        }
        if (o.getBirthEvent() != null) recalcOffspringSummary(o.getBirthEvent().getId());
    }

    @Transactional
    public void unlinkChild(UUID childId) {
        for (LivestockOffspring l : offspringRepository.findByChildLivestockIdAndIsDeletedFalse(childId)) {
            l.setIsDeleted(true);
            offspringRepository.save(l);
            if (l.getBirthEvent() != null) {
                recalcOffspringSummary(l.getBirthEvent().getId());
            }
        }
        livestockRepository.findById(childId).ifPresent(c -> {
            c.setMother(null);
            livestockRepository.save(c);
        });
    }

    @Transactional(readOnly = true)
    public List<LivestockOffspring> getOffspringByBirth(UUID birthId) {
        return offspringRepository.findByBirthEventIdAndIsDeletedFalse(birthId);
    }

    @Transactional(readOnly = true)
    public long countLiveOffspring(UUID birthId) {
        return offspringRepository.countLiveOffspringByBirthEventId(birthId);
    }

    @Transactional(readOnly = true)
    public Optional<LivestockBirth> getBirthByChildLivestock(UUID childId) {
        return offspringRepository.findFirstActiveByChildLivestockId(childId)
                .map(LivestockOffspring::getBirthEvent);
    }

    @Transactional(readOnly = true)
    public boolean isChildLivestock(UUID livestockId) {
        return offspringRepository.existsActiveByChildLivestockId(livestockId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // AUTO DATES
    // ══════════════════════════════════════════════════════════════════════

    public LocalDate calculateRealWeaningDate(LocalDate birthDate, LivestockCategory category) {
        if (birthDate == null || category == null) return null;
        Integer d = category.getWeaningPeriodDays();
        return (d == null || d < 0) ? null : birthDate.plusDays(d);
    }

    public LocalDate calculateRealNextBreedingDate(LocalDate birthDate, LivestockCategory category) {
        if (birthDate == null || category == null) return null;
        Integer d = category.getPostpartumRecoveryDays();
        return (d == null || d < 0) ? null : birthDate.plusDays(d);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDateSuggestionExplanation(LocalDate birthDate, LivestockCategory category) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (birthDate == null || category == null) return info;
        LocalDate w = calculateRealWeaningDate(birthDate, category);
        LocalDate b = calculateRealNextBreedingDate(birthDate, category);
        info.put("weaningDate", w);
        info.put("nextBreedingDate", b);
        if (w != null) info.put("weaningExplanation",
                "Birth date + " + category.getWeaningPeriodDays() + " days = " + w);
        if (b != null) info.put("breedingExplanation",
                "Birth date + " + category.getPostpartumRecoveryDays() + " days = " + b);
        return info;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAutoDatesForMother(UUID motherId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("birthDate", null);
        result.put("weaningDate", null);
        result.put("nextBreedingDate", null);
        if (motherId == null) return result;

        Livestock mother = livestockRepository.findById(motherId).orElse(null);
        if (mother == null) return result;

        LivestockCategory cat = mother.getLivestockCategory();
        LocalDate expected = null;

        List<LivestockBreeding> actives = breedingRepository.findActiveByLivestockId(motherId,
                List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT));
        LivestockBreeding latest = actives.stream()
                .filter(b -> b.getBreedingDate() != null)
                .max(Comparator.comparing(LivestockBreeding::getBreedingDate)).orElse(null);
        if (latest != null) {
            if (latest.getExpectedDueDate() != null) expected = latest.getExpectedDueDate();
            else if (cat != null && cat.getGestationPeriodDays() != null)
                expected = latest.getBreedingDate().plusDays(cat.getGestationPeriodDays());
        }
        if (expected != null) result.put("birthDate", expected.toString());

        LocalDate anchor = expected != null ? expected : LocalDate.now(APP_ZONE);
        if (cat != null) {
            LocalDate w = calculateRealWeaningDate(anchor, cat);
            if (w != null) {
                result.put("weaningDate", w.toString());
                result.put("weaningExplanation",
                        "Birth date + " + cat.getWeaningPeriodDays() + " days = " + w);
            }
            LocalDate b = calculateRealNextBreedingDate(anchor, cat);
            if (b != null) {
                result.put("nextBreedingDate", b.toString());
                result.put("breedingExplanation",
                        "Birth date + " + cat.getPostpartumRecoveryDays() + " days = " + b);
            }
        }
        if (expected != null && latest != null && latest.getExpectedDueDate() != null) {
            result.put("birthDateExplanation",
                    "From breeding record — expected due date was " + latest.getExpectedDueDate());
        } else if (expected != null && latest != null) {
            result.put("birthDateExplanation",
                    "From breeding record — " + latest.getBreedingDate()
                            + " + gestation " + (cat != null ? cat.getGestationPeriodDays() : "?") + " days");
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ANALYTICS
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public BirthPerformanceAnalyticsDto getPerformanceAnalytics(UUID categoryId,
                                                                LocalDate fromDate,
                                                                LocalDate toDate) {
        List<LivestockBirth> births = birthRepository.findAllByOrderByBirthDateDesc();
        if (fromDate != null || toDate != null) {
            births = births.stream()
                    .filter(b -> b.getBirthDate() != null)
                    .filter(b -> fromDate == null || !b.getBirthDate().isBefore(fromDate))
                    .filter(b -> toDate == null || !b.getBirthDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }

        Map<UUID, Livestock> mothers = getMothersForBirths(births);
        if (categoryId != null) {
            births = births.stream()
                    .filter(b -> {
                        Livestock m = mothers.get(b.getLivestockId());
                        return m != null && m.getLivestockCategory() != null
                                && categoryId.equals(m.getLivestockCategory().getId());
                    })
                    .collect(Collectors.toList());
        }

        BirthPerformanceAnalyticsDto dto = new BirthPerformanceAnalyticsDto();
        dto.setTotalBirthEvents(births.size());

        int offspring = 0;
        long male = 0, female = 0;

        for (LivestockBirth b : births) {
            int born = b.getOffspringCount() != null ? b.getOffspringCount() : 0;
            offspring += born;
            for (LivestockOffspring o : offspringRepository.findByBirthEventIdAndIsDeletedFalse(b.getId())) {
                Livestock c = o.getChildLivestock();
                if (c == null) continue;
                if ("MALE".equalsIgnoreCase(c.getGender()))        male++;
                else if ("FEMALE".equalsIgnoreCase(c.getGender())) female++;
            }
            Livestock m = mothers.get(b.getLivestockId());
            if (m != null && m.getLivestockCategory() != null) {
                String catName = m.getLivestockCategory().getName();
                BirthPerformanceAnalyticsDto.CategoryMetrics cm = dto.getByCategory()
                        .computeIfAbsent(catName, k -> new BirthPerformanceAnalyticsDto.CategoryMetrics());
                cm.setTotalBirthEvents(cm.getTotalBirthEvents() + 1);
                cm.setTotalOffspring(cm.getTotalOffspring() + born);
            }
        }

        dto.setTotalOffspring(offspring);
        dto.setMaleOffspringCount(male);
        dto.setFemaleOffspringCount(female);
        dto.setAverageLitterSize(births.isEmpty() ? 0.0 : (double) offspring / births.size());

        Map<UUID, List<LocalDate>> datesByMother = new TreeMap<>();
        for (LivestockBirth b : births) {
            if (b.getLivestockId() != null && b.getBirthDate() != null) {
                datesByMother.computeIfAbsent(b.getLivestockId(), k -> new ArrayList<>()).add(b.getBirthDate());
            }
        }
        long totalGapDays = 0;
        int gapCount = 0;
        for (List<LocalDate> dates : datesByMother.values()) {
            if (dates.size() < 2) continue;
            dates.sort(Comparator.naturalOrder());
            for (int i = 1; i < dates.size(); i++) {
                totalGapDays += ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
                gapCount++;
            }
        }
        dto.setAverageBirthIntervalDays(gapCount == 0 ? 0.0
                : round2((double) totalGapDays / gapCount));

        return dto;
    }

    // ══════════════════════════════════════════════════════════════════════
    // REPORTS
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<LivestockBirth> getBirthsByDateRange(LocalDate s, LocalDate e) {
        return birthRepository.findByBirthDateBetween(s, e);
    }

    @Transactional(readOnly = true)
    public BirthStatistics getBirthStatistics() {
        BirthStatistics stats = new BirthStatistics();
        List<LivestockBirth> births = birthRepository.findAllByOrderByBirthDateDesc();
        stats.setTotalBirths(births.size());
        int total = 0, live = 0, still = 0;
        for (LivestockBirth b : births) {
            total += b.getOffspringCount() != null ? b.getOffspringCount() : 0;
            for (LivestockOffspring o : offspringRepository.findByBirthEventIdAndIsDeletedFalse(b.getId())) {
                if (o.isAlive()) live++; else still++;
            }
        }
        stats.setTotalOffspring(total);
        stats.setLiveOffspring(live);
        stats.setStillborn(still);
        if (!births.isEmpty()) stats.setAverageOffspringPerBirth((double) total / births.size());
        Map<Integer, Integer> byYear = new TreeMap<>(Collections.reverseOrder());
        for (LivestockBirth b : births) {
            if (b.getBirthDate() != null) byYear.merge(b.getBirthDate().getYear(), 1, Integer::sum);
        }
        stats.setBirthsByYear(byYear);
        return stats;
    }

    @Transactional(readOnly = true)
    public BirthReportResult generateBirthReport(LocalDate fromDate, LocalDate toDate,
                                                 UUID categoryId, UUID motherId,
                                                 String birthType, String locationQuery) {
        log.debug("Generating birth report: from={}, to={}, categoryId={}, motherId={}, birthType={}, location={}",
                fromDate, toDate, categoryId, motherId, birthType, locationQuery);

        List<LivestockBirth> allBirths = birthRepository.findAllByOrderByBirthDateDesc();

        Set<UUID> allMotherIds = allBirths.stream()
                .map(LivestockBirth::getLivestockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Livestock> mothersById = allMotherIds.isEmpty()
                ? Collections.emptyMap()
                : livestockRepository.findAllById(allMotherIds).stream()
                .collect(Collectors.toMap(Livestock::getId, m -> m, (a, b) -> a));

        String locationNeedle = (locationQuery != null && !locationQuery.isBlank())
                ? locationQuery.trim().toLowerCase(Locale.ROOT) : null;
        String birthTypeNeedle = (birthType != null && !birthType.isBlank()) ? birthType.trim() : null;

        List<LivestockBirth> filtered = allBirths.stream()
                .filter(b -> fromDate == null || (b.getBirthDate() != null && !b.getBirthDate().isBefore(fromDate)))
                .filter(b -> toDate == null   || (b.getBirthDate() != null && !b.getBirthDate().isAfter(toDate)))
                .filter(b -> motherId == null || motherId.equals(b.getLivestockId()))
                .filter(b -> birthTypeNeedle == null || birthTypeNeedle.equalsIgnoreCase(b.getBirthType()))
                .filter(b -> categoryId == null || matchesCategory(mothersById.get(b.getLivestockId()), categoryId))
                .filter(b -> locationNeedle == null || matchesLocation(b, mothersById.get(b.getLivestockId()), locationNeedle))
                .collect(Collectors.toList());

        List<BirthReportRow> rows = new ArrayList<>();
        Map<String, CategoryReportStats> categorySummary = new TreeMap<>();
        Map<UUID, MotherPerformanceAccumulator> motherAccumulators = new LinkedHashMap<>();
        Map<YearMonth, Integer> monthlyTrend = new TreeMap<>();
        Map<YearMonth, Integer> monthlyOffspringTrend = new TreeMap<>();

        int totalOffspringBorn = 0, totalBornAlive = 0, totalStillborn = 0;
        int totalLinkedChildren = 0, totalMale = 0, totalFemale = 0;

        int rowIndex = 1;
        for (LivestockBirth birth : filtered) {
            Livestock mother = mothersById.get(birth.getLivestockId());
            String motherTag = mother != null ? mother.getTagNumber() : "Unknown";
            String categoryName = (mother != null && mother.getLivestockCategory() != null)
                    ? mother.getLivestockCategory().getName() : "Unknown";

            List<LivestockOffspring> links = offspringRepository.findByBirthEventIdAndIsDeletedFalse(birth.getId());
            List<Livestock> linkedChildren = links.stream()
                    .map(LivestockOffspring::getChildLivestock)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            int linkedCount = linkedChildren.size();
            long maleCount   = linkedChildren.stream().filter(c -> "MALE".equalsIgnoreCase(c.getGender())).count();
            long femaleCount = linkedChildren.stream().filter(c -> "FEMALE".equalsIgnoreCase(c.getGender())).count();

            int bornAlive = birth.getNumberBornAlive() != null ? birth.getNumberBornAlive() : 0;
            int stillborn = birth.getNumberStillborn() != null ? birth.getNumberStillborn() : 0;
            int totalBorn = birth.getOffspringCount() != null
                    ? birth.getOffspringCount() : (bornAlive + stillborn);

            BirthReportRow row = new BirthReportRow();
            row.setIndex(rowIndex++);
            row.setBirthId(birth.getId());
            row.setMotherTag(motherTag);
            row.setCategory(categoryName);
            row.setBirthDate(birth.getBirthDate());
            row.setTotalBorn(totalBorn);
            row.setBornAlive(bornAlive);
            row.setStillborn(stillborn);
            row.setMaleCount((int) maleCount);
            row.setFemaleCount((int) femaleCount);
            row.setLinkedChildren(linkedCount);
            row.setBirthType(birth.getBirthType());
            row.setAssistanceRequired(Boolean.TRUE.equals(birth.getAssistanceRequired()));
            row.setOutcomeLabel(buildOutcomeLabel(birth, totalBorn, bornAlive, stillborn));
            row.setLocked(birth.isLocked());
            rows.add(row);

            totalOffspringBorn += totalBorn;
            totalBornAlive += bornAlive;
            totalStillborn += stillborn;
            totalLinkedChildren += linkedCount;
            totalMale += (int) maleCount;
            totalFemale += (int) femaleCount;

            CategoryReportStats catStats = categorySummary.computeIfAbsent(categoryName, CategoryReportStats::new);
            catStats.addBirth(totalBorn, bornAlive, stillborn, linkedCount);

            if (mother != null) {
                MotherPerformanceAccumulator acc = motherAccumulators.computeIfAbsent(
                        mother.getId(), id -> new MotherPerformanceAccumulator(motherTag));
                acc.addBirth(birth, totalBorn, bornAlive, stillborn);
            }

            if (birth.getBirthDate() != null) {
                YearMonth ym = YearMonth.from(birth.getBirthDate());
                monthlyTrend.merge(ym, 1, Integer::sum);
                monthlyOffspringTrend.merge(ym, totalBorn, Integer::sum);
            }
        }

        int totalBirths = filtered.size();
        long uniqueMothers = filtered.stream()
                .map(LivestockBirth::getLivestockId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        double survivalRate = (totalOffspringBorn > 0)
                ? round1((totalBornAlive * 100.0) / totalOffspringBorn) : 0.0;
        double stillbirthRate = (totalOffspringBorn > 0)
                ? round1((totalStillborn * 100.0) / totalOffspringBorn) : 0.0;
        double registrationRate = (totalOffspringBorn > 0)
                ? round1((totalLinkedChildren * 100.0) / totalOffspringBorn) : 0.0;
        double avgPerBirth = (totalBirths > 0) ? round2((double) totalOffspringBorn / totalBirths) : 0.0;
        double avgPerMother = (uniqueMothers > 0) ? round2((double) totalOffspringBorn / uniqueMothers) : 0.0;

        List<MotherPerformanceRow> motherPerformance = motherAccumulators.values().stream()
                .map(MotherPerformanceAccumulator::toRow)
                .sorted(Comparator.comparing(MotherPerformanceRow::getMotherTag))
                .collect(Collectors.toList());

        String topMother = null;
        int topMotherOffspring = 0;
        MotherPerformanceRow topRow = motherPerformance.stream()
                .max(Comparator.comparingInt(MotherPerformanceRow::getTotalOffspring))
                .filter(r -> r.getTotalOffspring() > 0)
                .orElse(null);
        if (topRow != null) {
            topRow.setTopPerformer(true);
            topMother = topRow.getMotherTag();
            topMotherOffspring = topRow.getTotalOffspring();
        }

        String topCategory = null;
        double topCategoryRate = 0.0;
        for (CategoryReportStats s : categorySummary.values()) {
            if (s.getTotalOffspring() <= 0) continue;
            double rate = round1((s.getBornAlive() * 100.0) / s.getTotalOffspring());
            if (topCategory == null || rate > topCategoryRate) {
                topCategory = s.getCategory();
                topCategoryRate = rate;
            }
        }

        int maxMonthlyTrend = monthlyTrend.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int maxMonthlyOffspringTrend = monthlyOffspringTrend.values().stream()
                .mapToInt(Integer::intValue).max().orElse(0);

        PeriodComparison comparison = buildPeriodComparison(
                allBirths, mothersById, fromDate, toDate,
                categoryId, motherId, birthTypeNeedle, locationNeedle, totalBirths);

        BirthReportResult result = new BirthReportResult();
        result.setFromDate(fromDate);
        result.setToDate(toDate);
        result.setTotalBirths(totalBirths);
        result.setUniqueMothers((int) uniqueMothers);
        result.setTotalOffspringBorn(totalOffspringBorn);
        result.setTotalBornAlive(totalBornAlive);
        result.setTotalStillborn(totalStillborn);
        result.setLinkedChildren(totalLinkedChildren);
        result.setSurvivalRatePercent(survivalRate);
        result.setLiveBirthRate(survivalRate);
        result.setStillbirthRate(stillbirthRate);
        result.setRegistrationCompletionRate(registrationRate);
        result.setAverageOffspringPerBirth(avgPerBirth);
        result.setAverageOffspringPerMother(avgPerMother);
        result.setTotalMale(totalMale);
        result.setTotalFemale(totalFemale);
        result.setTopCategory(topCategory);
        result.setTopCategoryRate(topCategoryRate);
        result.setTopMother(topMother);
        result.setTopMotherOffspring(topMotherOffspring);
        result.setRows(rows);
        result.setCategorySummary(categorySummary);
        result.setMonthlyTrend(toDisplayTrend(monthlyTrend));
        result.setMonthlyOffspringTrend(toDisplayTrend(monthlyOffspringTrend));
        result.setMaxMonthlyTrend(maxMonthlyTrend);
        result.setMaxMonthlyOffspringTrend(maxMonthlyOffspringTrend);
        result.setMotherPerformance(motherPerformance);
        result.setPeriodComparison(comparison);
        return result;
    }

    private boolean matchesCategory(Livestock mother, UUID categoryId) {
        return mother != null && mother.getLivestockCategory() != null
                && categoryId.equals(mother.getLivestockCategory().getId());
    }

    private boolean matchesLocation(LivestockBirth birth, Livestock mother, String needle) {
        if (birth.getSourceLocation() != null
                && birth.getSourceLocation().toLowerCase(Locale.ROOT).contains(needle)) return true;
        return mother != null && mother.getLocation() != null && mother.getLocation().getName() != null
                && mother.getLocation().getName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private String buildOutcomeLabel(LivestockBirth birth, int totalBorn, int bornAlive, int stillborn) {
        List<String> parts = new ArrayList<>();
        String type = birth.getBirthType();
        if (type == null || type.isBlank()) parts.add("Not Recorded");
        else if (LivestockBirth.BIRTH_TYPE_DIFFICULT.equalsIgnoreCase(type)) parts.add("Difficult Birth");
        else if (LivestockBirth.BIRTH_TYPE_NORMAL.equalsIgnoreCase(type))    parts.add("Normal");
        else                                                                 parts.add(type);

        if (Boolean.TRUE.equals(birth.getAssistanceRequired())) parts.add("Assisted");
        if (totalBorn > 0 && bornAlive == 0 && stillborn > 0)  parts.add("Total Stillbirth");
        else if (stillborn > 0)                                 parts.add(stillborn + " Stillborn");

        return String.join(" · ", parts);
    }

    private LinkedHashMap<String, Integer> toDisplayTrend(Map<YearMonth, Integer> trend) {
        LinkedHashMap<String, Integer> display = new LinkedHashMap<>();
        trend.forEach((ym, c) -> display.put(ym.toString(), c));
        return display;
    }

    private PeriodComparison buildPeriodComparison(List<LivestockBirth> allBirths,
                                                   Map<UUID, Livestock> mothersById,
                                                   LocalDate fromDate, LocalDate toDate,
                                                   UUID categoryId, UUID motherId,
                                                   String birthTypeNeedle, String locationNeedle,
                                                   int currentPeriodBirths) {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) return null;

        long periodDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LocalDate prevTo = fromDate.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(periodDays - 1);

        long prevCount = allBirths.stream()
                .filter(b -> b.getBirthDate() != null
                        && !b.getBirthDate().isBefore(prevFrom)
                        && !b.getBirthDate().isAfter(prevTo))
                .filter(b -> motherId == null || motherId.equals(b.getLivestockId()))
                .filter(b -> birthTypeNeedle == null || birthTypeNeedle.equalsIgnoreCase(b.getBirthType()))
                .filter(b -> categoryId == null || matchesCategory(mothersById.get(b.getLivestockId()), categoryId))
                .filter(b -> locationNeedle == null || matchesLocation(b, mothersById.get(b.getLivestockId()), locationNeedle))
                .count();

        double pct = (prevCount == 0)
                ? (currentPeriodBirths > 0 ? 100.0 : 0.0)
                : round1(((currentPeriodBirths - prevCount) * 100.0) / prevCount);

        PeriodComparison c = new PeriodComparison();
        c.setPreviousFromDate(prevFrom);
        c.setPreviousToDate(prevTo);
        c.setCurrentPeriodBirths(currentPeriodBirths);
        c.setPreviousPeriodBirths((int) prevCount);
        c.setChangePercent(pct);
        return c;
    }

    private double round1(double v) { return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue(); }
    private double round2(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue(); }

    // ══════════════════════════════════════════════════════════════════════
    // REPAIR
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public int repairMissingMotherLinks() {
        int fixed = 0;
        for (LivestockOffspring o : offspringRepository.findAllMissingMotherOnChild()) {
            Livestock child  = o.getChildLivestock();
            Livestock mother = o.getBirthEvent() != null ? o.getBirthEvent().getLivestock() : null;
            if (child == null || mother == null) continue;
            child.setMother(mother);
            if (child.getBirthDate() == null && o.getBirthEvent().getBirthDate() != null) {
                child.setBirthDate(o.getBirthEvent().getBirthDate());
            }
            livestockRepository.save(child);
            fixed++;
        }
        return fixed;
    }

    @Transactional
    public int repairStalePregnanciesAfterBirth() {
        int fixed = 0;
        Set<UUID> ids = getAll().stream().map(LivestockBirth::getLivestockId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (UUID id : ids) {
            Livestock mother = livestockRepository.findById(id).orElse(null);
            if (mother == null) continue;
            boolean still = Livestock.REPRO_PREGNANT.equals(mother.getReproductiveStatus());
            List<LivestockBreeding> lingering = breedingRepository.findActiveByLivestockId(id,
                    List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT));
            if (still || !lingering.isEmpty()) {
                if (still) {
                    mother.setReproductiveStatus(Livestock.REPRO_OPEN);
                    mother.setExpectedDueDate(null);
                    livestockRepository.save(mother);
                }
                for (LivestockBreeding br : lingering) {
                    br.setStatus(LivestockBreeding.STATUS_COMPLETED);
                    br.setNotes((br.getNotes() != null ? br.getNotes() + " " : "")
                            + "Repaired: birth already on file.");
                    breedingRepository.save(br);
                }
                fixed++;
            }
        }
        return fixed;
    }

    // ══════════════════════════════════════════════════════════════════════
    // VETERINARIAN
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public Veterinarian quickAddVeterinarian(String firstName, String lastName, String phone) {
        if (firstName == null || firstName.trim().isEmpty())
            throw new IllegalArgumentException("First name is required.");
        if (lastName == null || lastName.trim().isEmpty())
            throw new IllegalArgumentException("Last name is required.");
        Veterinarian vet = new Veterinarian();
        vet.setFirstName(firstName.trim());
        vet.setLastName(lastName.trim());
        if (phone != null && !phone.trim().isEmpty()) vet.setPhone(phone.trim());
        vet.setIsActive(true);
        vet.setIsDeleted(false);
        vet.setNotes("Quick-added on " + LocalDate.now(APP_ZONE));
        return veterinarianRepository.save(vet);
    }

    @Transactional(readOnly = true)
    public List<Veterinarian> findPossibleDuplicateVeterinarians(String first, String last) {
        if (first == null || last == null) return Collections.emptyList();
        return veterinarianRepository.findPossibleDuplicates(first.trim(), last.trim());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private List<Livestock> loadAndValidateChildren(List<UUID> ids, LivestockBirth birth) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<Livestock> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Livestock child = livestockRepository.findById(id).orElse(null);
            if (child == null) {
                throw new IllegalArgumentException("Child animal not found: " + id);
            }
            validateChildLinkage(birth, null, child);
            result.add(child);
        }
        return result;
    }

    private void recalcOffspringSummary(UUID birthId) {
        birthRepository.findById(birthId).ifPresent(birth -> {
            List<Livestock> children = offspringRepository
                    .findByBirthEventIdAndIsDeletedFalse(birthId).stream()
                    .map(LivestockOffspring::getChildLivestock)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            birth.setOffspringCount(children.size());
            birth.setOffspringGender(buildGenderSummary(children));
            birthRepository.save(birth);
        });
    }

    private String buildGenderSummary(List<Livestock> children) {
        long m = children.stream().filter(c -> "MALE".equalsIgnoreCase(c.getGender())).count();
        long f = children.stream().filter(c -> "FEMALE".equalsIgnoreCase(c.getGender())).count();
        long u = children.size() - m - f;
        List<String> parts = new ArrayList<>();
        if (m > 0) parts.add(m + "M");
        if (f > 0) parts.add(f + "F");
        if (u > 0) parts.add(u + "U");
        String summary = String.join(", ", parts);
        return summary.length() > 20 ? summary.substring(0, 20) : summary;
    }

    private void validateOffspringCountConsistency(LivestockBirth birth, boolean hasLinked) {
        Integer alive = birth.getNumberBornAlive();
        if (alive == null) return;
        int still = birth.getNumberStillborn() != null ? birth.getNumberStillborn() : 0;
        int total = alive + still;
        if (hasLinked) {
            if (birth.getOffspringCount() != null && birth.getOffspringCount() > total) {
                throw new IllegalArgumentException(
                        "Inconsistent data: " + birth.getOffspringCount()
                                + " offspring linked but only " + total + " total reported.");
            }
            return;
        }
        if (birth.getOffspringCount() != null && birth.getOffspringCount() != 0
                && !birth.getOffspringCount().equals(total)) {
            throw new IllegalArgumentException(
                    "Number of Offspring must equal Born Alive + Stillborn. Got "
                            + birth.getOffspringCount() + " vs " + total + ".");
        }
        if (birth.getOffspringCount() == null || birth.getOffspringCount() == 0) {
            birth.setOffspringCount(total);
        }
    }

    private void validateBirthEligibility(UUID motherId, LocalDate birthDate) {
        Livestock mother = livestockRepository.findById(motherId)
                .orElseThrow(() -> new IllegalArgumentException("Mother animal not found"));
        List<LivestockBreeding> actives = breedingRepository.findActiveByLivestockId(motherId,
                List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT));
        boolean flagged = Livestock.REPRO_PREGNANT.equals(mother.getReproductiveStatus());
        if (actives.isEmpty() && !flagged) {
            throw new IllegalStateException(mother.getTagNumber() + " is not currently marked as pregnant.");
        }

        LivestockBreeding breeding = actives.stream()
                .filter(b -> b.getBreedingDate() != null)
                .max(Comparator.comparing(LivestockBreeding::getBreedingDate)).orElse(null);
        if (breeding == null) return;

        LocalDate breedingDate = breeding.getBreedingDate();
        if (!birthDate.isAfter(breedingDate)) {
            throw new IllegalStateException("Birth date (" + birthDate
                    + ") cannot be on or before the breeding date (" + breedingDate + ").");
        }

        long days = ChronoUnit.DAYS.between(breedingDate, birthDate);
        LivestockCategory cat = mother.getLivestockCategory();
        Integer gestation = cat != null ? cat.getGestationPeriodDays() : null;
        if (gestation != null && gestation > 0) {
            long min = Math.round(gestation * (1 - GESTATION_TOLERANCE_FRACTION));
            if (days < min) {
                throw new IllegalStateException(mother.getTagNumber()
                        + " is not yet due. Only " + days + " days since breeding; earliest plausible = "
                        + breedingDate.plusDays(min));
            }
        } else if (breeding.getExpectedDueDate() != null) {
            LocalDate earliest = breeding.getExpectedDueDate().minusDays(EXPECTED_DUE_DATE_FALLBACK_TOLERANCE_DAYS);
            if (birthDate.isBefore(earliest)) {
                throw new IllegalStateException("Birth date before earliest plausible = " + earliest);
            }
        }
    }

    private void assertNotLocked(LivestockBirth birth) {
        if (birth.isLocked()) throw new IllegalStateException(LOCKED_MESSAGE);
    }

    private void validateChildLinkage(LivestockBirth birth, Livestock mother, Livestock child) {
        if (Livestock.STATUS_DEAD.equals(child.getStatus())) {
            throw new IllegalStateException("Child animal is recorded as dead.");
        }
        if (Livestock.STATUS_SOLD.equals(child.getStatus())) {
            throw new IllegalStateException("Child animal is already recorded as sold.");
        }
        if (mother != null && Livestock.STATUS_DEAD.equals(mother.getStatus())) {
            throw new IllegalStateException("Mother animal is recorded as dead.");
        }
        if (mother != null && mother.getLivestockCategory() != null && child.getLivestockCategory() != null
                && !mother.getLivestockCategory().getId().equals(child.getLivestockCategory().getId())) {
            throw new IllegalStateException("Category mismatch between mother and child.");
        }
        if (birth.getBirthDate() != null && child.getBirthDate() != null) {
            long diff = Math.abs(ChronoUnit.DAYS.between(birth.getBirthDate(), child.getBirthDate()));
            if (diff > CHILD_BIRTH_DATE_TOLERANCE_DAYS) {
                throw new IllegalStateException("Child birth date is more than "
                        + CHILD_BIRTH_DATE_TOLERANCE_DAYS + " days from birth record.");
            }
        }
        if (mother != null && child.getMother() != null
                && !child.getMother().getId().equals(mother.getId())) {
            throw new IllegalStateException("Child already linked to a different mother.");
        }
        if (offspringRepository.existsActiveByChildLivestockId(child.getId())) {
            throw new IllegalStateException("Child already linked to a birth event.");
        }
    }

    private void applyFatherToLinkedChildren(UUID birthId, UUID fatherId) {
        livestockRepository.findById(fatherId).ifPresent(father -> {
            for (LivestockOffspring l : offspringRepository.findByBirthEventIdAndIsDeletedFalse(birthId)) {
                Livestock child = l.getChildLivestock();
                if (child != null) { child.setFather(father); livestockRepository.save(child); }
            }
        });
    }

    private void resolveAndSetLivestock(LivestockBirth birth) {
        String idStr = birth.getLivestockIdValue();
        if (idStr == null || idStr.isBlank()) {
            birth.setLivestockId(null);
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(idStr.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid mother selection.");
        }
        Livestock mother = livestockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Selected mother not found."));
        if (Boolean.TRUE.equals(mother.getIsDraft())) {
            throw new IllegalArgumentException("Selected mother is still a draft — complete registration first.");
        }
        if (Livestock.STATUS_DEAD.equals(mother.getStatus())) {
            throw new IllegalArgumentException("Selected mother is recorded as dead.");
        }
        birth.setLivestockId(id);
    }

    private void resolveAndSetVeterinarian(LivestockBirth birth) {
        String idStr = birth.getVeterinarianIdValue();
        if (idStr != null && !idStr.isBlank()) {
            UUID vid;
            try { vid = UUID.fromString(idStr.trim()); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid veterinarian selection.");
            }
            Veterinarian vet = veterinarianRepository.findById(vid)
                    .orElseThrow(() -> new IllegalArgumentException("Selected veterinarian not found."));
            if (!Boolean.TRUE.equals(vet.getIsActive()) || Boolean.TRUE.equals(vet.getIsDeleted())) {
                throw new IllegalArgumentException("Selected veterinarian is not active.");
            }
            birth.setVeterinarianId(vid);
        } else {
            birth.setVeterinarianId(null);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // INNER DTO CLASSES
    // ══════════════════════════════════════════════════════════════════════

    public static class BirthStatistics {
        private int totalBirths;
        private int totalOffspring;
        private int liveOffspring;
        private int stillborn;
        private double averageOffspringPerBirth;
        private Map<Integer, Integer> birthsByYear = new HashMap<>();
        public int getTotalBirths() { return totalBirths; } public void setTotalBirths(int v) { this.totalBirths = v; }
        public int getTotalOffspring() { return totalOffspring; } public void setTotalOffspring(int v) { this.totalOffspring = v; }
        public int getLiveOffspring() { return liveOffspring; } public void setLiveOffspring(int v) { this.liveOffspring = v; }
        public int getStillborn() { return stillborn; } public void setStillborn(int v) { this.stillborn = v; }
        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double v) { this.averageOffspringPerBirth = v; }
        public Map<Integer, Integer> getBirthsByYear() { return birthsByYear; } public void setBirthsByYear(Map<Integer, Integer> v) { this.birthsByYear = v; }
    }

    public static class CategoryBirthStats {
        private final String categoryName;
        private long totalBirths;
        private long locked;
        public CategoryBirthStats(String n) { this.categoryName = n; }
        public void incrementTotalBirths() { this.totalBirths++; }
        public void incrementLocked() { this.locked++; }
        public void addTotals(long total, long locked) { this.totalBirths += total; this.locked += locked; }
        public String getCategoryName() { return categoryName; }
        public long getTotalBirths() { return totalBirths; }
        public long getLocked() { return locked; }
    }

    public static class BirthReportRow {
        private int index;
        private UUID birthId;
        private String motherTag;
        private String category;
        private LocalDate birthDate;
        private int totalBorn;
        private int bornAlive;
        private int stillborn;
        private int maleCount;
        private int femaleCount;
        private int linkedChildren;
        private String birthType;
        private boolean assistanceRequired;
        private String outcomeLabel;
        private boolean locked;

        public int getIndex() { return index; } public void setIndex(int v) { this.index = v; }
        public UUID getBirthId() { return birthId; } public void setBirthId(UUID v) { this.birthId = v; }
        public String getMotherTag() { return motherTag; } public void setMotherTag(String v) { this.motherTag = v; }
        public String getCategory() { return category; } public void setCategory(String v) { this.category = v; }
        public LocalDate getBirthDate() { return birthDate; } public void setBirthDate(LocalDate v) { this.birthDate = v; }
        public int getTotalBorn() { return totalBorn; } public void setTotalBorn(int v) { this.totalBorn = v; }
        public int getBornAlive() { return bornAlive; } public void setBornAlive(int v) { this.bornAlive = v; }
        public int getStillborn() { return stillborn; } public void setStillborn(int v) { this.stillborn = v; }
        public int getMaleCount() { return maleCount; } public void setMaleCount(int v) { this.maleCount = v; }
        public int getFemaleCount() { return femaleCount; } public void setFemaleCount(int v) { this.femaleCount = v; }
        public int getLinkedChildren() { return linkedChildren; } public void setLinkedChildren(int v) { this.linkedChildren = v; }
        public String getBirthType() { return birthType; } public void setBirthType(String v) { this.birthType = v; }
        public boolean isAssistanceRequired() { return assistanceRequired; } public void setAssistanceRequired(boolean v) { this.assistanceRequired = v; }
        public String getOutcomeLabel() { return outcomeLabel; } public void setOutcomeLabel(String v) { this.outcomeLabel = v; }
        public boolean isLocked() { return locked; } public void setLocked(boolean v) { this.locked = v; }
    }

    public static class CategoryReportStats {
        private final String category;
        private int births;
        private int totalOffspring;
        private int bornAlive;
        private int stillborn;
        private int linkedChildren;
        public CategoryReportStats(String category) { this.category = category; }
        void addBirth(int totalBorn, int alive, int still, int linked) {
            this.births++;
            this.totalOffspring += totalBorn;
            this.bornAlive += alive;
            this.stillborn += still;
            this.linkedChildren += linked;
        }
        public String getCategory() { return category; }
        public int getBirths() { return births; }
        public int getTotalOffspring() { return totalOffspring; }
        public int getBornAlive() { return bornAlive; }
        public int getStillborn() { return stillborn; }
        public int getLinkedChildren() { return linkedChildren; }
        public double getAverageOffspring() {
            return births > 0 ? (double) totalOffspring / births : 0.0;
        }
    }

    public static class MotherPerformanceRow {
        private String motherTag;
        private int totalBirths;
        private int totalOffspring;
        private double averageOffspringPerBirth;
        private int bornAlive;
        private int stillborn;
        private LocalDate lastBirthDate;
        private LocalDate nextBreedingDate;
        private boolean firstTimeMother;
        private boolean topPerformer;
        public String getMotherTag() { return motherTag; } public void setMotherTag(String v) { this.motherTag = v; }
        public int getTotalBirths() { return totalBirths; } public void setTotalBirths(int v) { this.totalBirths = v; }
        public int getTotalOffspring() { return totalOffspring; } public void setTotalOffspring(int v) { this.totalOffspring = v; }
        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; } public void setAverageOffspringPerBirth(double v) { this.averageOffspringPerBirth = v; }
        public int getBornAlive() { return bornAlive; } public void setBornAlive(int v) { this.bornAlive = v; }
        public int getStillborn() { return stillborn; } public void setStillborn(int v) { this.stillborn = v; }
        public LocalDate getLastBirthDate() { return lastBirthDate; } public void setLastBirthDate(LocalDate v) { this.lastBirthDate = v; }
        public LocalDate getNextBreedingDate() { return nextBreedingDate; } public void setNextBreedingDate(LocalDate v) { this.nextBreedingDate = v; }
        public boolean isFirstTimeMother() { return firstTimeMother; } public void setFirstTimeMother(boolean v) { this.firstTimeMother = v; }
        public boolean isTopPerformer() { return topPerformer; } public void setTopPerformer(boolean v) { this.topPerformer = v; }
    }

    private static class MotherPerformanceAccumulator {
        private final String motherTag;
        private int totalBirths = 0;
        private int totalOffspring = 0;
        private int bornAlive = 0;
        private int stillborn = 0;
        private LocalDate lastBirthDate;
        private LocalDate nextBreedingDate;
        MotherPerformanceAccumulator(String motherTag) { this.motherTag = motherTag; }
        void addBirth(LivestockBirth birth, int totalBorn, int alive, int still) {
            totalBirths++;
            totalOffspring += totalBorn;
            bornAlive += alive;
            stillborn += still;
            if (birth.getBirthDate() != null
                    && (lastBirthDate == null || birth.getBirthDate().isAfter(lastBirthDate))) {
                lastBirthDate = birth.getBirthDate();
                nextBreedingDate = birth.getNextBreedingDate();
            }
        }
        MotherPerformanceRow toRow() {
            MotherPerformanceRow r = new MotherPerformanceRow();
            r.setMotherTag(motherTag);
            r.setTotalBirths(totalBirths);
            r.setTotalOffspring(totalOffspring);
            r.setBornAlive(bornAlive);
            r.setStillborn(stillborn);
            r.setAverageOffspringPerBirth(totalBirths > 0
                    ? BigDecimal.valueOf((double) totalOffspring / totalBirths)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0);
            r.setLastBirthDate(lastBirthDate);
            r.setNextBreedingDate(nextBreedingDate);
            r.setFirstTimeMother(totalBirths == 1);
            return r;
        }
    }

    public static class PeriodComparison {
        private LocalDate previousFromDate;
        private LocalDate previousToDate;
        private int currentPeriodBirths;
        private int previousPeriodBirths;
        private double changePercent;
        public LocalDate getPreviousFromDate() { return previousFromDate; } public void setPreviousFromDate(LocalDate v) { this.previousFromDate = v; }
        public LocalDate getPreviousToDate() { return previousToDate; } public void setPreviousToDate(LocalDate v) { this.previousToDate = v; }
        public int getCurrentPeriodBirths() { return currentPeriodBirths; } public void setCurrentPeriodBirths(int v) { this.currentPeriodBirths = v; }
        public int getPreviousPeriodBirths() { return previousPeriodBirths; } public void setPreviousPeriodBirths(int v) { this.previousPeriodBirths = v; }
        public double getChangePercent() { return changePercent; } public void setChangePercent(double v) { this.changePercent = v; }
    }

    public static class BirthReportResult {
        private LocalDate fromDate;
        private LocalDate toDate;
        private int totalBirths;
        private int uniqueMothers;
        private int totalOffspringBorn;
        private int totalBornAlive;
        private int totalStillborn;
        private int linkedChildren;
        private double survivalRatePercent;
        private double liveBirthRate;
        private double stillbirthRate;
        private double registrationCompletionRate;
        private double averageOffspringPerBirth;
        private double averageOffspringPerMother;
        private int totalMale;
        private int totalFemale;
        private String topCategory;
        private double topCategoryRate;
        private String topMother;
        private int topMotherOffspring;
        private List<BirthReportRow> rows;
        private Map<String, CategoryReportStats> categorySummary;
        private LinkedHashMap<String, Integer> monthlyTrend;
        private LinkedHashMap<String, Integer> monthlyOffspringTrend;
        private int maxMonthlyTrend;
        private int maxMonthlyOffspringTrend;
        private List<MotherPerformanceRow> motherPerformance;
        private PeriodComparison periodComparison;

        public LocalDate getFromDate() { return fromDate; } public void setFromDate(LocalDate v) { this.fromDate = v; }
        public LocalDate getToDate() { return toDate; } public void setToDate(LocalDate v) { this.toDate = v; }
        public int getTotalBirths() { return totalBirths; } public void setTotalBirths(int v) { this.totalBirths = v; }
        public int getUniqueMothers() { return uniqueMothers; } public void setUniqueMothers(int v) { this.uniqueMothers = v; }
        public int getTotalOffspringBorn() { return totalOffspringBorn; } public void setTotalOffspringBorn(int v) { this.totalOffspringBorn = v; }
        public int getTotalBornAlive() { return totalBornAlive; } public void setTotalBornAlive(int v) { this.totalBornAlive = v; }
        public int getTotalStillborn() { return totalStillborn; } public void setTotalStillborn(int v) { this.totalStillborn = v; }
        public int getLinkedChildren() { return linkedChildren; } public void setLinkedChildren(int v) { this.linkedChildren = v; }
        public double getSurvivalRatePercent() { return survivalRatePercent; } public void setSurvivalRatePercent(double v) { this.survivalRatePercent = v; }
        public double getLiveBirthRate() { return liveBirthRate; } public void setLiveBirthRate(double v) { this.liveBirthRate = v; }
        public double getStillbirthRate() { return stillbirthRate; } public void setStillbirthRate(double v) { this.stillbirthRate = v; }
        public double getRegistrationCompletionRate() { return registrationCompletionRate; } public void setRegistrationCompletionRate(double v) { this.registrationCompletionRate = v; }
        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; } public void setAverageOffspringPerBirth(double v) { this.averageOffspringPerBirth = v; }
        public double getAverageOffspringPerMother() { return averageOffspringPerMother; } public void setAverageOffspringPerMother(double v) { this.averageOffspringPerMother = v; }
        public int getTotalMale() { return totalMale; } public void setTotalMale(int v) { this.totalMale = v; }
        public int getTotalFemale() { return totalFemale; } public void setTotalFemale(int v) { this.totalFemale = v; }
        public String getTopCategory() { return topCategory; } public void setTopCategory(String v) { this.topCategory = v; }
        public double getTopCategoryRate() { return topCategoryRate; } public void setTopCategoryRate(double v) { this.topCategoryRate = v; }
        public String getTopMother() { return topMother; } public void setTopMother(String v) { this.topMother = v; }
        public int getTopMotherOffspring() { return topMotherOffspring; } public void setTopMotherOffspring(int v) { this.topMotherOffspring = v; }
        public List<BirthReportRow> getRows() { return rows; } public void setRows(List<BirthReportRow> v) { this.rows = v; }
        public Map<String, CategoryReportStats> getCategorySummary() { return categorySummary; } public void setCategorySummary(Map<String, CategoryReportStats> v) { this.categorySummary = v; }
        public LinkedHashMap<String, Integer> getMonthlyTrend() { return monthlyTrend; } public void setMonthlyTrend(LinkedHashMap<String, Integer> v) { this.monthlyTrend = v; }
        public LinkedHashMap<String, Integer> getMonthlyOffspringTrend() { return monthlyOffspringTrend; } public void setMonthlyOffspringTrend(LinkedHashMap<String, Integer> v) { this.monthlyOffspringTrend = v; }
        public int getMaxMonthlyTrend() { return maxMonthlyTrend; } public void setMaxMonthlyTrend(int v) { this.maxMonthlyTrend = v; }
        public int getMaxMonthlyOffspringTrend() { return maxMonthlyOffspringTrend; } public void setMaxMonthlyOffspringTrend(int v) { this.maxMonthlyOffspringTrend = v; }
        public List<MotherPerformanceRow> getMotherPerformance() { return motherPerformance; } public void setMotherPerformance(List<MotherPerformanceRow> v) { this.motherPerformance = v; }
        public PeriodComparison getPeriodComparison() { return periodComparison; } public void setPeriodComparison(PeriodComparison v) { this.periodComparison = v; }
    }
}