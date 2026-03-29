package rw.animalproduct.animal.production.controller;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;
import rw.animalproduct.animal.production.services.*;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/reports/supervisor")
public class SupervisorReportController {

    private final AbaragizwaAmatungoRepository  benRepository;
    private final LivestockRepository           livestockRepository;
    private final LivestockSickRepository       sickRepository;
    private final LivestockTreatmentRepository  treatmentRepository;
    private final UhagarariyeAbororaService     supervisorService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public SupervisorReportController(
            AbaragizwaAmatungoRepository  benRepository,
            LivestockRepository           livestockRepository,
            LivestockSickRepository       sickRepository,
            LivestockTreatmentRepository  treatmentRepository,
            UhagarariyeAbororaService     supervisorService) {

        this.benRepository       = benRepository;
        this.livestockRepository = livestockRepository;
        this.sickRepository      = sickRepository;
        this.treatmentRepository = treatmentRepository;
        this.supervisorService   = supervisorService;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private Map<UUID, Long> buildBenCountBySupervisor() {
        try {
            List<Object[]> rows = benRepository.countByEachSupervisor();
            Map<UUID, Long> map = new HashMap<>();
            for (Object[] row : rows) {
                map.put((UUID) row[0], (Long) row[1]);
            }
            return map;
        } catch (Exception e) {
            Map<UUID, Long> map = new HashMap<>();
            benRepository.findAll().forEach(b -> {
                if (b.getUhagarariyeAborora() != null) {
                    map.merge(b.getUhagarariyeAborora().getId(), 1L, Long::sum);
                }
            });
            return map;
        }
    }

    private List<SupervisorSummary> buildSummaries(Map<UUID, Long> benCount) {
        return supervisorService.getAll().stream()
                .map(s -> new SupervisorSummary(s, benCount.getOrDefault(s.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private List<AbaragizwaAmatungo> beneficiariesOf(UUID supervisorId) {
        try {
            return benRepository.findByUhagarariyeAbororaId(supervisorId);
        } catch (Exception e) {
            return benRepository.findAll().stream()
                    .filter(b -> b.getUhagarariyeAborora() != null
                            && b.getUhagarariyeAborora().getId().equals(supervisorId))
                    .collect(Collectors.toList());
        }
    }

    private List<Livestock> animalsOf(UUID beneficiaryId) {
        try {
            return livestockRepository.findByAbaragizwaAmatungoId(beneficiaryId);
        } catch (Exception e) {
            return livestockRepository.findAll().stream()
                    .filter(l -> l.getAbaragizwaAmatungo() != null
                            && l.getAbaragizwaAmatungo().getId().equals(beneficiaryId))
                    .collect(Collectors.toList());
        }
    }

    private List<BeneficiaryStat> buildBenStats(List<AbaragizwaAmatungo> beneficiaries) {
        List<BeneficiaryStat> stats = new ArrayList<>();
        for (AbaragizwaAmatungo ben : beneficiaries) {
            List<Livestock> animals = animalsOf(ben.getId());
            List<UUID> animalIds = animals.stream().map(Livestock::getId).collect(Collectors.toList());

            long totalAnimals  = animals.size();
            long activeAnimals = animals.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
            long soldAnimals   = animals.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();
            long bornOnFarm    = animals.stream().filter(l -> l.getMother() != null).count();

            // Calculate current value (only for active animals)
            BigDecimal currentValue = animals.stream()
                    .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()) && l.getCurrentValue() != null)
                    .map(Livestock::getCurrentValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calculate sold amount (sum of sold prices)
            BigDecimal soldAmount = animals.stream()
                    .filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus()) && l.getSoldPrice() != null)
                    .map(Livestock::getSoldPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Sick stats
            List<LivestockSick> sickList = animalIds.isEmpty()
                    ? Collections.emptyList()
                    : fetchSickByAnimalIds(animalIds);
            long sickCount      = sickList.size();
            long criticalCount  = sickList.stream().filter(s -> s.getStatus() != null && "CRITICAL".equals(s.getStatus().name())).count();
            long recoveredCount = sickList.stream().filter(s -> s.getStatus() != null && "RECOVERED".equals(s.getStatus().name())).count();
            BigDecimal sickCost = sickList.stream().filter(s -> s.getTreatmentCost() != null)
                    .map(LivestockSick::getTreatmentCost).reduce(BigDecimal.ZERO, BigDecimal::add);

            // Treatment stats
            List<LivestockTreatment> treatments = animalIds.isEmpty()
                    ? Collections.emptyList()
                    : fetchTreatmentsByAnimalIds(animalIds);
            long treatCount = treatments.size();
            BigDecimal treatCost = treatments.stream().filter(t -> t.getTreatmentCost() != null)
                    .map(LivestockTreatment::getTreatmentCost).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCost = sickCost.add(treatCost);

            stats.add(new BeneficiaryStat(ben, totalAnimals, activeAnimals, soldAnimals, bornOnFarm,
                    currentValue, soldAmount,
                    sickCount, criticalCount, recoveredCount,
                    treatCount, treatCost, sickCost, totalCost));
        }
        return stats;
    }

    private List<LivestockSick> fetchSickByAnimalIds(List<UUID> animalIds) {
        try {
            return sickRepository.findByLivestockIds(animalIds);
        } catch (Exception e) {
            return sickRepository.findAll().stream()
                    .filter(s -> s.getLivestock() != null && animalIds.contains(s.getLivestock().getId()))
                    .collect(Collectors.toList());
        }
    }

    private List<LivestockTreatment> fetchTreatmentsByAnimalIds(List<UUID> animalIds) {
        try {
            return treatmentRepository.findByLivestockIds(animalIds);
        } catch (Exception e) {
            return treatmentRepository.findAll().stream()
                    .filter(t -> t.getLivestock() != null && animalIds.contains(t.getLivestock().getId()))
                    .collect(Collectors.toList());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  VIEW ENDPOINTS
    // ═════════════════════════════════════════════════════════════════════════

    /** Landing page — shows all supervisors in sidebar only. */
    @GetMapping
    public String landing(Model model) {
        Map<UUID, Long> benCount = buildBenCountBySupervisor();
        model.addAttribute("summaries",         buildSummaries(benCount));
        model.addAttribute("totalSupervisors",  supervisorService.getAll().size());
        model.addAttribute("totalBeneficiaries", benRepository.count());
        model.addAttribute("selectedSupervisor", null);
        return "supervisor-report";
    }

    /**
     * Supervisor selected → show their beneficiary overview table.
     */
    @GetMapping("/{supervisorId}")
    public String supervisorView(@PathVariable UUID supervisorId, Model model) {
        UhagarariyeAborora sup = supervisorService.getById(supervisorId).orElse(null);
        if (sup == null) return "redirect:/reports/supervisor";

        List<AbaragizwaAmatungo> beneficiaries = beneficiariesOf(supervisorId);
        List<BeneficiaryStat> benStats = buildBenStats(beneficiaries);

        // Per-supervisor KPI totals
        long       totalAnimals    = benStats.stream().mapToLong(BeneficiaryStat::getTotalAnimals).sum();
        long       totalActive     = benStats.stream().mapToLong(BeneficiaryStat::getActiveAnimals).sum();
        long       totalSold       = benStats.stream().mapToLong(BeneficiaryStat::getSoldAnimals).sum();
        long       totalBorn       = benStats.stream().mapToLong(BeneficiaryStat::getBornOnFarm).sum();
        long       totalSick       = benStats.stream().mapToLong(BeneficiaryStat::getSickCount).sum();
        long       totalCritical   = benStats.stream().mapToLong(BeneficiaryStat::getCriticalCount).sum();
        long       totalRecovered  = benStats.stream().mapToLong(BeneficiaryStat::getRecoveredCount).sum();
        long       totalTreat      = benStats.stream().mapToLong(BeneficiaryStat::getTreatCount).sum();

        BigDecimal totalCurrentValue = benStats.stream().map(BeneficiaryStat::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSoldAmount   = benStats.stream().map(BeneficiaryStat::getSoldAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSickCost     = benStats.stream().map(BeneficiaryStat::getSickCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTreatCost    = benStats.stream().map(BeneficiaryStat::getTreatCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost         = benStats.stream().map(BeneficiaryStat::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, Long> benCount = buildBenCountBySupervisor();

        // Model attributes
        model.addAttribute("summaries",          buildSummaries(benCount));
        model.addAttribute("totalSupervisors",   supervisorService.getAll().size());
        model.addAttribute("totalBeneficiaries", benRepository.count());
        model.addAttribute("selectedSupervisor", sup);
        model.addAttribute("selectedBeneficiary", null);
        model.addAttribute("beneficiaries",      beneficiaries);
        model.addAttribute("benStats",           benStats);

        // KPI strip
        model.addAttribute("supTotalAnimals",      totalAnimals);
        model.addAttribute("supTotalActive",       totalActive);
        model.addAttribute("supTotalSold",         totalSold);
        model.addAttribute("supTotalSick",         totalSick);
        model.addAttribute("supTotalTreat",        totalTreat);
        model.addAttribute("supTotalBorn",         totalBorn);
        model.addAttribute("supTotalCurrentValue", totalCurrentValue);
        model.addAttribute("supTotalSoldAmount",   totalSoldAmount);
        model.addAttribute("supTotalCost",         totalCost);

        // Footer totals for beneficiary overview table
        model.addAttribute("totalBenAnimals",      totalAnimals);
        model.addAttribute("totalBenActive",       totalActive);
        model.addAttribute("totalBenSold",         totalSold);
        model.addAttribute("totalBenBorn",         totalBorn);
        model.addAttribute("totalBenSick",         totalSick);
        model.addAttribute("totalBenCritical",     totalCritical);
        model.addAttribute("totalBenRecovered",    totalRecovered);
        model.addAttribute("totalBenTreat",        totalTreat);
        model.addAttribute("totalBenCurrentValue", totalCurrentValue);
        model.addAttribute("totalBenSoldAmount",   totalSoldAmount);
        model.addAttribute("totalBenSickCost",     totalSickCost);
        model.addAttribute("totalBenTreatCost",    totalTreatCost);

        return "supervisor-report";
    }

    /**
     * Beneficiary selected → show their animals with full stats.
     */
    @GetMapping("/{supervisorId}/beneficiary/{beneficiaryId}")
    public String beneficiaryView(@PathVariable UUID supervisorId,
                                  @PathVariable UUID beneficiaryId,
                                  Model model) {

        UhagarariyeAborora     sup = supervisorService.getById(supervisorId).orElse(null);
        AbaragizwaAmatungo     ben = benRepository.findById(beneficiaryId).orElse(null);
        if (sup == null || ben == null) return "redirect:/reports/supervisor";

        List<AbaragizwaAmatungo> beneficiaries = beneficiariesOf(supervisorId);
        List<Livestock>          animals       = animalsOf(beneficiaryId);
        List<UUID>               animalIds     = animals.stream().map(Livestock::getId).collect(Collectors.toList());

        // Counts
        long totalAnimals  = animals.size();
        long activeAnimals = animals.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long bornOnFarm    = animals.stream().filter(l -> l.getMother() != null).count();
        long soldAnimals   = animals.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();
        long deadAnimals   = animals.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count();

        // Current value and sold amount
        BigDecimal currentValue = animals.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()) && l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal soldAmount = animals.stream()
                .filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus()) && l.getSoldPrice() != null)
                .map(Livestock::getSoldPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sick
        List<LivestockSick> sickList = animalIds.isEmpty()
                ? Collections.emptyList() : fetchSickByAnimalIds(animalIds);
        long sickCount      = sickList.size();
        long criticalCount  = sickList.stream().filter(s -> s.getStatus() != null && "CRITICAL".equals(s.getStatus().name())).count();
        long recoveredCount = sickList.stream().filter(s -> s.getStatus() != null && "RECOVERED".equals(s.getStatus().name())).count();
        BigDecimal sickCost = sickList.stream().filter(s -> s.getTreatmentCost() != null)
                .map(LivestockSick::getTreatmentCost).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, List<LivestockSick>> animalSickMap = sickList.stream()
                .filter(s -> s.getLivestock() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getId()));

        Map<UUID, BigDecimal> animalSickCostMap = sickList.stream()
                .filter(s -> s.getLivestock() != null && s.getTreatmentCost() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getLivestock().getId(),
                        Collectors.reducing(BigDecimal.ZERO, LivestockSick::getTreatmentCost, BigDecimal::add)));

        // Treatments
        List<LivestockTreatment> treatments = animalIds.isEmpty()
                ? Collections.emptyList() : fetchTreatmentsByAnimalIds(animalIds);
        long treatCount = treatments.size();
        BigDecimal treatCost = treatments.stream().filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, Long> animalTreatCountMap = treatments.stream()
                .filter(t -> t.getLivestock() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getId(), Collectors.counting()));

        Map<UUID, BigDecimal> animalTreatCostMap = treatments.stream()
                .filter(t -> t.getLivestock() != null && t.getTreatmentCost() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getLivestock().getId(),
                        Collectors.reducing(BigDecimal.ZERO, LivestockTreatment::getTreatmentCost, BigDecimal::add)));

        BigDecimal totalCost = sickCost.add(treatCost);

        Map<UUID, Long> benCount = buildBenCountBySupervisor();

        // Model attributes
        model.addAttribute("summaries",           buildSummaries(benCount));
        model.addAttribute("totalSupervisors",    supervisorService.getAll().size());
        model.addAttribute("totalBeneficiaries",  benRepository.count());
        model.addAttribute("selectedSupervisor",  sup);
        model.addAttribute("selectedBeneficiary", ben);
        model.addAttribute("beneficiaries",       beneficiaries);
        model.addAttribute("animals",             animals);
        model.addAttribute("totalAnimals",        totalAnimals);
        model.addAttribute("activeAnimals",       activeAnimals);
        model.addAttribute("bornOnFarm",          bornOnFarm);
        model.addAttribute("soldAnimals",         soldAnimals);
        model.addAttribute("deadAnimals",         deadAnimals);
        model.addAttribute("currentValue",        currentValue);
        model.addAttribute("soldAmount",          soldAmount);
        model.addAttribute("sickCount",           sickCount);
        model.addAttribute("criticalCount",       criticalCount);
        model.addAttribute("recoveredCount",      recoveredCount);
        model.addAttribute("treatmentCount",      treatCount);
        model.addAttribute("treatCost",           treatCost);
        model.addAttribute("sickCost",            sickCost);
        model.addAttribute("totalCost",           totalCost);
        model.addAttribute("animalSickMap",       animalSickMap);
        model.addAttribute("animalTreatCountMap", animalTreatCountMap);
        model.addAttribute("animalSickCostMap",   animalSickCostMap);
        model.addAttribute("animalTreatCostMap",  animalTreatCostMap);

        return "supervisor-report";
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PDF DOWNLOAD — Supervisor overview
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/{supervisorId}/download/pdf")
    public void downloadSupervisorPdf(@PathVariable UUID supervisorId,
                                      HttpServletResponse response) throws IOException {

        UhagarariyeAborora sup = supervisorService.getById(supervisorId).orElse(null);
        if (sup == null) { response.sendError(404); return; }

        List<AbaragizwaAmatungo> beneficiaries = beneficiariesOf(supervisorId);
        List<BeneficiaryStat>    stats         = buildBenStats(beneficiaries);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"Supervisor_Report_" + sup.getLastName() + ".pdf\"");

        Document doc = new Document(PageSize.A4.rotate());
        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();
            addPdfMeta(doc, "Supervisor Report — " + sup.getFirstName() + " " + sup.getLastName());

            Font titleFont  = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(26, 95, 122));
            Font subFont    = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
            Font headerFont = new Font(Font.HELVETICA, 7,  Font.BOLD,   Color.WHITE);
            Font cellFont   = new Font(Font.HELVETICA, 7,  Font.NORMAL, Color.BLACK);

            doc.add(new Paragraph("RAPORO Y'UMUHAGARARIYE / SUPERVISOR REPORT", titleFont));
            doc.add(new Paragraph(sup.getFirstName() + " " + sup.getLastName()
                    + " | NID: " + nvl(sup.getNid())
                    + " | Tel: " + nvl(sup.getPhone()), subFont));
            doc.add(new Paragraph("Generated / Yakozwe: " + LocalDate.now().format(DATE_FMT), subFont));
            doc.add(Chunk.NEWLINE);

            long totAnimals   = stats.stream().mapToLong(BeneficiaryStat::getTotalAnimals).sum();
            long totActive    = stats.stream().mapToLong(BeneficiaryStat::getActiveAnimals).sum();
            long totSold      = stats.stream().mapToLong(BeneficiaryStat::getSoldAnimals).sum();
            long totSick      = stats.stream().mapToLong(BeneficiaryStat::getSickCount).sum();
            long totTreat     = stats.stream().mapToLong(BeneficiaryStat::getTreatCount).sum();
            BigDecimal totCurrentValue = stats.stream().map(BeneficiaryStat::getCurrentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totSoldAmount   = stats.stream().map(BeneficiaryStat::getSoldAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totSickCost     = stats.stream().map(BeneficiaryStat::getSickCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totTreatCost    = stats.stream().map(BeneficiaryStat::getTreatCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totCost         = totSickCost.add(totTreatCost);

            PdfPTable kpiTable = new PdfPTable(7);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(14);
            addKpiCell(kpiTable, "Abaragizwa / Beneficiaries",      String.valueOf(beneficiaries.size()), new Color(26, 95, 122));
            addKpiCell(kpiTable, "Amatungo yose / Total Animals",   String.valueOf(totAnimals),            new Color(16, 185, 129));
            addKpiCell(kpiTable, "Akiriho / Available",             String.valueOf(totActive),             new Color(59, 130, 246));
            addKpiCell(kpiTable, "Yagurishijwe / Sold",             String.valueOf(totSold),               new Color(245, 158, 11));
            addKpiCell(kpiTable, "Agaciro / Current Value",         formatRwf(totCurrentValue),           new Color(139, 92, 246));
            addKpiCell(kpiTable, "Amafaranga yo Kugurisha / Sold",  formatRwf(totSoldAmount),             new Color(34, 197, 94));
            addKpiCell(kpiTable, "IGICIRO CYOSE / Total Cost",      formatRwf(totCost),                   new Color(220, 38, 38));
            doc.add(kpiTable);

            // Main table with all columns including current value and sold amount
            PdfPTable table = new PdfPTable(new float[]{0.3f, 2.5f, 1.5f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 1.5f, 1.5f, 1.3f, 1.3f, 1.5f});
            table.setWidthPercentage(100);

            String[] headers = {
                    "#", "Amazina / Name", "NID",
                    "Amatungo", "Akiriho", "Yaguze", "Yavutswe", "Yarwaye", "Bikomeye",
                    "Agaciro\n(Current RWF)",
                    "Yagurishijwe\n(Sold RWF)",
                    "Igiciro Indwara\n(Sick RWF)",
                    "Igiciro Imiti\n(Treat RWF)",
                    "IGICIRO CYOSE\n(Total RWF)"
            };
            Color headerBg = new Color(26, 95, 122);
            for (String h : headers) {
                PdfPCell hc = new PdfPCell(new Phrase(h, headerFont));
                hc.setBackgroundColor(headerBg);
                hc.setPadding(5);
                hc.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(hc);
            }

            boolean alt = false;
            int rowNum = 1;
            for (BeneficiaryStat s : stats) {
                Color rowBg = alt ? new Color(248, 249, 251) : Color.WHITE;
                addPdfCell(table, String.valueOf(rowNum++),           cellFont, rowBg, Element.ALIGN_CENTER);
                addPdfCell(table, s.getBeneficiary().getFirstName() + " " + s.getBeneficiary().getLastName(), cellFont, rowBg, Element.ALIGN_LEFT);
                addPdfCell(table, nvl(s.getBeneficiary().getNid()),   cellFont, rowBg, Element.ALIGN_CENTER);
                addPdfCell(table, String.valueOf(s.getTotalAnimals()), cellFont, rowBg, Element.ALIGN_CENTER);
                addPdfCell(table, String.valueOf(s.getActiveAnimals()), cellFont, rowBg, Element.ALIGN_CENTER);
                addPdfCell(table, String.valueOf(s.getSoldAnimals()),  cellFont, rowBg, Element.ALIGN_CENTER);
                addPdfCell(table, String.valueOf(s.getBornOnFarm()),   cellFont, rowBg, Element.ALIGN_CENTER);
                addPdfCell(table, String.valueOf(s.getSickCount()),    cellFont, rowBg, Element.ALIGN_CENTER);
                addPdfCell(table, String.valueOf(s.getCriticalCount()), cellFont, rowBg, Element.ALIGN_CENTER);
                addPdfCell(table, formatRwf(s.getCurrentValue()),      cellFont, rowBg, Element.ALIGN_RIGHT);
                addPdfCell(table, formatRwf(s.getSoldAmount()),        cellFont, rowBg, Element.ALIGN_RIGHT);
                addPdfCell(table, formatRwf(s.getSickCost()),          cellFont, rowBg, Element.ALIGN_RIGHT);
                addPdfCell(table, formatRwf(s.getTreatCost()),         cellFont, rowBg, Element.ALIGN_RIGHT);
                addPdfCell(table, formatRwf(s.getTotalCost()),         cellFont, rowBg, Element.ALIGN_RIGHT);
                alt = !alt;
            }

            // Footer row
            Color footerBg = new Color(240, 249, 255);
            Font footerFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(14, 116, 144));
            addPdfCell(table, "", footerFont, footerBg, Element.ALIGN_CENTER);
            addPdfCell(table, "TOTAL / ISANO", footerFont, footerBg, Element.ALIGN_RIGHT);
            addPdfCell(table, "", footerFont, footerBg, Element.ALIGN_CENTER);
            addPdfCell(table, String.valueOf(totAnimals), footerFont, footerBg, Element.ALIGN_CENTER);
            addPdfCell(table, String.valueOf(totActive), footerFont, footerBg, Element.ALIGN_CENTER);
            addPdfCell(table, String.valueOf(totSold), footerFont, footerBg, Element.ALIGN_CENTER);
            addPdfCell(table, String.valueOf(stats.stream().mapToLong(BeneficiaryStat::getBornOnFarm).sum()), footerFont, footerBg, Element.ALIGN_CENTER);
            addPdfCell(table, String.valueOf(totSick), footerFont, footerBg, Element.ALIGN_CENTER);
            addPdfCell(table, String.valueOf(stats.stream().mapToLong(BeneficiaryStat::getCriticalCount).sum()), footerFont, footerBg, Element.ALIGN_CENTER);
            addPdfCell(table, formatRwf(totCurrentValue), footerFont, footerBg, Element.ALIGN_RIGHT);
            addPdfCell(table, formatRwf(totSoldAmount), footerFont, footerBg, Element.ALIGN_RIGHT);
            addPdfCell(table, formatRwf(totSickCost), footerFont, footerBg, Element.ALIGN_RIGHT);
            addPdfCell(table, formatRwf(totTreatCost), footerFont, footerBg, Element.ALIGN_RIGHT);
            addPdfCell(table, formatRwf(totCost), footerFont, footerBg, Element.ALIGN_RIGHT);

            doc.add(table);
            addPdfFooter(doc, subFont);

        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PDF DOWNLOAD — Beneficiary animal detail
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/{supervisorId}/beneficiary/{beneficiaryId}/download/pdf")
    public void downloadBeneficiaryPdf(@PathVariable UUID supervisorId,
                                       @PathVariable UUID beneficiaryId,
                                       HttpServletResponse response) throws IOException {

        UhagarariyeAborora sup = supervisorService.getById(supervisorId).orElse(null);
        AbaragizwaAmatungo ben = benRepository.findById(beneficiaryId).orElse(null);
        if (sup == null || ben == null) { response.sendError(404); return; }

        List<Livestock>          animals    = animalsOf(beneficiaryId);
        List<UUID>               animalIds  = animals.stream().map(Livestock::getId).collect(Collectors.toList());
        List<LivestockSick>      sickList   = animalIds.isEmpty() ? Collections.emptyList() : fetchSickByAnimalIds(animalIds);
        List<LivestockTreatment> treatments = animalIds.isEmpty() ? Collections.emptyList() : fetchTreatmentsByAnimalIds(animalIds);

        Map<UUID, Long> sickPerAnimal  = sickList.stream().filter(s -> s.getLivestock() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getId(), Collectors.counting()));
        Map<UUID, Long> treatPerAnimal = treatments.stream().filter(t -> t.getLivestock() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getId(), Collectors.counting()));
        Map<UUID, BigDecimal> sickCostPerAnimal = sickList.stream()
                .filter(s -> s.getLivestock() != null && s.getTreatmentCost() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getId(),
                        Collectors.reducing(BigDecimal.ZERO, LivestockSick::getTreatmentCost, BigDecimal::add)));
        Map<UUID, BigDecimal> treatCostPerAnimal = treatments.stream()
                .filter(t -> t.getLivestock() != null && t.getTreatmentCost() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getId(),
                        Collectors.reducing(BigDecimal.ZERO, LivestockTreatment::getTreatmentCost, BigDecimal::add)));

        long activeCount = animals.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long soldCount   = animals.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();

        BigDecimal currentValue = animals.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()) && l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal soldAmount = animals.stream()
                .filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus()) && l.getSoldPrice() != null)
                .map(Livestock::getSoldPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sickCost  = sickList.stream().filter(s -> s.getTreatmentCost() != null)
                .map(LivestockSick::getTreatmentCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal treatCost = treatments.stream().filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost).reduce(BigDecimal.ZERO, BigDecimal::add);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"Beneficiary_" + ben.getLastName() + "_Animals.pdf\"");

        Document doc = new Document(PageSize.A4.rotate());
        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();
            addPdfMeta(doc, "Beneficiary Animal Report — " + ben.getFirstName() + " " + ben.getLastName());

            Font titleFont  = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(26, 95, 122));
            Font subFont    = new Font(Font.HELVETICA, 9,  Font.NORMAL, Color.DARK_GRAY);
            Font headerFont = new Font(Font.HELVETICA, 7,  Font.BOLD,   Color.WHITE);
            Font cellFont   = new Font(Font.HELVETICA, 7,  Font.NORMAL, Color.BLACK);
            Font boldBlue   = new Font(Font.HELVETICA, 9,  Font.BOLD,   new Color(26, 95, 122));

