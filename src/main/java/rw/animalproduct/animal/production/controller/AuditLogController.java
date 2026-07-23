package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.entity.AuditLog;
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.BuyerRepository;
import rw.animalproduct.animal.production.repository.RepresentativeRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;
// ── NEW: livestock repositories ─────────────────────────────────────────
// Adjust these imports/class names if your actual repository interfaces are
// named differently — they should each be a plain JpaRepository<Entity, UUID>
// living in the same repository package as everything else.
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;
import rw.animalproduct.animal.production.repository.LivestockSickRepository;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.repository.LivestockDeathRepository;
import rw.animalproduct.animal.production.services.AuditLogService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    // Master data
    private final RepresentativeRepository representativeRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final BuyerRepository buyerRepository;
    private final VeterinarianRepository veterinarianRepository;

    // NEW — Livestock modules: Birth, Livestock, Sick, Breeding, Sales, Death
    private final LivestockRepository livestockRepository;
    private final LivestockBirthRepository livestockBirthRepository;
    private final LivestockSickRepository livestockSickRepository;
    private final LivestockBreedingRepository livestockBreedingRepository;
    private final LivestockSaleRepository livestockSaleRepository;
    private final LivestockDeathRepository livestockDeathRepository;

    public AuditLogController(AuditLogService auditLogService,
                              RepresentativeRepository representativeRepository,
                              BeneficiaryRepository beneficiaryRepository,
                              BuyerRepository buyerRepository,
                              VeterinarianRepository veterinarianRepository,
                              LivestockRepository livestockRepository,
                              LivestockBirthRepository livestockBirthRepository,
                              LivestockSickRepository livestockSickRepository,
                              LivestockBreedingRepository livestockBreedingRepository,
                              LivestockSaleRepository livestockSaleRepository,
                              LivestockDeathRepository livestockDeathRepository) {
        this.auditLogService = auditLogService;
        this.representativeRepository = representativeRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.buyerRepository = buyerRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.livestockRepository = livestockRepository;
        this.livestockBirthRepository = livestockBirthRepository;
        this.livestockSickRepository = livestockSickRepository;
        this.livestockBreedingRepository = livestockBreedingRepository;
        this.livestockSaleRepository = livestockSaleRepository;
        this.livestockDeathRepository = livestockDeathRepository;
    }

    @GetMapping({"", "/", "/list"})
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String entityType,
                       Model model) {

        boolean filtered = entityType != null && !entityType.isBlank();

        Page<AuditLog> result = filtered
                ? auditLogService.getByEntityTypePaged(entityType, page, size)
                : auditLogService.getAllPaged(page, size);

        model.addAttribute("logs",        result.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages",  result.getTotalPages());
        model.addAttribute("totalItems",  result.getTotalElements());
        model.addAttribute("pageSize",    size);
        model.addAttribute("entityType",  entityType);
        model.addAttribute("entityLabels", AuditLogService.ENTITY_LABELS);
        model.addAttribute("entityIcons",  AuditLogService.ENTITY_ICONS); // NEW

        // ── Live counts straight from the database ──────────────────────────
        // NOT derived from the audit trail (which keeps a row forever, even
        // after records are deleted). This is what's actually in each table
        // right now — now covering master data AND every livestock module,
        // so the top summary on every audit-log page shows both.
        Map<String, Long> liveCounts = getLiveCounts();
        model.addAttribute("liveCounts", liveCounts);

        if (filtered) {
            String label = auditLogService.getEntityLabel(entityType);

            model.addAttribute("filtered", true);
            model.addAttribute("filterLabel", label);

            model.addAttribute("grandTotal",  auditLogService.getCountByEntityType(entityType));
            model.addAttribute("createCount", auditLogService.getCountByEntityTypeAndAction(entityType, "CREATE"));
            model.addAttribute("updateCount", auditLogService.getCountByEntityTypeAndAction(entityType, "UPDATE"));
            model.addAttribute("deleteCount", auditLogService.getDeleteCountForEntityType(entityType));

            // Real current count for whichever module is selected — now resolves
            // for master-data types AND livestock types, since both live in liveCounts.
            model.addAttribute("liveCount", liveCounts.get(entityType));

        } else {
            model.addAttribute("filtered", false);

            model.addAttribute("grandTotal",  auditLogService.getTotalCount());
            model.addAttribute("createCount", auditLogService.getCountByAction("CREATE"));
            model.addAttribute("updateCount", auditLogService.getCountByAction("UPDATE"));
            model.addAttribute("deleteCount", auditLogService.getTotalDeleteCount());

            model.addAttribute("totalBreakdown",   auditLogService.getBreakdownByEntityType());
            model.addAttribute("createdBreakdown", auditLogService.getBreakdownByAction("CREATE"));
            model.addAttribute("updatedBreakdown", auditLogService.getBreakdownByAction("UPDATE"));
            model.addAttribute("deletedBreakdown", auditLogService.getDeleteBreakdown());
        }

        return "audit-log-list";
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public String entityHistory(@PathVariable String entityType,
                                @PathVariable UUID entityId,
                                Model model) {
        List<AuditLog> logs = auditLogService.getLogsForEntity(entityType, entityId);
        model.addAttribute("logs",       logs);
        model.addAttribute("entityType", entityType);
        model.addAttribute("entityId",   entityId);
        return "audit-log-entity";
    }

    // ── HELPER ────────────────────────────────────────────────────────────
    private Map<String, Long> getLiveCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();

        // Master data
        counts.put("representative", representativeRepository.count());
        counts.put("beneficiary",    beneficiaryRepository.count());
        counts.put("buyer",          buyerRepository.count());
        counts.put("veterinarian",   veterinarianRepository.countByIsDeletedFalse());

        // NEW — Livestock: Birth, Livestock, Sick, Breeding, Sales, Death
        // Using plain .count() here since soft-delete support varies per entity.
        // If any of these entities have an is_deleted flag like Veterinarian does,
        // swap .count() for that entity's countByIsDeletedFalse() the same way.
        counts.put("livestock_birth",    livestockBirthRepository.count());
        counts.put("livestock",          livestockRepository.count());
        counts.put("livestock_sick",     livestockSickRepository.count());
        counts.put("livestock_breeding", livestockBreedingRepository.count());
        counts.put("livestock_sale",     livestockSaleRepository.count());
        counts.put("livestock_death",    livestockDeathRepository.count());

        return counts;
    }
}