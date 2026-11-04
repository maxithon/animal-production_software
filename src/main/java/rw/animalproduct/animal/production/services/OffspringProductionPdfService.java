package rw.animalproduct.animal.production.services;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.DateRange;
import rw.animalproduct.animal.production.dto.OffspringProductionKpiDTO;
import rw.animalproduct.animal.production.dto.OffspringProductionReportDTO;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OffspringProductionPdfService {

    private static final int MAX_PDF_ROWS = 2000;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);
    private static final Font KPI_LABEL_FONT = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font KPI_VALUE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font EMPTY_STATE_FONT = new Font(Font.HELVETICA, 11, Font.ITALIC, Color.GRAY);
    private static final Color HEADER_BG = new Color(51, 65, 85);

    private final OffspringProductionReportService reportService;

    public byte[] generate(UUID categoryId, String status, String search, DateRange range,
                           String categoryLabel, String periodLabel) throws Exception {

        Page<OffspringProductionReportDTO> page = reportService.getReport(
                categoryId, status, search, range, PageRequest.of(0, MAX_PDF_ROWS));
        List<OffspringProductionReportDTO> rows = page.getContent();
        OffspringProductionKpiDTO kpis = reportService.getKpis(categoryId, status, range);

        if (rows.isEmpty()) {
            log.info("PDF export requested with zero matching rows (categoryId={}, status={}, search={}, range={})",
                    categoryId, status, search, range);
        }

        String resolvedCategory = categoryLabel == null ? "All Categories" : categoryLabel;
        String resolvedPeriod = periodLabel == null ? "All Time" : periodLabel;
        boolean truncated = page.getTotalElements() > MAX_PDF_ROWS;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        PdfWriter.getInstance(document, out);
        document.open();

        addTitleBlock(document, resolvedCategory, resolvedPeriod);
        addKpiBlock(document, kpis);

        if (rows.isEmpty()) {
            addEmptyState(document);
        } else {
            addRowsTable(document, rows);
        }

        if (truncated) {
            Paragraph note = new Paragraph(
                    "Note: results truncated to the first " + MAX_PDF_ROWS + " rows.",
                    SUBTITLE_FONT);
            note.setSpacingBefore(8);
            document.add(note);
        }

        document.close();
        return out.toByteArray();
    }

    private void addTitleBlock(Document document, String categoryLabel, String periodLabel) throws DocumentException {
        Paragraph title = new Paragraph("Offspring Production Report", TITLE_FONT);
        document.add(title);

        Paragraph subtitle = new Paragraph(
                categoryLabel + "  |  " + periodLabel + "  |  Generated " + LocalDate.now().format(DATE_FMT),
                SUBTITLE_FONT);
        subtitle.setSpacingBefore(4);
        subtitle.setSpacingAfter(14);
        document.add(subtitle);
    }

    private void addKpiBlock(Document document, OffspringProductionKpiDTO kpis) throws DocumentException {
        if (kpis == null) return;

        PdfPTable kpiTable = new PdfPTable(4);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingAfter(16);

        addKpiCell(kpiTable, "Total Mothers", String.valueOf(kpis.getTotalMothers()));
        addKpiCell(kpiTable, "Productive Mothers", String.valueOf(kpis.getProductiveMothers()));
        addKpiCell(kpiTable, "Total Birth Events", String.valueOf(kpis.getTotalBirthEvents()));
        addKpiCell(kpiTable, "Total Offspring", String.valueOf(kpis.getTotalOffspring()));

        addKpiCell(kpiTable, "Avg / Birth", String.valueOf(kpis.getAverageOffspringPerBirth()));
        addKpiCell(kpiTable, "Avg / Productive Mother", String.valueOf(kpis.getAverageOffspringPerProductiveMother()));
        addKpiCell(kpiTable, "Total Alive", String.valueOf(kpis.getTotalAlive()));

        Double survival = kpis.getSurvivalRate();
        addKpiCell(kpiTable, "Survival Rate", survival == null ? "N/A" : survival + "%");

        addKpiCell(kpiTable, "Productive Rate", kpis.getProductiveRate() == null ? "N/A" : kpis.getProductiveRate() + "%");
        addKpiCell(kpiTable, "Total Sold", String.valueOf(kpis.getTotalSold()));
        addKpiCell(kpiTable, "Total Transferred", String.valueOf(kpis.getTotalTransferred()));

        document.add(kpiTable);
    }

    private void addKpiCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setPadding(8);

        Paragraph content = new Paragraph();
        content.add(new Chunk(label + "\n", KPI_LABEL_FONT));
        content.add(new Chunk(value, KPI_VALUE_FONT));
        cell.addElement(content);

        table.addCell(cell);
    }

    private void addEmptyState(Document document) throws DocumentException {
        Paragraph empty = new Paragraph(
                "No offspring production records match the selected filters.", EMPTY_STATE_FONT);
        empty.setSpacingBefore(20);
        empty.setSpacingAfter(20);
        empty.setAlignment(Element.ALIGN_CENTER);
        document.add(empty);
    }

    private void addRowsTable(Document document, List<OffspringProductionReportDTO> rows) throws DocumentException {
        String[] headers = {"Rank", "Tag", "Category", "Location", "Beneficiary", "Phone",
                "Births", "Offspring", "Avg/Birth", "Status", "Repro Status", "Last Birth"};
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 1.4f, 1.0f, 1.6f, 1.4f, 1.0f, 0.8f, 0.8f, 0.8f, 0.8f, 1.2f, 1.0f});

        for (String header : headers) {
            PdfPCell headerCell = new PdfPCell(new Phrase(header, TABLE_HEADER_FONT));
            headerCell.setBackgroundColor(HEADER_BG);
            headerCell.setPadding(5);
            headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(headerCell);
        }

        for (OffspringProductionReportDTO row : rows) {
            addCell(table, String.valueOf(row.getRank()));
            addCell(table, row.getTagNumber());
            addCell(table, row.getCategoryName());
            addCell(table, row.getLocationName() != null ? row.getLocationName() : "Unassigned");
            addCell(table, row.getBeneficiaryName() != null ? row.getBeneficiaryName() : "Unknown");
            addCell(table, row.getBeneficiaryPhone() != null ? row.getBeneficiaryPhone() : "—");
            addCell(table, String.valueOf(row.getNumberOfBirths()));
            addCell(table, String.valueOf(row.getTotalOffspring()));
            addCell(table, String.valueOf(row.getAverageOffspringPerBirth()));
            addCell(table, row.getStatus());
            addCell(table, row.getReproductiveStatusLabel() != null ? row.getReproductiveStatusLabel() : "—");
            addCell(table, row.getLastBirthDate() != null ? row.getLastBirthDate().format(DATE_FMT) : "—");
        }

        document.add(table);
    }

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, TABLE_CELL_FONT));
        cell.setPadding(4);
        table.addCell(cell);
    }
}