            doc.add(new Paragraph("RAPORO Y'UMURAGIZWA / BENEFICIARY ANIMAL REPORT", titleFont));

            // Add beneficiary photo if available
            if (ben.getPhoto() != null && !ben.getPhoto().isEmpty()) {
                try {
                    // Assuming photo is stored as file path
                    File photoFile = new File(ben.getPhoto());
                    if (photoFile.exists()) {
                        Image photo = Image.getInstance(ben.getPhoto());
                        photo.scaleToFit(80, 80);
                        photo.setAlignment(Element.ALIGN_RIGHT);
                        doc.add(photo);
                    }
                } catch (Exception e) {
                    // Photo loading failed, continue without it
                    System.err.println("Could not load beneficiary photo: " + e.getMessage());
                }
            }

            doc.add(new Paragraph(
                    "Umuragizwa: " + ben.getFirstName() + " " + ben.getLastName() +
                            " | NID: " + nvl(ben.getNid()) + " | Tel: " + nvl(ben.getPhone()), subFont));
            doc.add(new Paragraph(
                    "Umuhagarariye: " + sup.getFirstName() + " " + sup.getLastName(), subFont));
            doc.add(new Paragraph("Generated / Yakozwe: " + LocalDate.now().format(DATE_FMT), subFont));
            doc.add(Chunk.NEWLINE);

