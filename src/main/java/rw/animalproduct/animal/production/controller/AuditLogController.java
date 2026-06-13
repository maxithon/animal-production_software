package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.entity.AuditLog;
import rw.animalproduct.animal.production.services.AuditLogService;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping({"", "/", "/list"})
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String entityType,
                       Model model) {

        Page<AuditLog> result = (entityType != null && !entityType.isBlank())
                ? auditLogService.getByEntityTypePaged(entityType, page, size)
                : auditLogService.getAllPaged(page, size);

        model.addAttribute("logs",        result.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages",  result.getTotalPages());
        model.addAttribute("totalItems",  result.getTotalElements());
        model.addAttribute("pageSize",    size);
        model.addAttribute("entityType",  entityType);
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
}