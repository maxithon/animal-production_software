package rw.animalproduct.animal.production.services;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import rw.animalproduct.animal.production.dto.BulkImportResult;
import rw.animalproduct.animal.production.dto.BulkImportRow;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class BulkLivestockImportService {

    private static final Logger log = LoggerFactory.getLogger(BulkLivestockImportService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Africa/Kigali");

    private static final int HEADER_SCAN_LIMIT = 5;
    private static final int MIN_HEADERS_TO_ACCEPT = 2;

    private static final List<String> DATE_PATTERNS = List.of(
            "yyyy-MM-dd", "yyyy/MM/dd", "dd.MM.yyyy", "d.M.yyyy",
            "dd-MMM-yyyy", "d-MMM-yyyy", "MMM d, yyyy", "MMM dd, yyyy",
            "dd-MM-yyyy", "d-M-yyyy", "dd/MM/yyyy", "d/M/yyyy",
            "MM/dd/yyyy", "M/d/yyyy"
    );

    private static final int MAX_AGE_YEARS = 30;

    private static final boolean LOCATION_REQUIRED    = false;
    private static final boolean BENEFICIARY_REQUIRED = false;

    private final LivestockRepository              livestockRepository;
    private final LivestockCategoryRepository      categoryRepository;
    private final LocationRepository               locationRepository;
    private final BeneficiaryRepository            beneficiaryRepository;
    private final LivestockImportBatchRepository   importBatchRepository;
    private final TagAllocationService             tagAllocationService;
    private final LifecycleEmailService            emailService;   // ✅ NEW

    public BulkLivestockImportService(LivestockRepository livestockRepository,
                                      LivestockCategoryRepository categoryRepository,
                                      LocationRepository locationRepository,
                                      BeneficiaryRepository beneficiaryRepository,
                                      LivestockImportBatchRepository importBatchRepository,
                                      TagAllocationService tagAllocationService,
                                      LifecycleEmailService emailService) {   // ✅ NEW
        this.livestockRepository   = livestockRepository;
        this.categoryRepository    = categoryRepository;
        this.locationRepository    = locationRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.importBatchRepository = importBatchRepository;
        this.tagAllocationService  = tagAllocationService;
        this.emailService          = emailService;   // ✅ NEW
    }

    // ════════════════════════════════════════════════════════════════════════
    // PARSE + VALIDATE
    // ════════════════════════════════════════════════════════════════════════

    public BulkImportResult parseAndValidate(MultipartFile file) {
        BulkImportResult result = new BulkImportResult();

        if (file == null || file.isEmpty()) {
            result.setFileError("Please upload an Excel file (.xlsx).");
            return result;
        }
        result.setFileName(file.getOriginalFilename());
        result.setFileSizeBytes(file.getSize());

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {

            Sheet sheet = pickBestSheet(wb);
            if (sheet == null || sheet.getLastRowNum() < 0) {
                result.setFileError("The uploaded file has no sheets with data.");
                return result;
            }

            int headerRowIdx = -1;
            Map<String, Integer> cols = new HashMap<>();
            int bestScore = 0;

            int scanLimit = Math.min(HEADER_SCAN_LIMIT - 1, sheet.getLastRowNum());
            for (int r = 0; r <= scanLimit; r++) {
                Row candidate = sheet.getRow(r);
                if (candidate == null) continue;

                Map<String, Integer> candidateCols = mapHeaderColumns(candidate);
                int score = 0;
                for (String k : candidateCols.keySet()) {
                    if (!k.startsWith("__")) score++;
                }

                if (score > bestScore) {
                    bestScore = score;
                    headerRowIdx = r;
                    cols = candidateCols;
                }
            }

            log.info("[bulk-import] sheet '{}' — best header row {} with score {}; cols={}",
                    sheet.getSheetName(), headerRowIdx, bestScore, cols);

            if (headerRowIdx < 0 || bestScore < MIN_HEADERS_TO_ACCEPT) {
                result.setFileError(
                        "Could not find a header row in the first "
                                + HEADER_SCAN_LIMIT + " rows of sheet '"
                                + sheet.getSheetName() + "'. Please download the template at "
                                + "/livestock/bulk-import/template and use its exact format.");
                return result;
            }

            List<String> required = Arrays.asList("species", "gender", "birthDate");
            List<String> missing = new ArrayList<>();
            for (String r : required) {
                if (!cols.containsKey(r)) missing.add(r);
            }

            if (!missing.isEmpty()) {
                result.setFileError(
                        "Missing required column(s): " + String.join(", ", missing)
                                + ". Headers detected in your file: " + cols.keySet()
                                + ". Download the template at /livestock/bulk-import/template "
                                + "for the exact format.");
                return result;
            }

            Set<String> existingTags = new HashSet<>();
            for (Livestock a : livestockRepository.findAll()) {
                if (Boolean.TRUE.equals(a.getIsDeleted())) continue;
                String t = a.getTagNumber();
                if (t != null) existingTags.add(t.trim());
            }

            List<LivestockCategory> allCategories = categoryRepository.findAll();
            Map<String, LivestockCategory> byCode = new HashMap<>();
            Map<String, LivestockCategory> byName = new HashMap<>();
            for (LivestockCategory c : allCategories) {
                if (Boolean.TRUE.equals(c.getIsDeleted())) continue;
                if (c.getCode() != null) byCode.putIfAbsent(c.getCode().toUpperCase(Locale.ROOT), c);
                if (c.getName() != null) byName.putIfAbsent(c.getName().toUpperCase(Locale.ROOT), c);
            }

            Set<String> existingNids = new HashSet<>();
            for (Beneficiary b : beneficiaryRepository.findAll()) {
                if (b.getNid() != null) existingNids.add(b.getNid());
            }

            Set<String> existingLocationNames = new HashSet<>();
            for (Location loc : locationRepository.findAll()) {
                if (loc.getName() != null) {
                    existingLocationNames.add(loc.getName().toLowerCase(Locale.ROOT));
                }
            }

            Set<String> seenInFile       = new HashSet<>();
            Set<String> suggestedThisRun = new HashSet<>();

            int rowIndex = headerRowIdx + 1;
            int parsedCount = 0;
            int skippedBlank = 0;

            while (rowIndex <= sheet.getLastRowNum()) {
                Row row = sheet.getRow(rowIndex);
                int currentRowNumber = rowIndex + 1;
                rowIndex++;

                if (row == null || isBlank(row)) {
                    skippedBlank++;
                    continue;
                }

                BulkImportRow parsed = new BulkImportRow();
                parsed.setRowNumber(currentRowNumber);

                try {
                    parsed.setTagNumber(readString(row, cols.get("tagNumber")));
                    parsed.setSpecies(readString(row, cols.get("species")));
                    parsed.setGender(readString(row, cols.get("gender")));
                    parsed.setBirthDate(readDate(row, cols.get("birthDate")));
                    parsed.setAcquisitionMethod(readString(row, cols.get("acquisitionMethod")));
                    parsed.setHealthStatus(readString(row, cols.get("healthStatus")));
                    parsed.setReproductiveStatus(readString(row, cols.get("reproductiveStatus")));
                    parsed.setLocationName(readString(row, cols.get("locationName")));
                    parsed.setBeneficiaryNid(readString(row, cols.get("beneficiaryNid")));
                    parsed.setNotes(readString(row, cols.get("notes")));
                    parsed.setPhotoUrl(readString(row, cols.get("photoUrl")));
                    parsed.setQuantity(readInt(row, cols.get("quantity")));

                    String groupFlag = readString(row, cols.get("isGroup"));
                    parsed.setIsGroup(
                            "yes".equalsIgnoreCase(groupFlag)
                                    || "true".equalsIgnoreCase(groupFlag)
                                    || "1".equals(groupFlag)
                                    || (parsed.getQuantity() != null && parsed.getQuantity() > 1));

                    validateRow(parsed, existingTags, seenInFile,
                            byCode, byName, existingNids, existingLocationNames,
                            suggestedThisRun);
                } catch (Exception e) {
                    parsed.setStatus("ERROR");
                    parsed.setMessage("Row parse error: " + e.getMessage());
                }

                result.getRows().add(parsed);
                parsedCount++;

                String st = parsed.getStatus() == null ? "ERROR" : parsed.getStatus();
                if ("OK".equals(st))             result.setOkRows(result.getOkRows() + 1);
                else if ("WARNING".equals(st))   result.setWarningRows(result.getWarningRows() + 1);
                else                              result.setErrorRows(result.getErrorRows() + 1);
            }

            log.info("[bulk-import] parsed {} data rows ({} blank rows skipped)",
                    parsedCount, skippedBlank);
            result.setTotalRows(result.getRows().size());
            return result;

        } catch (IOException e) {
            log.warn("[bulk-import] could not read file: {}", e.getMessage());
            result.setFileError("Could not read the file: " + e.getMessage());
            return result;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ROW VALIDATION
    // ════════════════════════════════════════════════════════════════════════

    private void validateRow(BulkImportRow r,
                             Set<String> existingTags,
                             Set<String> seenInFile,
                             Map<String, LivestockCategory> byCode,
                             Map<String, LivestockCategory> byName,
                             Set<String> existingNids,
                             Set<String> existingLocationNames,
                             Set<String> suggestedThisRun) {

        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        LivestockCategory resolvedCategory = null;
        if (r.getSpecies() != null && !r.getSpecies().isBlank()) {
            String key = r.getSpecies().trim().toUpperCase(Locale.ROOT);
            resolvedCategory = byCode.get(key);
            if (resolvedCategory == null) resolvedCategory = byName.get(key);
        }

        boolean speciesValid = false;
        if (r.getSpecies() == null || r.getSpecies().isBlank()) {
            problems.add("Species is missing");
        } else if (resolvedCategory == null) {
            problems.add("Unknown species '" + r.getSpecies()
                    + "'. Valid values: " + String.join(", ", byCode.keySet()));
        } else {
            speciesValid = true;
        }

        if (r.getTagNumber() != null && !r.getTagNumber().isBlank()) {
            String tag = r.getTagNumber().trim();

            if (r.isSuggestionAccepted()) {
                // already settled
            }
            else if (existingTags.contains(tag)) {
                String suggestion = suggestUniqueTagFor(
                        resolvedCategory, tag, existingTags, suggestedThisRun);
                if (suggestion != null) {
                    r.setSuggestedTag(suggestion);
                    suggestedThisRun.add(suggestion);
                    problems.add("Tag '" + tag + "' already exists — suggested: " + suggestion);
                } else {
                    problems.add("Tag '" + tag + "' already exists");
                }
            }
            else if (!seenInFile.add(tag)) {
                String suggestion = suggestUniqueTagFor(
                        resolvedCategory, tag, existingTags, suggestedThisRun);
                if (suggestion != null) {
                    r.setSuggestedTag(suggestion);
                    suggestedThisRun.add(suggestion);
                    problems.add("Tag '" + tag
                            + "' appears more than once in the file — suggested: " + suggestion);
                } else {
                    problems.add("Tag '" + tag + "' appears more than once in the file");
                }
            }
        } else if (!speciesValid) {
            problems.add("Tag number is missing and Species is invalid — cannot auto-assign a tag");
        } else {
            warnings.add("No tag number — one will be auto-assigned from the Species prefix");
        }

        if (r.getGender() == null || r.getGender().isBlank()) {
            problems.add("Gender is missing");
        } else {
            String g = r.getGender().trim().toUpperCase(Locale.ROOT);
            if (!g.equals("MALE") && !g.equals("FEMALE") && !g.equals("MIXED")) {
                problems.add("Gender must be MALE, FEMALE, or MIXED");
            } else if (r.isGroup() && !g.equals("MIXED")) {
                warnings.add("Batch row with gender '" + g + "' — did you mean MIXED?");
            }
        }

        if (r.getBirthDate() == null) {
            problems.add("Birth date is required — without it, the animal cannot be bred.");
        } else if (r.getBirthDate().isAfter(LocalDate.now(APP_ZONE))) {
            problems.add("Birth date is in the future");
        } else if (r.getBirthDate().isBefore(LocalDate.now(APP_ZONE).minusYears(MAX_AGE_YEARS))) {
            problems.add("Birth date is more than " + MAX_AGE_YEARS + " years ago — likely a typo");
        }

        if (r.isGroup()) {
            if (r.getQuantity() == null || r.getQuantity() <= 0) {
                problems.add("Batch row must have a quantity greater than 0");
            }
        } else if (r.getQuantity() != null && r.getQuantity() > 1) {
            warnings.add("Quantity is " + r.getQuantity()
                    + " but is_group is not set — will store as batch");
        }

        if (r.getBeneficiaryNid() == null || r.getBeneficiaryNid().isBlank()) {
            if (BENEFICIARY_REQUIRED) problems.add("Beneficiary NID is required");
            else                       warnings.add("No beneficiary NID — the animal will be imported unassigned");
        } else if (!existingNids.contains(r.getBeneficiaryNid().trim())) {
            if (BENEFICIARY_REQUIRED) problems.add("Beneficiary NID '" + r.getBeneficiaryNid() + "' not found");
            else                       warnings.add("Beneficiary NID '" + r.getBeneficiaryNid() + "' not found — will be ignored");
        }

        if (r.getLocationName() == null || r.getLocationName().isBlank()) {
            if (LOCATION_REQUIRED) problems.add("Location is required");
            else                    warnings.add("No location — the animal will be imported without a location");
        } else if (!existingLocationNames.contains(
                r.getLocationName().trim().toLowerCase(Locale.ROOT))) {
            if (LOCATION_REQUIRED) problems.add("Location '" + r.getLocationName() + "' not found");
            else                    warnings.add("Location '" + r.getLocationName() + "' not found — will be ignored");
        }

        if (r.getPhotoUrl() != null && !r.getPhotoUrl().isBlank()
                && !r.getPhotoUrl().startsWith("/") && !r.getPhotoUrl().startsWith("http")) {
            warnings.add("Photo URL '" + r.getPhotoUrl()
                    + "' doesn't look like a URL or path — will be stored as-is");
        }

        if (r.getNotes() != null
                && r.getNotes().toLowerCase(Locale.ROOT).contains("pregnant")
                && "FEMALE".equalsIgnoreCase(r.getGender())) {
            warnings.add("Note mentions 'pregnant' — pregnancy must be recorded "
                    + "on the Breeding page after import, not here.");
        }

        r.setStatus(problems.isEmpty() ? (warnings.isEmpty() ? "OK" : "WARNING") : "ERROR");
        if (!problems.isEmpty())      r.setMessage(String.join("; ", problems));
        else if (!warnings.isEmpty()) r.setMessage(String.join("; ", warnings));
    }

    private String suggestUniqueTagFor(LivestockCategory category,
                                       String conflictingTag,
                                       Set<String> existingTags,
                                       Set<String> suggestedThisRun) {
        if (category != null) {
            try {
                String candidate = tagAllocationService.nextTagFor(category);
                int attempts = 0;
                while (candidate != null
                        && (existingTags.contains(candidate) || suggestedThisRun.contains(candidate))
                        && attempts++ < 10_000) {
                    candidate = incrementTag(candidate, category);
                    if (candidate == null) break;
                }
                return candidate;
            } catch (Exception e) {
                log.warn("[bulk-import] TagAllocationService failed: {}", e.getMessage());
            }
        }

        if (conflictingTag != null) {
            int dash = conflictingTag.lastIndexOf('-');
            if (dash > 0) {
                String prefix = conflictingTag.substring(0, dash).trim().toUpperCase(Locale.ROOT);
                LivestockCategory cat = null;
                for (LivestockCategory c : categoryRepository.findAll()) {
                    if (c.getCode() != null && prefix.equalsIgnoreCase(c.getCode().trim())) {
                        cat = c; break;
                    }
                }
                if (cat != null) {
                    try {
                        String candidate = tagAllocationService.nextTagFor(cat);
                        int attempts = 0;
                        while (candidate != null
                                && (existingTags.contains(candidate) || suggestedThisRun.contains(candidate))
                                && attempts++ < 10_000) {
                            candidate = incrementTag(candidate, cat);
                            if (candidate == null) break;
                        }
                        return candidate;
                    } catch (Exception ignore) { }
                }
            }
        }
        return null;
    }

    private String incrementTag(String current, LivestockCategory category) {
        if (current == null) return null;
        int dash = current.lastIndexOf('-');
        if (dash <= 0) return null;
        String prefix = current.substring(0, dash);
        String numPart = current.substring(dash + 1);
        try {
            int n = Integer.parseInt(numPart);
            return String.format("%s-%03d", prefix, n + 1);
        } catch (NumberFormatException e) {
            return tagAllocationService.nextTagFor(category);
        }
    }

    public void acceptSuggestion(BulkImportResult preview, int rowNumber) {
        for (BulkImportRow r : preview.getRows()) {
            if (r.getRowNumber() == rowNumber) {
                if (r.hasSuggestedTag()) {
                    r.setTagNumber(r.getSuggestedTag());
                    r.setSuggestionAccepted(true);
                    r.setStatus("OK");
                    r.setMessage("Tag updated to " + r.getTagNumber());
                }
                break;
            }
        }
        recomputeCounters(preview);
    }

    private void recomputeCounters(BulkImportResult preview) {
        int ok = 0, warn = 0, err = 0;
        for (BulkImportRow r : preview.getRows()) {
            String st = r.getStatus() == null ? "ERROR" : r.getStatus();
            if ("OK".equals(st))           ok++;
            else if ("WARNING".equals(st)) warn++;
            else                            err++;
        }
        preview.setOkRows(ok);
        preview.setWarningRows(warn);
        preview.setErrorRows(err);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONFIRM IMPORT — now sends the summary email
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public BulkImportResult importValidatedRows(BulkImportResult preview,
                                                UUID importedBy,
                                                String fileName,
                                                long fileSizeBytes) {

        BulkImportResult result = new BulkImportResult();
        result.setFileName(fileName);
        result.setFileSizeBytes(fileSizeBytes);
        result.setRows(preview.getRows());

        List<BulkImportRow> rows = preview.getRows();
        if (rows == null || rows.isEmpty()) {
            result.setFileError("No rows to import.");
            return result;
        }

        List<LivestockCategory> allCategories = categoryRepository.findAll();
        Map<String, LivestockCategory> byCode = new HashMap<>();
        Map<String, LivestockCategory> byName = new HashMap<>();
        for (LivestockCategory c : allCategories) {
            if (Boolean.TRUE.equals(c.getIsDeleted())) continue;
            if (c.getCode() != null) byCode.putIfAbsent(c.getCode().toUpperCase(Locale.ROOT), c);
            if (c.getName() != null) byName.putIfAbsent(c.getName().toUpperCase(Locale.ROOT), c);
        }

        Map<String, Location> locationsByName = new HashMap<>();
        for (Location loc : locationRepository.findAll()) {
            if (loc.getName() != null) {
                locationsByName.putIfAbsent(loc.getName().toLowerCase(Locale.ROOT), loc);
            }
        }

        Map<String, Beneficiary> beneficiariesByNid = new HashMap<>();
        for (Beneficiary b : beneficiaryRepository.findAll()) {
            if (b.getNid() != null) beneficiariesByNid.putIfAbsent(b.getNid(), b);
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        int created = 0;
        List<String> createdTags = new ArrayList<>();
        Set<String> allocatedInThisRun = new HashSet<>();

        for (BulkImportRow r : rows) {
            if (!r.isImportable()) continue;

            LivestockCategory category = null;
            if (r.getSpecies() != null) {
                String key = r.getSpecies().trim().toUpperCase(Locale.ROOT);
                category = byCode.get(key);
                if (category == null) category = byName.get(key);
            }

            String tag;
            if (r.getTagNumber() != null && !r.getTagNumber().isBlank()) {
                tag = r.getTagNumber().trim();
            } else {
                tag = tagAllocationService.nextTagFor(category, allocatedInThisRun);
            }

            boolean tagTaken = false;
            if (tag != null) {
                for (Livestock other : livestockRepository.findAll()) {
                    if (Boolean.TRUE.equals(other.getIsDeleted())) continue;
                    String ot = other.getTagNumber();
                    if (ot != null && tag.equalsIgnoreCase(ot.trim())) {
                        tagTaken = true;
                        break;
                    }
                }
            }

            if (tag == null || allocatedInThisRun.contains(tag) || tagTaken) {
                log.warn("[bulk-import] skipping row {} — no unique tag available",
                        r.getRowNumber());
                continue;
            }
            allocatedInThisRun.add(tag);

            Livestock l = new Livestock();
            l.setTagNumber(tag);
            l.setGender(r.getGender() == null
                    ? "MIXED"
                    : r.getGender().trim().toUpperCase(Locale.ROOT));
            l.setBirthDate(r.getBirthDate());
            l.setDateReceived(r.getBirthDate() != null ? r.getBirthDate() : today);
            l.setAcquisitionMethod(normalizeAcquisition(r.getAcquisitionMethod()));
            l.setHealthStatus(normalizeHealth(r.getHealthStatus()));
            l.setReproductiveStatus(normalizeRepro(r.getReproductiveStatus()));
            l.setStatus(Livestock.STATUS_ACTIVE);
            l.setIsDraft(false);
            l.setIsDeleted(false);
            l.setCreatedBy(importedBy);
            l.setCreatedAt(LocalDateTime.now());

            if (category != null) l.setLivestockCategory(category);

            if (r.getLocationName() != null && !r.getLocationName().isBlank()) {
                Location loc = locationsByName.get(
                        r.getLocationName().trim().toLowerCase(Locale.ROOT));
                if (loc != null) l.setLocation(loc);
            }

            if (r.getBeneficiaryNid() != null && !r.getBeneficiaryNid().isBlank()) {
                Beneficiary b = beneficiariesByNid.get(r.getBeneficiaryNid().trim());
                if (b != null) l.setBeneficiary(b);
            }

            if (r.getPhotoUrl() != null && !r.getPhotoUrl().isBlank()) {
                l.setPhoto(r.getPhotoUrl().trim());
            }

            l.setIsGroup(r.isGroup());
            l.setQuantity(r.isGroup() ? r.getQuantity() : 1);

            if (r.getNotes() != null && !r.getNotes().isBlank())
                l.setNotes(r.getNotes().trim());

            livestockRepository.save(l);
            created++;
            createdTags.add(l.getTagNumber());
        }

        LivestockImportBatch batch = new LivestockImportBatch();
        batch.setImportedBy(importedBy);
        batch.setFileName(fileName);
        batch.setFileSizeBytes(fileSizeBytes);
        batch.setTotalRows(preview.getTotalRows());
        batch.setOkRows(preview.getOkRows());
        batch.setWarningRows(preview.getWarningRows());
        batch.setErrorRows(preview.getErrorRows());
        batch.setCreatedCount(created);
        batch.setCreatedTags(createdTags.size() > 500
                ? String.join(",", createdTags.subList(0, 500))
                + " … +" + (createdTags.size() - 500) + " more"
                : String.join(",", createdTags));
        batch.setStatus(LivestockImportBatch.STATUS_COMPLETED);
        LivestockImportBatch saved = importBatchRepository.save(batch);

        result.setCreatedCount(created);
        result.setCreatedTags(createdTags);
        result.setImportBatchId(saved.getId());
        result.setImportBatchTimestamp(saved.getImportedAt().toString());
        result.setTotalRows(preview.getTotalRows());
        result.setOkRows(preview.getOkRows());
        result.setWarningRows(preview.getWarningRows());
        result.setErrorRows(preview.getErrorRows());

        log.info("[bulk-import] created {} livestock records (batch {})",
                created, saved.getId());

        // ✅ NEW: send the summary email. Wrapped in try/catch so a mail
        // outage never rolls back a successful import.
        try {
            String uploadedBy = (importedBy != null) ? importedBy.toString() : "system";
            emailService.sendBulkImportCompletedNotification(
                    created,
                    preview.getTotalRows(),
                    preview.getOkRows(),
                    preview.getWarningRows(),
                    preview.getErrorRows(),
                    fileName,
                    uploadedBy,
                    createdTags);
            log.info("[bulk-import] 📧 summary email queued ({} animal(s))", created);
        } catch (Exception mailEx) {
            log.error("[bulk-import] ❌ failed to queue summary email: {}",
                    mailEx.getMessage(), mailEx);
        }

        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EXCEL HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private Sheet pickBestSheet(Workbook wb) {
        if (wb == null || wb.getNumberOfSheets() == 0) return null;
        Sheet named = wb.getSheet("Livestock");
        if (named != null) return named;

        Sheet best = null;
        int bestFilled = -1;
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            int filled = 0;
            int limit = Math.min(HEADER_SCAN_LIMIT - 1, s.getLastRowNum());
            for (int r = 0; r <= limit; r++) {
                Row row = s.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && !stringCell(cell).isBlank()) filled++;
                }
            }
            if (filled > bestFilled) {
                bestFilled = filled;
                best = s;
            }
        }
        return best;
    }

    private Map<String, Integer> mapHeaderColumns(Row header) {
        Map<String, Integer> cols = new HashMap<>();
        if (header == null) return cols;
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell c = header.getCell(i);
            if (c == null) continue;
            String name = stringCell(c)
                    .replace('\u00A0', ' ')
                    .replace('\u200B', ' ')
                    .replace('\uFEFF', ' ')
                    .trim();
            if (name.isEmpty()) continue;
            cols.put(normalizeHeader(name), i);
        }
        return cols;
    }

    private String normalizeHeader(String raw) {
        String cleaned = raw.replace('\u00A0', ' ')
                .replace('\u200B', ' ')
                .replace('\uFEFF', ' ')
                .replace("*", "")
                .replaceAll("\\(.*?\\)", "")
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .trim();

        switch (cleaned) {
            case "tagnumber": case "tag": case "animaltag": case "animalid": return "tagNumber";
            case "species": case "category": case "livestockcategory":
            case "animaltype": case "type": return "species";
            case "gender": case "sex": return "gender";
            case "breed": return "__deprecated_breed__";
            case "birthdate": case "dob": case "dateofbirth":
            case "bornon": case "dateborn": return "birthDate";
            case "acquisitionmethod": case "acquisition":
            case "source": case "howacquired": return "acquisitionMethod";
            case "healthstatus": case "health": case "condition": return "healthStatus";
            case "reproductivestatus": case "repro": case "breedingstatus":
            case "reproductivestate": return "reproductiveStatus";
            case "locationname": case "location": case "village":
            case "farm": case "site": return "locationName";
            case "beneficiarynid": case "nid": case "beneficiary":
            case "ownernid": case "ownernationalid": return "beneficiaryNid";
            case "notes": case "note": case "comment": case "remarks":
            case "description": return "notes";
            case "photourl": case "photo": case "image": case "imageurl":
            case "picture": return "photoUrl";
            case "isgroup": case "group": case "batch": case "isbatch":
            case "isgroupanimal": return "isGroup";
            case "quantity": case "count": case "number": case "qty":
            case "amount": return "quantity";
            default: return cleaned;
        }
    }

    private String readString(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        String s = stringCell(c).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer readInt(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
            if (c.getCellType() == CellType.FORMULA) {
                CellValue cv = c.getSheet().getWorkbook()
                        .getCreationHelper().createFormulaEvaluator().evaluate(c);
                if (cv != null && cv.getCellType() == CellType.NUMERIC)
                    return (int) cv.getNumberValue();
            }
            String s = stringCell(c).trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s.replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate readDate(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;

        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c))
            return c.getLocalDateTimeCellValue().toLocalDate();

        if (c.getCellType() == CellType.FORMULA) {
            try {
                CellValue cv = c.getSheet().getWorkbook()
                        .getCreationHelper().createFormulaEvaluator().evaluate(c);
                if (cv != null && cv.getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(c)) {
                    return DateUtil.getLocalDateTime(cv.getNumberValue()).toLocalDate();
                }
            } catch (Exception ignore) { }
        }

        String s = stringCell(c).trim();
        if (s.isEmpty()) return null;

        for (String pat : DATE_PATTERNS) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(pat, Locale.ENGLISH));
            } catch (DateTimeParseException ignore) { }
        }
        return null;
    }

    private String stringCell(Cell c) {
        if (c == null) return "";
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        CellType type = c.getCellType();
        if (type == CellType.STRING) return c.getStringCellValue();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            return fmt.formatCellValue(c);
        }
        if (type == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
        if (type == CellType.FORMULA) {
            try {
                FormulaEvaluator ev = c.getSheet().getWorkbook()
                        .getCreationHelper().createFormulaEvaluator();
                return fmt.formatCellValue(c, ev);
            } catch (Exception e) {
                return c.getCellFormula();
            }
        }
        return "";
    }

    private boolean isBlank(Row row) {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c != null && !stringCell(c).isBlank()) return false;
        }
        return true;
    }

    private String normalizeAcquisition(String v) {
        if (v == null || v.isBlank()) return "PURCHASE";
        String s = v.trim().toUpperCase(Locale.ROOT);
        if ("PURCHASE".equals(s) || "BIRTH".equals(s)
                || "DONATION".equals(s) || "TRANSFER".equals(s)) return s;
        if ("BOUGHT".equals(s) || "BUY".equals(s)) return "PURCHASE";
        if ("GIFT".equals(s) || "GIVEN".equals(s)) return "DONATION";
        return "PURCHASE";
    }

    private String normalizeHealth(String v) {
        if (v == null || v.isBlank()) return "HEALTHY";
        String s = v.trim().toUpperCase(Locale.ROOT);
        if ("HEALTHY".equals(s) || "SICK".equals(s) || "UNDER_TREATMENT".equals(s)) return s;
        if ("RECOVERING".equals(s) || "GOOD".equals(s)
                || "OK".equals(s) || "FINE".equals(s)) return "HEALTHY";
        return "HEALTHY";
    }

    private String normalizeRepro(String v) {
        if (v == null || v.isBlank()) return "OPEN";
        String s = v.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
        if ("OPEN".equals(s) || "PREGNANT".equals(s)
                || "READY_TO_BREED".equals(s) || "NOT_APPLICABLE".equals(s)
                || "BRED".equals(s)) return s;
        if ("READY".equals(s) || "READYTOBREED".equals(s)) return "READY_TO_BREED";
        return "OPEN";
    }
}