            PdfPTable kpi = new PdfPTable(7);
            kpi.setWidthPercentage(100);
            kpi.setSpacingAfter(14);
            addKpiCell(kpi, "Amatungo yose / Total",       String.valueOf(animals.size()),    new Color(26, 95, 122));
            addKpiCell(kpi, "Akiriho / Available",         String.valueOf(activeCount),       new Color(16, 185, 129));
            addKpiCell(kpi, "Yagurishijwe / Sold",         String.valueOf(soldCount),         new Color(245, 158, 11));
            addKpiCell(kpi, "Agaciro / Current Value",     formatRwf(currentValue),          new Color(139, 92, 246));
            addKpiCell(kpi, "Yaguze / Sold Amount",        formatRwf(soldAmount),            new Color(34, 197, 94));
            addKpiCell(kpi, "Igiciro Indwara / Sick",      formatRwf(sickCost),              new Color(220, 38, 38));
            addKpiCell(kpi, "Igiciro Imiti / Treatment",   formatRwf(treatCost),             new Color(239, 68, 68));
            doc.add(kpi);

            // Animals table
            PdfPTable table = new PdfPTable(new float[]{0.4f, 1.8f, 0.8f, 1f, 1.5f, 0.6f, 0.6f, 1.3f, 1.3f, 1.5f, 1.5f});
            table.setWidthPercentage(100);

