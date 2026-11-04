package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.entity.AuditLog;
import rw.animalproduct.animal.production.repository.*;
import rw.animalproduct.animal.production.services.AuditLogService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    private final RepresentativeRepository representativeRepository;
    private final BeneficiaryRepository    beneficiaryRepository;
    private final BuyerRepository          buyerRepository;
    private final VeterinarianRepository   veterinarianRepository;

    private final LivestockRepository        livestockRepository;
    private final LivestockBirthRepository   livestockBirthRepository;
    private final LivestockSickRepository    livestockSickRepository;
    private final LivestockBreedingRepository livestockBreedingRepository;
    private final LivestockSaleRepository    livestockSaleRepository;
    private final LivestockDeathRepository   livestockDeathRepository;

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

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        boolean filtered = entityType != null && !entityType.isBlank();

        Page<AuditLog> result = filtered
                ? auditLogService.getByEntityTypePaged(entityType, safePage, safeSize)
                : auditLogService.getAllPaged(safePage, safeSize);

        model.addAttribute("logs",         result.getContent());
        model.addAttribute("currentPage",  safePage);
        model.addAttribute("totalPages",   result.getTotalPages());
        model.addAttribute("totalItems",   result.getTotalElements());
        model.addAttribute("pageSize",     safeSize);
        model.addAttribute("entityType",   entityType);
        model.addAttribute("entityLabels", AuditLogService.ENTITY_LABELS);
        model.addAttribute("entityIcons",  AuditLogService.ENTITY_ICONS);

        Map<String, Long> liveCounts = getLiveCounts();
        model.addAttribute("liveCounts", liveCounts);

        if (filtered) {
            model.addAttribute("filtered",    true);
            model.addAttribute("filterLabel", auditLogService.getEntityLabel(entityType));
            model.addAttribute("grandTotal",  auditLogService.getCountByEntityType(entityType));
            model.addAttribute("createCount", auditLogService.getCountByEntityTypeAndAction(entityType, AuditLog.ACTION_CREATE));
            model.addAttribute("updateCount", auditLogService.getCountByEntityTypeAndAction(entityType, AuditLog.ACTION_UPDATE));
            model.addAttribute("deleteCount", auditLogService.getDeleteCountForEntityType(entityType));
            model.addAttribute("liveCount",   liveCounts.get(entityType));
        } else {
            model.addAttribute("filtered",    false);
            model.addAttribute("grandTotal",  auditLogService.getTotalCount());
            model.addAttribute("createCount", auditLogService.getCountByAction(AuditLog.ACTION_CREATE));
            model.addAttribute("updateCount", auditLogService.getCountByAction(AuditLog.ACTION_UPDATE));
            model.addAttribute("deleteCount", auditLogService.getTotalDeleteCount());
            model.addAttribute("totalBreakdown",   auditLogService.getBreakdownByEntityType());
            model.addAttribute("createdBreakdown", auditLogService.getBreakdownByAction(AuditLog.ACTION_CREATE));
            model.addAttribute("updatedBreakdown", auditLogService.getBreakdownByAction(AuditLog.ACTION_UPDATE));
            model.addAttribute("deletedBreakdown", auditLogService.getDeleteBreakdown());
        }

        return "audit-log-list";
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public String entityHistory(@PathVariable String entityType,
                                @PathVariable UUID entityId,
                                Model model) {
        List<AuditLog> logs = auditLogService.getLogsForEntity(entityType, entityId);
        model.addAttribute("logs",        logs);
        model.addAttribute("entityType",  entityType);
        model.addAttribute("entityId",    entityId);
        model.addAttribute("entityLabel", auditLogService.getEntityLabel(entityType));
        model.addAttribute("entityIcon",  auditLogService.getEntityIcon(entityType));
        return "audit-log-entity";
    }

    private Map<String, Long> getLiveCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("representative",     representativeRepository.count());
        counts.put("beneficiary",        beneficiaryRepository.count());
        counts.put("buyer",              buyerRepository.count());
        counts.put("veterinarian",       veterinarianRepository.countByIsDeletedFalse());
        counts.put("livestock_birth",    livestockBirthRepository.count());
        counts.put("livestock",          livestockRepository.count());
        counts.put("livestock_sick",     livestockSickRepository.count());
        counts.put("livestock_breeding", livestockBreedingRepository.count());
        counts.put("livestock_sale",     livestockSaleRepository.count());
        counts.put("livestock_death",    livestockDeathRepository.count());
        return counts;
    }
}