package rw.animalproduct.animal.production.services;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates the bulk-import .xlsx template with:
 *  - a "Livestock" sheet with headers + 2 example rows + dropdowns
 *  - a "Reference" sheet containing valid species, locations, and NIDs
 *
 * The dropdowns prevent the #1 cause of import failures: typing a location
 * that doesn't exist in the DB.
 */
@Service
public class BulkTemplateService {

    private final LivestockCategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final BeneficiaryRepository beneficiaryRepository;

    public BulkTemplateService(LivestockCategoryRepository categoryRepository,
                               LocationRepository locationRepository,
                               BeneficiaryRepository beneficiaryRepository) {
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.beneficiaryRepository = beneficiaryRepository;
    }

    public byte[] generateTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ── 1. Reference sheet ──────────────────────────────────────
            Sheet ref = wb.createSheet("Reference");

            // ✅ FIXED: removed the isDeleted filter since categories no longer have it
            List<String> speciesCodes = categoryRepository.findAll().stream()
                    .map(LivestockCategory::getCode)
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());

            List<String> locationNames = locationRepository.findAll().stream()
                    .map(Location::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            List<String> nids = beneficiaryRepository.findAll().stream()
                    .map(Beneficiary::getNid)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            writeColumn(ref, 0, "Species", speciesCodes);
            writeColumn(ref, 1, "Location", locationNames);
            writeColumn(ref, 2, "Beneficiary NID", nids);

            for (int i = 0; i < 3; i++) ref.setColumnWidth(i, 25 * 256);

            int speciesLastRow = speciesCodes.size();
            int locationLastRow = locationNames.size();
            int nidLastRow = nids.size();

            Name speciesName = wb.createName();
            speciesName.setNameName("SpeciesList");
            speciesName.setRefersToFormula("Reference!$A$2:$A$"
                    + Math.max(2, speciesLastRow + 1));

            Name locationName = wb.createName();
            locationName.setNameName("LocationList");
            locationName.setRefersToFormula("Reference!$B$2:$B$"
                    + Math.max(2, locationLastRow + 1));

            Name nidName = wb.createName();
            nidName.setNameName("NidList");
            nidName.setRefersToFormula("Reference!$C$2:$C$"
                    + Math.max(2, nidLastRow + 1));

            // ── 2. Livestock sheet ───────────────────────────────────────
            Sheet sheet = wb.createSheet("Livestock");

            String[] headers = {
                    "Tag Number", "Species", "Gender", "Birth Date",
                    "Acquisition Method", "Health Status", "Reproductive Status",
                    "Location", "Beneficiary NID", "Notes", "Photo URL",
                    "Is Group", "Quantity"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell c = sheet.getRow(0) == null
                        ? sheet.createRow(0).createCell(i)
                        : sheet.getRow(0).createCell(i);
                c.setCellValue(headers[i]);

                CellStyle style = wb.createCellStyle();
                Font bold = wb.createFont();
                bold.setBold(true);
                style.setFont(bold);
                style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                c.setCellStyle(style);
                sheet.setColumnWidth(i, 22 * 256);
            }

            // Example rows
            writeSampleRow(sheet, 1,
                    "GOAT-001", "GOAT", "FEMALE", LocalDate.now().minusMonths(10).toString(),
                    "PURCHASE", "HEALTHY", "OPEN",
                    locationNames.isEmpty() ? "" : locationNames.get(0),
                    nids.isEmpty() ? "" : nids.get(0),
                    "Example individual animal", "", "no", "");
            writeSampleRow(sheet, 2,
                    "BROILER-2026-001", "CHICKEN", "MIXED", LocalDate.now().minusWeeks(2).toString(),
                    "PURCHASE", "HEALTHY", "NOT_APPLICABLE",
                    locationNames.isEmpty() ? "" : locationNames.get(0),
                    nids.isEmpty() ? "" : nids.get(0),
                    "Example batch of 500 broilers", "", "yes", "500");

            // Dropdowns
            addDropdown(sheet, 1, 500, 1, "SpeciesList");
            addDropdown(sheet, 1, 500, 7, "LocationList");
            addDropdown(sheet, 1, 500, 8, "NidList");
            addStaticDropdown(sheet, 1, 500, 2, "MALE", "FEMALE", "MIXED");
            addStaticDropdown(sheet, 1, 500, 4, "PURCHASE", "BIRTH", "DONATION", "TRANSFER");
            addStaticDropdown(sheet, 1, 500, 5, "HEALTHY", "SICK", "UNDER_TREATMENT");
            addStaticDropdown(sheet, 1, 500, 6,
                    "OPEN", "PREGNANT", "READY_TO_BREED", "NOT_APPLICABLE", "BRED");
            addStaticDropdown(sheet, 1, 500, 11, "yes", "no");

            // ── 3. Instructions sheet ────────────────────────────────────
            Sheet instr = wb.createSheet("Instructions");
            String[] lines = {
                    "Livestock Bulk Import — Instructions",
                    "",
                    "1. Fill in the 'Livestock' sheet. One row per animal, or one row per batch.",
                    "2. Tag Number is OPTIONAL — if left blank, a tag is assigned automatically",
                    "   from the Species prefix (e.g. GOAT-005, GOAT-006, …).",
                    "3. Birth Date is REQUIRED. Accepted formats: yyyy-MM-dd, dd/MM/yyyy, dd.MM.yyyy.",
                    "4. Location and Beneficiary NID are validated against the 'Reference' sheet.",
                    "   Pick values from the dropdowns to avoid typos.",
                    "5. Photo URL is optional — paste a URL or a path like /uploads/photos/x.jpg.",
                    "6. For a batch (e.g. 500 broilers) set 'Is Group = yes' and Quantity = 500.",
                    "7. Save as .xlsx and upload.",
                    "",
                    "Any row that fails validation will be shown with a red ✗ on the preview.",
                    "Yellow ⚠ rows will still be imported but with a note.",
                    "Green ✓ rows will be imported without changes."
            };
            for (int i = 0; i < lines.length; i++) {
                Row r = instr.createRow(i);
                r.createCell(0).setCellValue(lines[i]);
            }
            instr.setColumnWidth(0, 100 * 256);

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Could not generate template: " + e.getMessage(), e);
        }
    }

    private void writeColumn(Sheet sheet, int col, String header, List<String> values) {
        Row h = sheet.getRow(0) == null ? sheet.createRow(0) : sheet.getRow(0);
        h.createCell(col).setCellValue(header);
        for (int i = 0; i < values.size(); i++) {
            Row r = sheet.getRow(i + 1) == null ? sheet.createRow(i + 1) : sheet.getRow(i + 1);
            r.createCell(col).setCellValue(values.get(i));
        }
    }

    private void writeSampleRow(Sheet sheet, int rowIdx, String... cells) {
        Row r = sheet.createRow(rowIdx);
        for (int i = 0; i < cells.length; i++) {
            r.createCell(i).setCellValue(cells[i]);
        }
    }

    private void addDropdown(Sheet sheet, int firstRow, int lastRow, int col, String rangeName) {
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper((XSSFSheet) sheet);
        DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeName);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, col, col);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private void addStaticDropdown(Sheet sheet, int firstRow, int lastRow, int col, String... values) {
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper((XSSFSheet) sheet);
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, col, col);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }
}