            Color headerBg = new Color(26, 95, 122);
            for (String h : new String[]{"#", "Tag / Inomero", "Igitsina", "Imiterere",
                    "Ubwoko / Category", "Born", "Sick",
                    "Igiciro Indwara\n(Sick RWF)",
                    "Igiciro Imiti\n(Treat RWF)",
                    "IGICIRO CYOSE\n(Total RWF)",
                    "Agaciro / Value\n(RWF)"}) {
                PdfPCell hc = new PdfPCell(new Phrase(h, headerFont));
                hc.setBackgroundColor(headerBg);
                hc.setPadding(5);
                hc.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(hc);
            }

            int row = 1;
            boolean alt = false;
            for (Livestock l : animals) {
                Color bg = alt ? new Color(248, 249, 251) : Color.WHITE;
                BigDecimal sc = sickCostPerAnimal.getOrDefault(l.getId(), BigDecimal.ZERO);
                BigDecimal tc = treatCostPerAnimal.getOrDefault(l.getId(), BigDecimal.ZERO);

                addPdfCell(table, String.valueOf(row++),              cellFont, bg, Element.ALIGN_CENTER);
                addPdfCell(table, nvl(l.getTagNumber()),              cellFont, bg, Element.ALIGN_LEFT);
                addPdfCell(table, nvl(l.getGender()),                 cellFont, bg, Element.ALIGN_CENTER);
                addPdfCell(table, nvl(l.getStatus()),                 cellFont, bg, Element.ALIGN_CENTER);
                addPdfCell(table, l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : "—", cellFont, bg, Element.ALIGN_LEFT);
                addPdfCell(table, l.getMother() != null ? "Yego" : "Oya", cellFont, bg, Element.ALIGN_CENTER);
                addPdfCell(table, String.valueOf(sickPerAnimal.getOrDefault(l.getId(), 0L)), cellFont, bg, Element.ALIGN_CENTER);
                addPdfCell(table, sc.compareTo(BigDecimal.ZERO) > 0 ? formatRwf(sc) : "—", cellFont, bg, Element.ALIGN_RIGHT);
                addPdfCell(table, tc.compareTo(BigDecimal.ZERO) > 0 ? formatRwf(tc) : "—", cellFont, bg, Element.ALIGN_RIGHT);
                addPdfCell(table, sc.add(tc).compareTo(BigDecimal.ZERO) > 0 ? formatRwf(sc.add(tc)) : "—", cellFont, bg, Element.ALIGN_RIGHT);
                addPdfCell(table, l.getCurrentValue() != null ? formatRwf(l.getCurrentValue()) : "—", cellFont, bg, Element.ALIGN_RIGHT);
                alt = !alt;
            }
            doc.add(table);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Agaciro k'amatungo akiriho / Current value of available animals:  " + formatRwf(currentValue), boldBlue));
            doc.add(new Paragraph("Amafaranga yo kugurisha / Sold amount:                           " + formatRwf(soldAmount), boldBlue));
            doc.add(new Paragraph("Igiciro cy'imiti y'indwara / Sick care cost:                     " + formatRwf(sickCost), boldBlue));
            doc.add(new Paragraph("Igiciro cy'imiti / Treatment cost:                               " + formatRwf(treatCost), boldBlue));
            doc.add(new Paragraph("IGICIRO CYOSE CY'IMITI / TOTAL TREATMENT COST:                   " + formatRwf(sickCost.add(treatCost)),
                    new Font(Font.HELVETICA, 11, Font.BOLD, new Color(76, 29, 149))));

