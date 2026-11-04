package rw.animalproduct.animal.production.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.BulkImportResult;
import rw.animalproduct.animal.production.services.BulkLivestockImportService;
import rw.animalproduct.animal.production.services.BulkTemplateService;
import rw.animalproduct.animal.production.services.UsersService;

import java.io.IOException;
import java.util.UUID;

/**
 * The single owner of the /livestock/bulk-import flow.
 *
 * <p>Two-phase import:
 * <ol>
 *   <li>POST /upload  → parse + validate → store preview in session → render preview page</li>
 *   <li>POST /confirm → read preview from session → persist → redirect to the livestock list</li>
 * </ol>
 */
@Controller
@RequestMapping("/livestock/bulk-import")
public class BulkLivestockImportController {

    private static final Logger log =
            LoggerFactory.getLogger(BulkLivestockImportController.class);

    /** Session attribute key for the preview result. */
    private static final String SESSION_PREVIEW_KEY = "BULK_IMPORT_PREVIEW";

    private final BulkLivestockImportService importService;
    private final BulkTemplateService        templateService;
    private final UsersService               usersService;

    public BulkLivestockImportController(BulkLivestockImportService importService,
                                         BulkTemplateService templateService,
                                         UsersService usersService) {
        this.importService   = importService;
        this.templateService = templateService;
        this.usersService    = usersService;
    }

    // ════════════════════════════════════════════════════════════════════════
    // LANDING — clears any stale preview so the page always starts fresh
    // ════════════════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @GetMapping
    public String landing(HttpSession session, Model model) {
        session.removeAttribute(SESSION_PREVIEW_KEY);
        model.addAttribute("pageTitle", "Bulk Animal Registration");
        return "livestock-bulk-import";
    }

    // ════════════════════════════════════════════════════════════════════════
    // TEMPLATE DOWNLOAD
    // ════════════════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] xlsx = templateService.generateTemplate();

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=livestock-bulk-template.xlsx");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
        response.getOutputStream().flush();
    }

    // ════════════════════════════════════════════════════════════════════════
    // UPLOAD + PREVIEW
    // ════════════════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @PostMapping("/upload")
    public String uploadAndPreview(@RequestParam("file") MultipartFile file,
                                   HttpSession session,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        BulkImportResult result = importService.parseAndValidate(file);

        if (result.hasFileError()) {
            redirectAttributes.addFlashAttribute("error", result.getFileError());
            return "redirect:/livestock/bulk-import";
        }

        session.setAttribute(SESSION_PREVIEW_KEY, result);

        model.addAttribute("preview", result);
        model.addAttribute("pageTitle", "Bulk Animal Registration");
        return "livestock-bulk-import";
    }

    // ════════════════════════════════════════════════════════════════════════
    // ACCEPT SUGGESTED TAG — user clicked the green "Use GOAT-XXX" pill
    // ════════════════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @PostMapping("/accept-suggestion")
    public String acceptSuggestion(@RequestParam("rowNumber") int rowNumber,
                                   HttpSession session,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        BulkImportResult preview =
                (BulkImportResult) session.getAttribute(SESSION_PREVIEW_KEY);

        if (preview == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Your preview has expired. Please upload the file again.");
            return "redirect:/livestock/bulk-import";
        }

        importService.acceptSuggestion(preview, rowNumber);
        session.setAttribute(SESSION_PREVIEW_KEY, preview);

        log.info("[bulk-import] accepted suggestion for row {}", rowNumber);

        model.addAttribute("preview", preview);
        model.addAttribute("pageTitle", "Bulk Animal Registration");
        return "livestock-bulk-import";
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONFIRM — pulls the preview from the session and persists it
    // ════════════════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @PostMapping("/confirm")
    public String confirmImport(HttpSession session,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {

        BulkImportResult preview =
                (BulkImportResult) session.getAttribute(SESSION_PREVIEW_KEY);

        if (preview == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Your preview has expired. Please upload the file again.");
            return "redirect:/livestock/bulk-import";
        }

        try {
            UUID userId = resolveUserId(auth);

            BulkImportResult finalResult = importService.importValidatedRows(
                    preview,
                    userId,
                    preview.getFileName(),
                    preview.getFileSizeBytes());

            session.removeAttribute(SESSION_PREVIEW_KEY);

            if (finalResult.hasFileError()) {
                redirectAttributes.addFlashAttribute("error",
                        finalResult.getFileError());
                return "redirect:/livestock/bulk-import";
            }

            redirectAttributes.addFlashAttribute("success",
                    "Imported " + finalResult.getCreatedCount()
                            + " animal(s) successfully.");
            return "redirect:/livestock/list";

        } catch (Exception e) {
            log.error("[bulk-import] confirm failed", e);
            redirectAttributes.addFlashAttribute("error",
                    "Import failed: " + e.getMessage());
            return "redirect:/livestock/bulk-import";
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // CANCEL
    // ════════════════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @PostMapping("/cancel")
    public String cancel(HttpSession session) {
        session.removeAttribute(SESSION_PREVIEW_KEY);
        return "redirect:/livestock/bulk-import";
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    private UUID resolveUserId(Authentication auth) {
        if (auth == null) return null;
        try {
            return usersService.getUserByEmail(auth.getName())
                    .map(u -> u.getUserId())
                    .orElse(null);
        } catch (Exception e) {
            log.debug("[bulk-import] could not resolve user id for '{}': {}",
                    auth.getName(), e.getMessage());
            return null;
        }
    }
}