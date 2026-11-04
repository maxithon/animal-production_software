package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.OffspringProductionReportDTO;
import rw.animalproduct.animal.production.entity.DateRange;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OffspringProductionExcelService {

    private static final int MAX_EXPORT_ROWS = 5000;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final String[] HEADERS = {
            "Rank", "Animal Tag", "Category", "Location", "Beneficiary", "Beneficiary Phone",
            "Birth Events", "Total Offspring", "Male", "Female", "Unknown", "Avg. Offspring per Birth",
            "First Birth Date", "Last Birth Date", "Days Since Last Birth", "Current Age",
            "Alive", "Dead", "Sold", "Transferred", "Weaned",
            "Survival Rate %", "Reproductive Status", "Status"
    };

    private final OffspringProductionReportService reportService;

    public byte[] generate(UUID categoryId, String status, String search, DateRange range) throws Exception {
        List<OffspringProductionReportDTO> rows = reportService.getReport(
                categoryId, status, search, range, PageRequest.of(0, MAX_EXPORT_ROWS)).getContent();

        if (rows.isEmpty()) {
            log.info("Excel export requested with zero matching rows (categoryId={}, status={}, search={}, range={})",
                    categoryId, status, search, range);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offspring Production");

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle numericStyle = workbook.createCellStyle();
            DataFormat fmt = workbook.createDataFormat();
            numericStyle.setDataFormat(fmt.getFormat("0.00"));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, 1);

            int rowIdx = 1;
            for (OffspringProductionReportDTO r : rows) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                setCell(row, col++, r.getRank());
                setCell(row, col++, r.getTagNumber());
                setCell(row, col++, r.getCategoryName());
                setCell(row, col++, r.getLocationName() != null ? r.getLocationName() : "Unassigned");
                setCell(row, col++, r.getBeneficiaryName() != null ? r.getBeneficiaryName() : "Unknown");
                setCell(row, col++, r.getBeneficiaryPhone() != null ? r.getBeneficiaryPhone() : "—");
                setCell(row, col++, r.getNumberOfBirths());
                setCell(row, col++, r.getTotalOffspring());
                setCell(row, col++, r.getMaleOffspring());
                setCell(row, col++, r.getFemaleOffspring());
                setCell(row, col++, r.getUnknownOffspring());
                setCellNumeric(row, col++, r.getAverageOffspringPerBirth(), numericStyle);
                setCell(row, col++, r.getFirstBirthDate() != null ? r.getFirstBirthDate().format(DATE_FMT) : "");
                setCell(row, col++, r.getLastBirthDate() != null ? r.getLastBirthDate().format(DATE_FMT) : "");
                setCell(row, col++, r.getDaysSinceLastBirthDisplay() != null ? r.getDaysSinceLastBirthDisplay() : "—");
                setCell(row, col++, r.getCurrentAgeDisplay());
                setCell(row, col++, r.getAliveCount());
                setCell(row, col++, r.getDeathCount());
                setCell(row, col++, r.getSoldCount());
                setCell(row, col++, r.getTransferredCount());
                setCell(row, col++, r.getWeanedCount());
                setCellNumeric(row, col++, r.getSurvivalRate() != null ? r.getSurvivalRate() : 0.0, numericStyle);
                setCell(row, col++, r.getReproductiveStatusLabel() != null ? r.getReproductiveStatusLabel() : "—");
                setCell(row, col, r.getStatus());
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            if (rows.size() > 0) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, rows.size(), 0, HEADERS.length - 1));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void setCell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    private void setCell(Row row, int col, long value) {
        row.createCell(col).setCellValue(value);
    }

    private void setCellNumeric(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}