            addPdfFooter(doc, subFont);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EXCEL DOWNLOAD — Supervisor overview
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/{supervisorId}/download/excel")
    public void downloadSupervisorExcel(@PathVariable UUID supervisorId,
                                        HttpServletResponse response) throws IOException {

        UhagarariyeAborora sup = supervisorService.getById(supervisorId).orElse(null);
        if (sup == null) { response.sendError(404); return; }

        List<AbaragizwaAmatungo> beneficiaries = beneficiariesOf(supervisorId);
        List<BeneficiaryStat>    stats         = buildBenStats(beneficiaries);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"Supervisor_Report_" + sup.getLastName() + ".xlsx\"");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Supervisor Report");
            sheet.setDefaultColumnWidth(18);

            CellStyle titleStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font tf = wb.createFont();
            tf.setBold(true); tf.setFontHeightInPoints((short) 14);
            tf.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(tf);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font hf = wb.createFont();
            hf.setBold(true); hf.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(hf);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle altStyle = wb.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle numStyle = wb.createCellStyle();
            numStyle.setAlignment(HorizontalAlignment.RIGHT);

            int r = 0;
            Row title = sheet.createRow(r++);
            Cell tc = title.createCell(0);
            tc.setCellValue("RAPORO Y'UMUHAGARARIYE — " + sup.getFirstName() + " " + sup.getLastName());
            tc.setCellStyle(titleStyle);

