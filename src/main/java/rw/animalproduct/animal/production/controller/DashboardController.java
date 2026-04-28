package rw.animalproduct.animal.production.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickHistoryRepository;
import rw.animalproduct.animal.production.services.BeneficiaryService;
import rw.animalproduct.animal.production.services.LivestockAbortionService;
import rw.animalproduct.animal.production.services.LivestockBirthService;
import rw.animalproduct.animal.production.services.LivestockDeathService;
import rw.animalproduct.animal.production.services.LivestockSaleService;
import rw.animalproduct.animal.production.services.LivestockSickService;
import rw.animalproduct.animal.production.services.LivestockTreatmentService;
import rw.animalproduct.animal.production.services.RepresentativeService;
import rw.animalproduct.animal.production.services.UsersService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final RepresentativeService      representativesAbororaService;
    private final BeneficiaryService       beneficiariesAmatungoService;
    private final UsersService                   usersService;
    private final LivestockRepository            livestockRepository;
    private final LivestockBirthService          birthService;
    private final LivestockTreatmentService      treatmentService;
    private final LivestockSickService           sickService;
    private final LivestockAbortionService       abortionService;
    private final LivestockSaleService           saleService;
    private final LivestockDeathService          deathService;
    private final LivestockSickHistoryRepository sickHistoryRepository;

    public DashboardController(RepresentativeService representativesAbororaService,
                               BeneficiaryService beneficiariesAmatungoService,
                               UsersService usersService,
                               LivestockRepository livestockRepository,
                               LivestockBirthService birthService,
                               LivestockTreatmentService treatmentService,
                               LivestockSickService sickService,
                               LivestockAbortionService abortionService,
                               LivestockSaleService saleService,
                               LivestockDeathService deathService,
                               LivestockSickHistoryRepository sickHistoryRepository) {
        this.representativesAbororaService = representativesAbororaService;
        this.beneficiariesAmatungoService = beneficiariesAmatungoService;
        this.usersService              = usersService;
        this.livestockRepository       = livestockRepository;
        this.birthService              = birthService;
        this.treatmentService          = treatmentService;
        this.sickService               = sickService;
        this.abortionService           = abortionService;
        this.saleService               = saleService;
        this.deathService              = deathService;
        this.sickHistoryRepository     = sickHistoryRepository;
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("username", email);
        model.addAttribute("userInitial", email.substring(0, 1).toUpperCase());
        model.addAttribute("isSuperAdmin", true);

        // ── Representatives & Beneficiaries ──────────────────────────
        model.addAttribute("totalRepresentatives", representativesAbororaService.getAll().size());
        model.addAttribute("totalBeneficiaries",   beneficiariesAmatungoService.getAll().size());

        // ── Users ─────────────────────────────────────────────────────
        List<Users> allUsers   = usersService.getAllUsers();
        long activeUsers       = allUsers.stream().filter(Users::isActive).count();
        long inactiveUsers     = allUsers.stream().filter(u -> !u.isActive()).count();
        model.addAttribute("totalUsers",    allUsers.size());
        model.addAttribute("activeUsers",   activeUsers);
        model.addAttribute("inactiveUsers", inactiveUsers);

        // ── Livestock count ───────────────────────────────────────────
        model.addAttribute("totalLivestock", livestockRepository.count());

        LocalDate today = LocalDate.now();
        int       year  = today.getYear();

        // ════════════════════════════════════════════════════════════
        // BIRTH REPORT DATA
        // ════════════════════════════════════════════════════════════
        List<LivestockBirth> allBirths = birthService.getAll();
        model.addAttribute("totalBirths", allBirths.size());

        // ✅ FIX: These were missing from the model — JS renderBirthReport() needs them
        long totalMothers = allBirths.stream()
                .map(b -> b.getLivestock() != null ? b.getLivestock().getId() : null)
                .filter(id -> id != null).distinct().count();
        model.addAttribute("totalMothers", totalMothers);

        long totalLinkedChildren = allBirths.stream()
                .mapToLong(b -> b.getChildren() != null ? b.getChildren().size() : 0).sum();
        model.addAttribute("totalLinkedChildren", totalLinkedChildren);

        double avg = allBirths.isEmpty() ? 0 :
                allBirths.stream().mapToInt(b -> b.getOffspringCount() != null ? b.getOffspringCount() : 0)
                        .average().orElse(0);
        model.addAttribute("avgOffspringPerBirth", Math.round(avg));
        model.addAttribute("recentBirths", allBirths);

        // ════════════════════════════════════════════════════════════
        // TREATMENT REPORT DATA
        // ════════════════════════════════════════════════════════════
        List<LivestockTreatment> treatmentList = treatmentService.getAll();
        model.addAttribute("treatmentList",   treatmentList);
        model.addAttribute("totalTreatments", (long) treatmentList.size());

        BigDecimal totalTreatmentCost = treatmentList.stream()
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalTreatmentCost", totalTreatmentCost);

        long treatmentsThisMonth = treatmentList.stream()
                .filter(t -> t.getTreatmentDate() != null
                        && t.getTreatmentDate().getMonthValue() == today.getMonthValue()
                        && t.getTreatmentDate().getYear()        == today.getYear()).count();
        model.addAttribute("treatmentsThisMonth", treatmentsThisMonth);

        String mostTreatedAnimalTag = treatmentList.stream()
                .filter(t -> t.getLivestock() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostTreatedAnimalTag", mostTreatedAnimalTag);

        // ════════════════════════════════════════════════════════════
        // SICK LIVESTOCK DATA
        // ════════════════════════════════════════════════════════════
        List<LivestockSick> sickList = sickService.getAll();

        model.addAttribute("sickRecords", sickList);
        model.addAttribute("sickList",  sickList);
        model.addAttribute("totalSick", (long) sickList.size());

        model.addAttribute("sickRecord", new LivestockSick());

        List<Livestock> availableLivestock = livestockRepository.findAll().stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !"SOLD".equals(ls.getStatus()))
                .collect(Collectors.toList());
        model.addAttribute("livestockList", availableLivestock);

        long currentlySick = sickList.stream()
                .filter(s -> s.getStatus() != null && !s.getStatus().name().equals("RECOVERED")).count();
        model.addAttribute("currentlySick", currentlySick);

        // ✅ FIX: criticalCount was computed but never added to model — JS renderSickReport() needs it
        long criticalCount = sickList.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("CRITICAL")).count();
        model.addAttribute("criticalCount", criticalCount);

        long recoveringCount = sickList.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERING")).count();
        model.addAttribute("recoveringCount", recoveringCount);

        // ✅ FIX: recoveredCount was computed but never added to model — JS renderSickReport() needs it
        long recoveredCount = sickList.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERED")).count();
        model.addAttribute("recoveredCount", recoveredCount);

        long sickThisMonth = sickList.stream()
                .filter(s -> s.getReportedDate() != null
                        && s.getReportedDate().getMonthValue() == today.getMonthValue()
                        && s.getReportedDate().getYear()        == today.getYear()).count();
        model.addAttribute("sickThisMonth", sickThisMonth);

        String mostSickAnimalTag = sickList.stream()
                .filter(s -> s.getLivestock() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostSickAnimalTag", mostSickAnimalTag);

        // Calculate totalSickTreatmentCost from LivestockTreatment table
        BigDecimal totalSickTreatmentCost = treatmentList.stream()
                .filter(t -> t.getSickLivestock() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSickTreatmentCost", totalSickTreatmentCost);

        long lifeThreatCount = sickList.stream()
                .filter(s -> s.getSeverityLevel() != null
                        && s.getSeverityLevel().name().equals("LIFE_THREATENING")).count();
        model.addAttribute("lifeThreatCount", lifeThreatCount);

        // ════════════════════════════════════════════════════════════
        // SICK HISTORY DATA
        // ════════════════════════════════════════════════════════════
        long yearSickCount      = sickHistoryRepository.countSickByYear(year);
        long yearCriticalCount  = sickHistoryRepository.countCriticalByYear(year);
        long yearRecoveredCount = sickHistoryRepository.countRecoveredByYear(year);
        model.addAttribute("yearSickCount",      yearSickCount);
        model.addAttribute("yearCriticalCount",  yearCriticalCount);
        model.addAttribute("yearRecoveredCount", yearRecoveredCount);

        long recoveryRate = yearSickCount > 0
                ? Math.round((yearRecoveredCount * 100.0) / yearSickCount)
                : 100L;
        model.addAttribute("recoveryRate", recoveryRate);

        LocalDateTime historyFrom = LocalDateTime.now().minusDays(30);
        LocalDateTime historyTo   = LocalDateTime.now();

        List<LivestockSickHistory> recentHistory = sickHistoryRepository
                .findByDateRange(historyFrom, historyTo);

        model.addAttribute("totalHistoryEvents30Days", recentHistory.size());

        List<LivestockSickHistory> recentHistoryTop20 = recentHistory.stream()
                .limit(20)
                .collect(Collectors.toList());
        model.addAttribute("recentSickHistory", recentHistoryTop20);

        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        List<LivestockSickHistory> criticalThisMonth =
                sickHistoryRepository.findCriticalCasesByDateRange(monthStart, historyTo);
        model.addAttribute("criticalThisMonthCount", criticalThisMonth.size());

        List<LivestockSickHistory> recoveredThisMonth =
                sickHistoryRepository.findRecoveredCasesByDateRange(monthStart, historyTo);
        model.addAttribute("recoveredThisMonthCount", recoveredThisMonth.size());

        // ════════════════════════════════════════════════════════════
        // ABORTION REPORT DATA
        // ════════════════════════════════════════════════════════════
        List<LivestockAbortion> abortionList = abortionService.getAll();
        model.addAttribute("abortionList",   abortionList);
        model.addAttribute("totalAbortions", (long) abortionList.size());

        // ✅ FIX: totalAnimalsAffected was computed but never added to model — JS renderAbortionReport() needs it
        long totalAnimalsAffected = abortionList.stream()
                .filter(a -> a.getLivestock() != null)
                .map(a -> a.getLivestock().getId()).distinct().count();
        model.addAttribute("totalAnimalsAffected", totalAnimalsAffected);

        String mostAffectedAnimalTag = abortionList.stream()
                .filter(a -> a.getLivestock() != null)
                .collect(Collectors.groupingBy(a -> a.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostAffectedAnimalTag", mostAffectedAnimalTag);

        long abortionsThisMonth = abortionList.stream()
                .filter(a -> a.getAbortionDate() != null
                        && a.getAbortionDate().getMonthValue() == today.getMonthValue()
                        && a.getAbortionDate().getYear()        == today.getYear()).count();
        model.addAttribute("abortionsThisMonth", abortionsThisMonth);

        // ════════════════════════════════════════════════════════════
        // SALES REPORT DATA
        // ════════════════════════════════════════════════════════════
        List<LivestockSale> salesList = saleService.getAll();
        model.addAttribute("salesList",   salesList);
        model.addAttribute("totalSales",  (long) salesList.size());

        BigDecimal totalSaleRevenue = salesList.stream()
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSaleRevenue", totalSaleRevenue);

        String mostSoldAnimalTag = salesList.stream()
                .filter(s -> s.getLivestock() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostSoldAnimalTag", mostSoldAnimalTag);

        long salesThisMonth = salesList.stream()
                .filter(s -> s.getSaleDate() != null
                        && s.getSaleDate().getMonthValue() == today.getMonthValue()
                        && s.getSaleDate().getYear()        == today.getYear()).count();
        model.addAttribute("salesThisMonth", salesThisMonth);

        // ════════════════════════════════════════════════════════════
        // DEATHS REPORT DATA
        // ════════════════════════════════════════════════════════════
        List<LivestockDeath> deathsList = deathService.getAll();
        model.addAttribute("deathsList",   deathsList);
        model.addAttribute("totalDeaths",  (long) deathsList.size());

        long deathsThisMonth = deathsList.stream()
                .filter(d -> d.getDeathDate() != null
                        && d.getDeathDate().getMonthValue() == today.getMonthValue()
                        && d.getDeathDate().getYear()        == today.getYear()).count();
        model.addAttribute("deathsThisMonth", deathsThisMonth);

        String mostCommonCause = deathsList.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .collect(Collectors.groupingBy(LivestockDeath::getCauseOfDeath, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostCommonCause", mostCommonCause);

        // ✅ FIX: distinctCausesCount was computed but never added to model — JS renderDeathsReport() needs it
        long distinctCausesCount = deathsList.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .map(LivestockDeath::getCauseOfDeath).distinct().count();
        model.addAttribute("distinctCausesCount", distinctCausesCount);

        return "admin-dashboard";
    }

    @GetMapping("/user/dashboard")
    public String userDashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("username", email);
        model.addAttribute("userInitial", email.substring(0, 1).toUpperCase());
        model.addAttribute("isSuperAdmin", false);
        return "user-dashboard";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }

    @GetMapping("/livestock/summary-report")
    public String summaryReport(Model model) {
        LocalDate today = LocalDate.now();

        // ── Active animals with current_value ─────────────────────────
        List<Livestock> activeAnimals = livestockRepository.findByStatus(Livestock.STATUS_ACTIVE);
        BigDecimal totalActiveValue = activeAnimals.stream()
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("activeAnimals", activeAnimals);
        model.addAttribute("totalActiveValue", totalActiveValue);

        // ── Treatment spend ───────────────────────────────────────────
        List<LivestockTreatment> allTreatments = treatmentService.getAll();
        BigDecimal totalTreatmentSpend = allTreatments.stream()
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalTreatmentSpend", totalTreatmentSpend);
        model.addAttribute("totalTreatmentsCount", allTreatments.size());

        // ── Sick care spend (from treatments linked to sick episodes) ──
        BigDecimal totalSickCareSpend = allTreatments.stream()
                .filter(t -> t.getSickLivestock() != null && t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCareSpend = totalTreatmentSpend;
        model.addAttribute("totalCareSpend", totalCareSpend);
        model.addAttribute("totalSickCareSpend", totalSickCareSpend);

        // ── Sales ─────────────────────────────────────────────────────
        List<LivestockSale> allSales = saleService.getAll();
        BigDecimal totalSaleRevenue = allSales.stream()
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSaleRevenue", totalSaleRevenue);
        model.addAttribute("totalSalesCount", allSales.size());

        // ── Status counts ─────────────────────────────────────────────
        List<Livestock> all = livestockRepository.findAll();
        long countActive   = all.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long countSold     = all.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();
        long countDead     = all.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count();
        long countSick     = all.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count();
        long countPregnant = all.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count();
        model.addAttribute("countActive",   countActive);
        model.addAttribute("countSold",     countSold);
        model.addAttribute("countDead",     countDead);
        model.addAttribute("countSick",     countSick);
        model.addAttribute("countPregnant", countPregnant);
        model.addAttribute("totalAll",      (long) all.size());

        // ── Generated timestamp ───────────────────────────────────────
        model.addAttribute("generatedAt", today);

        // ── Pre-compute net position for the template ─────────────────
        BigDecimal netPosition = totalSaleRevenue.subtract(totalCareSpend);
        model.addAttribute("netPosition",    netPosition);
        model.addAttribute("isProfit",       netPosition.compareTo(BigDecimal.ZERO) >= 0);
        model.addAttribute("netPositionAbs", netPosition.abs());

        return "livestock-summary-report";
    }
}
