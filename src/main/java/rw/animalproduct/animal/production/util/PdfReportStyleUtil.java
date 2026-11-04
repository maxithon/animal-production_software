package rw.animalproduct.animal.production.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Shared PDF layout/styling for every "quick report" PDF export (Overdue
 * Pregnancies, Upcoming Births, and any future report). Keeping this in one
 * place means every exported PDF shares the same brand header, KPI strip,
 * and table styling instead of each controller reinventing it.
 *
 * Uses OpenPDF (com.github.librepdf:openpdf) — same com.lowagie.text API
 * surface as classic iText 2.x, already on the project's classpath.
 */
public final class PdfReportStyleUtil {

    public static final Color BRAND_GREEN      = new Color(47, 107, 79);
    public static final Color BRAND_GREEN_DARK = new Color(35, 79, 59);
    public static final Color HEADER_GREY      = new Color(240, 244, 241);
    public static final Color TEXT_MUTED       = new Color(107, 122, 114);
    public static final Color LINE_GREY        = new Color(228, 232, 229);
    public static final Color RED              = new Color(181, 72, 47);
    public static final Color AMBER            = new Color(201, 138, 52);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private PdfReportStyleUtil() {}

    @FunctionalInterface
    public interface TableBodyWriter {
        void write(PdfPTable table) throws DocumentException;
    }

    /**
     * Builds a landscape A4 PDF: branded title, generated-on date, an optional
     * filter summary line, an optional KPI strip, then a data table.
     *
     * @param columnWidths relative column widths (same length as columnHeaders), or null for equal widths
     * @param kpiPairs     label/value pairs rendered as a strip of cards under the header, or null/empty to skip
     * @param bodyWriter   callback that appends the data rows to the table (called once, after headers are added)
     */
    public static byte[] buildReport(
            String title,
            String subtitle,
            String filterSummary,
            List<String> columnHeaders,
            float[] columnWidths,
            List<String[]> kpiPairs,
            TableBodyWriter bodyWriter
    ) throws DocumentException, IOException {

        Document document = new Document(PageSize.A4.rotate(), 28, 28, 90, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        writer.setPageEvent(new HeaderFooterEvent(title));
        document.open();

        Font titleFont    = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_GREEN_DARK);
        Font subtitleFont = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_MUTED);
        Font filterFont   = new Font(Font.HELVETICA, 8, Font.ITALIC, TEXT_MUTED);

        Paragraph titleP = new Paragraph(title, titleFont);
        titleP.setSpacingAfter(2f);
        document.add(titleP);

        if (subtitle != null && !subtitle.isBlank()) {
            document.add(new Paragraph(subtitle, subtitleFont));
        }

        Paragraph generated = new Paragraph("Generated " + LocalDate.now().format(DATE_FMT), filterFont);
        generated.setSpacingAfter(2f);
        document.add(generated);

        Paragraph filterP = new Paragraph(
                "Filters: " + (filterSummary != null && !filterSummary.isBlank() ? filterSummary : "None"),
                filterFont);
        filterP.setSpacingAfter(10f);
        document.add(filterP);

        // -- KPI strip --------------------------------------------------------
        if (kpiPairs != null && !kpiPairs.isEmpty()) {
            PdfPTable kpiTable = new PdfPTable(kpiPairs.size());
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(14f);
            Font kpiLabelFont = new Font(Font.HELVETICA, 7, Font.BOLD, TEXT_MUTED);
            Font kpiValueFont = new Font(Font.HELVETICA, 14, Font.BOLD, BRAND_GREEN_DARK);
            for (String[] pair : kpiPairs) {
                PdfPCell cell = new PdfPCell();
                cell.setBorderColor(LINE_GREY);
                cell.setPadding(8f);
                cell.setBackgroundColor(HEADER_GREY);
                cell.addElement(new Paragraph(pair[1], kpiValueFont));
                cell.addElement(new Paragraph(pair[0].toUpperCase(), kpiLabelFont));
                kpiTable.addCell(cell);
            }
            document.add(kpiTable);
        }

        // -- Data table ---------------------------------------------------------
        PdfPTable table = columnWidths != null
                ? new PdfPTable(columnWidths)
                : new PdfPTable(columnHeaders.size());
        table.setWidthPercentage(100);

        // FIX: Replaced setEqualColumnWidths(true) with this
        if (columnWidths == null) {
            try {
                float[] equalWidths = new float[columnHeaders.size()];
                for (int i = 0; i < equalWidths.length; i++) {
                    equalWidths[i] = 1f;
                }
                table.setWidths(equalWidths);
            } catch (DocumentException e) {
                throw new RuntimeException(e);
            }
        }

        Font headFont = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.WHITE);
        for (String h : columnHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
            cell.setBackgroundColor(BRAND_GREEN);
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
        table.setHeaderRows(1);

        bodyWriter.write(table);
        document.add(table);
        document.close();
        return baos.toByteArray();
    }

    public static PdfPCell bodyCell(String text, Color color, boolean bold) {
        Font font = new Font(Font.HELVETICA, 7.5f, bold ? Font.BOLD : Font.NORMAL,
                color != null ? color : Color.DARK_GRAY);
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setPadding(5f);
        cell.setBorderColor(LINE_GREY);
        return cell;
    }

    private static class HeaderFooterEvent extends PdfPageEventHelper {
        private final String reportTitle;

        HeaderFooterEvent(String reportTitle) {
            this.reportTitle = reportTitle;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font footerFont = new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_MUTED);
            Phrase footer = new Phrase(
                    "Animal Production System \u2014 " + reportTitle + "   \u00b7   Page " + writer.getPageNumber(),
                    footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer,
                    (document.right() + document.left()) / 2, document.bottom() - 20, 0);
        }
    }
}