            Row info = sheet.createRow(r++);
            info.createCell(0).setCellValue("NID: " + nvl(sup.getNid()) + "  |  Tel: " + nvl(sup.getPhone()) + "  |  Generated: " + LocalDate.now().format(DATE_FMT));
            r++;

            Row headerRow = sheet.createRow(r++);
            String[] cols = {
                    "#", "Amazina / Name", "NID", "Telefone / Phone",
                    "Amatungo / Animals", "Akiriho / Available", "Yagurishijwe / Sold",
                    "Yavutswe / Born on Farm", "Yarwaye / Sick", "Bikomeye / Critical",
                    "Yarakize / Recovered", "Imiti / Treatments",
                    "Agaciro / Current Value (RWF)",
                    "Yaguze / Sold Amount (RWF)",
                    "Igiciro Indwara / Sick Cost (RWF)",
                    "Igiciro Imiti / Treat. Cost (RWF)",
                    "IGICIRO CYOSE / TOTAL (RWF)"
            };
            for (int i = 0; i < cols.length; i++) {
                Cell c2 = headerRow.createCell(i);
                c2.setCellValue(cols[i]);
                c2.setCellStyle(headerStyle);
            }

            boolean alt = false;
            int rowNum = 1;
            for (BeneficiaryStat s : stats) {
                Row row = sheet.createRow(r++);
                CellStyle rowStyle = alt ? altStyle : null;
                setCell(row, 0,  String.valueOf(rowNum++), rowStyle);
                setCell(row, 1,  s.getBeneficiary().getFirstName() + " " + s.getBeneficiary().getLastName(), rowStyle);
                setCell(row, 2,  nvl(s.getBeneficiary().getNid()),         rowStyle);
                setCell(row, 3,  nvl(s.getBeneficiary().getPhone()),       rowStyle);
                setNumCell(row, 4,  s.getTotalAnimals(),             numStyle);
                setNumCell(row, 5,  s.getActiveAnimals(),            numStyle);
                setNumCell(row, 6,  s.getSoldAnimals(),              numStyle);
                setNumCell(row, 7,  s.getBornOnFarm(),               numStyle);
                setNumCell(row, 8,  s.getSickCount(),                numStyle);
                setNumCell(row, 9,  s.getCriticalCount(),            numStyle);
                setNumCell(row, 10, s.getRecoveredCount(),           numStyle);
                setNumCell(row, 11, s.getTreatCount(),               numStyle);
                setNumCell(row, 12, s.getCurrentValue().longValue(), numStyle);
                setNumCell(row, 13, s.getSoldAmount().longValue(),   numStyle);
                setNumCell(row, 14, s.getSickCost().longValue(),     numStyle);
                setNumCell(row, 15, s.getTreatCost().longValue(),    numStyle);
                setNumCell(row, 16, s.getTotalCost().longValue(),    numStyle);
                alt = !alt;
            }

            Row totRow = sheet.createRow(r);
            CellStyle totStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font totFont = wb.createFont();
            totFont.setBold(true); totFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            totStyle.setFont(totFont);
            totStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            totStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            setCell(totRow, 0, "", totStyle);
            setCell(totRow, 1, "TOTAL", totStyle);
            setCell(totRow, 2, "", totStyle);
            setCell(totRow, 3, "", totStyle);
            setNumCell(totRow, 4,  stats.stream().mapToLong(BeneficiaryStat::getTotalAnimals).sum(),  totStyle);
            setNumCell(totRow, 5,  stats.stream().mapToLong(BeneficiaryStat::getActiveAnimals).sum(), totStyle);
            setNumCell(totRow, 6,  stats.stream().mapToLong(BeneficiaryStat::getSoldAnimals).sum(),   totStyle);
            setNumCell(totRow, 7,  stats.stream().mapToLong(BeneficiaryStat::getBornOnFarm).sum(),    totStyle);
            setNumCell(totRow, 8,  stats.stream().mapToLong(BeneficiaryStat::getSickCount).sum(),     totStyle);
            setNumCell(totRow, 9,  stats.stream().mapToLong(BeneficiaryStat::getCriticalCount).sum(), totStyle);
            setNumCell(totRow, 10, stats.stream().mapToLong(BeneficiaryStat::getRecoveredCount).sum(),totStyle);
            setNumCell(totRow, 11, stats.stream().mapToLong(BeneficiaryStat::getTreatCount).sum(),    totStyle);
            setNumCell(totRow, 12, stats.stream().map(BeneficiaryStat::getCurrentValue).reduce(BigDecimal.ZERO, BigDecimal::add).longValue(),  totStyle);
            setNumCell(totRow, 13, stats.stream().map(BeneficiaryStat::getSoldAmount).reduce(BigDecimal.ZERO, BigDecimal::add).longValue(),    totStyle);
            setNumCell(totRow, 14, stats.stream().map(BeneficiaryStat::getSickCost).reduce(BigDecimal.ZERO, BigDecimal::add).longValue(),      totStyle);
            setNumCell(totRow, 15, stats.stream().map(BeneficiaryStat::getTreatCost).reduce(BigDecimal.ZERO, BigDecimal::add).longValue(),     totStyle);
            setNumCell(totRow, 16, stats.stream().map(BeneficiaryStat::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add).longValue(),     totStyle);

            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);
            wb.write(response.getOutputStream());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EXCEL DOWNLOAD — Beneficiary animal detail
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/{supervisorId}/beneficiary/{beneficiaryId}/download/excel")
    public void downloadBeneficiaryExcel(@PathVariable UUID supervisorId,
                                         @PathVariable UUID beneficiaryId,
                                         HttpServletResponse response) throws IOException {

        UhagarariyeAborora sup = supervisorService.getById(supervisorId).orElse(null);
        AbaragizwaAmatungo ben = benRepository.findById(beneficiaryId).orElse(null);
        if (sup == null || ben == null) { response.sendError(404); return; }

        List<Livestock>          animals    = animalsOf(beneficiaryId);
        List<UUID>               animalIds  = animals.stream().map(Livestock::getId).collect(Collectors.toList());
        List<LivestockSick>      sickList   = animalIds.isEmpty() ? Collections.emptyList() : fetchSickByAnimalIds(animalIds);
        List<LivestockTreatment> treatments = animalIds.isEmpty() ? Collections.emptyList() : fetchTreatmentsByAnimalIds(animalIds);

        Map<UUID, Long> sickPerAnimal  = sickList.stream().filter(s -> s.getLivestock() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getId(), Collectors.counting()));
        Map<UUID, Long> treatPerAnimal = treatments.stream().filter(t -> t.getLivestock() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getId(), Collectors.counting()));
        Map<UUID, BigDecimal> sickCostPerAnimal = sickList.stream()
                .filter(s -> s.getLivestock() != null && s.getTreatmentCost() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getId(),
                        Collectors.reducing(BigDecimal.ZERO, LivestockSick::getTreatmentCost, BigDecimal::add)));
        Map<UUID, BigDecimal> treatCostPerAnimal = treatments.stream()
                .filter(t -> t.getLivestock() != null && t.getTreatmentCost() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getId(),
                        Collectors.reducing(BigDecimal.ZERO, LivestockTreatment::getTreatmentCost, BigDecimal::add)));

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"Beneficiary_" + ben.getLastName() + "_Animals.xlsx\"");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Animals Report");
            sheet.setDefaultColumnWidth(16);

            CellStyle titleStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font tf = wb.createFont();
            tf.setBold(true); tf.setFontHeightInPoints((short) 13);
            tf.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(tf);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font hf = wb.createFont();
            hf.setBold(true); hf.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(hf);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle altStyle = wb.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle numStyle = wb.createCellStyle();
            numStyle.setAlignment(HorizontalAlignment.RIGHT);

            int r = 0;
            Row t1 = sheet.createRow(r++);
            Cell c1 = t1.createCell(0);
            c1.setCellValue("RAPORO Y'UMURAGIZWA — " + ben.getFirstName() + " " + ben.getLastName());
            c1.setCellStyle(titleStyle);

            Row t2 = sheet.createRow(r++);
            t2.createCell(0).setCellValue(
                    "Umuhagarariye: " + sup.getFirstName() + " " + sup.getLastName()
                            + "  |  NID: " + nvl(ben.getNid())
                            + "  |  Generated: " + LocalDate.now().format(DATE_FMT));
            r++;

            Row headerRow = sheet.createRow(r++);
            String[] cols = {
                    "#", "Tag / Inomero", "Igitsina / Gender", "Imiterere / Status",
                    "Ubwoko / Category", "Yavutswe mu rugo / Born on Farm",
                    "Yarwaye / Sick (count)",
                    "Igiciro Indwara / Sick Cost (RWF)",
                    "Imiti / Treat. (count)",
                    "Igiciro Imiti / Treat. Cost (RWF)",
                    "IGICIRO CYOSE / Total Cost (RWF)",
                    "Agaciro / Current Value (RWF)"
            };
            for (int i = 0; i < cols.length; i++) {
                Cell hc = headerRow.createCell(i);
                hc.setCellValue(cols[i]);
                hc.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            boolean alt = false;
            BigDecimal grandSickCost  = BigDecimal.ZERO;
            BigDecimal grandTreatCost = BigDecimal.ZERO;
            BigDecimal grandTotalCost = BigDecimal.ZERO;
            BigDecimal grandCurrentValue = BigDecimal.ZERO;

            for (Livestock l : animals) {
                Row row = sheet.createRow(r++);
                CellStyle rs = alt ? altStyle : null;
                BigDecimal sc = sickCostPerAnimal.getOrDefault(l.getId(), BigDecimal.ZERO);
                BigDecimal tc = treatCostPerAnimal.getOrDefault(l.getId(), BigDecimal.ZERO);
                BigDecimal cv = l.getCurrentValue() != null ? l.getCurrentValue() : BigDecimal.ZERO;

                grandSickCost  = grandSickCost.add(sc);
                grandTreatCost = grandTreatCost.add(tc);
                grandTotalCost = grandTotalCost.add(sc.add(tc));
                grandCurrentValue = grandCurrentValue.add(cv);

                setCell(row, 0, String.valueOf(rowNum++), rs);
                setCell(row, 1, nvl(l.getTagNumber()), rs);
                setCell(row, 2, nvl(l.getGender()), rs);
                setCell(row, 3, nvl(l.getStatus()), rs);
                setCell(row, 4, l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : "—", rs);
                setCell(row, 5, l.getMother() != null ? "Yego/Yes" : "Oya/No", rs);
                setNumCell(row, 6,  sickPerAnimal.getOrDefault(l.getId(), 0L), numStyle);
                setNumCell(row, 7,  sc.longValue(), numStyle);
                setNumCell(row, 8,  treatPerAnimal.getOrDefault(l.getId(), 0L), numStyle);
                setNumCell(row, 9,  tc.longValue(), numStyle);
                setNumCell(row, 10, sc.add(tc).longValue(), numStyle);
                setNumCell(row, 11, cv.longValue(), numStyle);
                alt = !alt;
            }

            Row tot = sheet.createRow(r);
            CellStyle ts2 = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font totF = wb.createFont();
            totF.setBold(true); totF.setColor(IndexedColors.DARK_BLUE.getIndex());
            ts2.setFont(totF);
            ts2.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            ts2.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            setCell(tot, 0, "", ts2);
            setCell(tot, 1, "TOTAL", ts2);
            setCell(tot, 2, "", ts2);
            setCell(tot, 3, "", ts2);
            setCell(tot, 4, "", ts2);
            setCell(tot, 5, "", ts2);
            setNumCell(tot, 6,  (long) sickList.size(), ts2);
            setNumCell(tot, 7,  grandSickCost.longValue(), ts2);
            setNumCell(tot, 8,  (long) treatments.size(), ts2);
            setNumCell(tot, 9,  grandTreatCost.longValue(), ts2);
            setNumCell(tot, 10, grandTotalCost.longValue(), ts2);
            setNumCell(tot, 11, grandCurrentValue.longValue(), ts2);

            for (int i = 0; i < 5; i++) sheet.autoSizeColumn(i);
            wb.write(response.getOutputStream());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PDF HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private void addPdfMeta(Document doc, String title) {
        doc.addTitle(title);
        doc.addCreator("Animal Production System");
        doc.addCreationDate();
    }

    private void addKpiCell(PdfPTable table, String label, String value, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setPadding(10);
        cell.setBorder(Rectangle.NO_BORDER);
        Paragraph p = new Paragraph();
        Font vf = new Font(Font.HELVETICA, 16, Font.BOLD, Color.WHITE);
        Font lf = new Font(Font.HELVETICA, 7,  Font.NORMAL, Color.WHITE);
        p.add(new Chunk(value + "\n", vf));
        p.add(new Chunk(label, lf));
        cell.addElement(p);
        table.addCell(cell);
    }

    private void addPdfCell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", font));
        cell.setPadding(5);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }

    private void addPdfFooter(Document doc, Font font) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        Paragraph footer = new Paragraph(
                "Animal Production System — Confidential / Ibanga | " + LocalDate.now().format(DATE_FMT),
                new Font(Font.HELVETICA, 7, Font.ITALIC, Color.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EXCEL HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "—");
        if (style != null) c.setCellStyle(style);
    }

    private void setNumCell(Row row, int col, long value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MISC HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private String nvl(String s) { return s != null && !s.isBlank() ? s : "—"; }

    private String formatRwf(BigDecimal v) {
        if (v == null) return "0 RWF";
        return String.format("%,.0f RWF", v);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  INNER CLASSES
    // ═════════════════════════════════════════════════════════════════════════

    public static class SupervisorSummary {
        private final UhagarariyeAborora supervisor;
        private final long               beneficiaryCount;

        public SupervisorSummary(UhagarariyeAborora s, long c) { supervisor = s; beneficiaryCount = c; }

        public UUID   getId()               { return supervisor.getId(); }
        public String getFirstName()        { return supervisor.getFirstName(); }
        public String getLastName()         { return supervisor.getLastName(); }
        public String getPhone()            { return supervisor.getPhone(); }
        public String getEmail()            { return supervisor.getEmail(); }
        public long   getBeneficiaryCount() { return beneficiaryCount; }
    }

    public static class BeneficiaryStat {
        private final AbaragizwaAmatungo beneficiary;
        private final long   totalAnimals, activeAnimals, soldAnimals, bornOnFarm;
        private final BigDecimal currentValue, soldAmount;
        private final long   sickCount, criticalCount, recoveredCount, treatCount;
        private final BigDecimal treatCost, sickCost, totalCost;

        public BeneficiaryStat(AbaragizwaAmatungo b,
                               long totalAnimals, long activeAnimals, long soldAnimals, long bornOnFarm,
                               BigDecimal currentValue, BigDecimal soldAmount,
                               long sickCount, long criticalCount, long recoveredCount,
                               long treatCount, BigDecimal treatCost, BigDecimal sickCost, BigDecimal totalCost) {
            this.beneficiary    = b;
            this.totalAnimals   = totalAnimals;
            this.activeAnimals  = activeAnimals;
            this.soldAnimals    = soldAnimals;
            this.bornOnFarm     = bornOnFarm;
            this.currentValue   = currentValue;
            this.soldAmount     = soldAmount;
            this.sickCount      = sickCount;
            this.criticalCount  = criticalCount;
            this.recoveredCount = recoveredCount;
            this.treatCount     = treatCount;
            this.treatCost      = treatCost;
            this.sickCost       = sickCost;
            this.totalCost      = totalCost;
        }

        public AbaragizwaAmatungo getBeneficiary()  { return beneficiary; }
        public long getTotalAnimals()               { return totalAnimals; }
        public long getActiveAnimals()              { return activeAnimals; }
        public long getSoldAnimals()                { return soldAnimals; }
        public long getBornOnFarm()                 { return bornOnFarm; }
        public BigDecimal getCurrentValue()         { return currentValue; }
        public BigDecimal getSoldAmount()           { return soldAmount; }
        public long getSickCount()                  { return sickCount; }
        public long getCriticalCount()              { return criticalCount; }
        public long getRecoveredCount()             { return recoveredCount; }
        public long getTreatCount()                 { return treatCount; }
        public BigDecimal getTreatCost()            { return treatCost; }
        public BigDecimal getSickCost()             { return sickCost; }
        public BigDecimal getTotalCost()            { return totalCost; }
    }
}
