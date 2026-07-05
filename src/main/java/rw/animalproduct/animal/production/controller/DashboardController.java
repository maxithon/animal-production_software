package rw.animalproduct.animal.production.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.BuyerRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickHistoryRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;
import rw.animalproduct.animal.production.services.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final RepresentativeService representativesAbororaService;
    private final BeneficiaryService beneficiariesAmatungoService;
    private final UsersService usersService;
    private final LivestockRepository livestockRepository;
    private final LivestockBirthService birthService;
    private final LivestockTreatmentService treatmentService;
    private final LivestockSickService sickService;
    private final LivestockAbortionService abortionService;
    private final LivestockSaleService saleService;
    private final LivestockDeathService deathService;
    private final LivestockSickHistoryRepository sickHistoryRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final BuyerRepository buyerRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int DAYS_FOR_ALERTS = 7;

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
                               LivestockSickHistoryRepository sickHistoryRepository,
                               VeterinarianRepository veterinarianRepository,
                               BuyerRepository buyerRepository) {
        this.representativesAbororaService = representativesAbororaService;
        this.beneficiariesAmatungoService = beneficiariesAmatungoService;
        this.usersService = usersService;
        this.livestockRepository = livestockRepository;
        this.birthService = birthService;
        this.treatmentService = treatmentService;
        this.sickService = sickService;
        this.abortionService = abortionService;
        this.saleService = saleService;
        this.deathService = deathService;
        this.sickHistoryRepository = sickHistoryRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.buyerRepository = buyerRepository;
    }

    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Role helpers ─────────────────────────────────────────────────────────
    // These are the actual server-side gate. SecurityConfig blocks the URLs;
    // these helpers let shared endpoints (like /livestock/summary-report)
    // scope *data* differently depending on who is asking.

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** Regular users only ever see livestock tied to their own beneficiaryId. */
    private List<Livestock> scopedLivestock(Authentication authentication) {
        if (isAdmin(authentication)) {
            return safeList(livestockRepository.findAll());
        }
        Users current = usersService.getUserByEmail(authentication.getName()).orElse(null);
        if (current == null || current.getBeneficiaryId() == null) {
            return Collections.emptyList();
        }
        return safeList(livestockRepository.findByBeneficiaryId(current.getBeneficiaryId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String toJson(Object obj) {
        try { return MAPPER.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ── Category breakdown builder ───────────────────────────────────────────

    private <T> Map<String, Long> categoryCountFromList(List<T> items,
                                                        java.util.function.Function<T, String> catExtractor) {
        Map<String, Long> raw = new HashMap<>();
        for (T item : safeList(items)) {
            String cat = catExtractor.apply(item);
            if (cat != null && !cat.isBlank()) {
                raw.merge(cat, 1L, Long::sum);
            }
        }
        return raw.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String catName(Livestock ls) {
        if (ls == null) return null;
        LivestockCategory cat = ls.getLivestockCategory();
        return cat != null ? cat.getName() : null;
    }

    // ── Build map helpers for JSON serialisation ──────────────────────────────

    private List<Map<String, Object>> buildBirthMaps(List<LivestockBirth> births) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockBirth b : safeList(births)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("birthDate", formatDate(b.getBirthDate()));
            m.put("offspringCount", b.getOffspringCount());
            m.put("offspringGender", b.getOffspringGender());
            Map<String, Object> ls = new LinkedHashMap<>();
            if (b.getLivestock() != null) {
                ls.put("tagNumber", b.getLivestock().getTagNumber());
                ls.put("id", b.getLivestock().getId());
                if (b.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", b.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            m.put("children", b.getChildren() != null ? b.getChildren().size() : 0);
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildSickMaps(List<LivestockSick> records) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockSick s : safeList(records)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reportedDate", formatDate(s.getReportedDate()));
            m.put("symptoms", s.getSymptoms());
            m.put("diagnosis", s.getDiagnosis());
            m.put("vetName", s.getVetName());
            m.put("status", s.getStatus() != null ? s.getStatus().name() : null);
            m.put("severityLevel", s.getSeverityLevel() != null ? s.getSeverityLevel().name() : null);
            Map<String, Object> ls = new LinkedHashMap<>();
            if (s.getLivestock() != null) {
                ls.put("tagNumber", s.getLivestock().getTagNumber());
                ls.put("gender", s.getLivestock().getGender());
                if (s.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", s.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            if (s.getVeterinarian() != null) {
                Map<String, Object> vet = new LinkedHashMap<>();
                vet.put("fullName", s.getVeterinarian().getFullName());
                m.put("veterinarian", vet);
            }
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildSaleMaps(List<LivestockSale> sales) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockSale s : safeList(sales)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("saleDate", formatDate(s.getSaleDate()));
            m.put("salePrice", s.getSalePrice() != null ? s.getSalePrice().doubleValue() : null);
            m.put("saleReason", s.getSaleReason());
            Map<String, Object> ls = new LinkedHashMap<>();
            if (s.getLivestock() != null) {
                ls.put("tagNumber", s.getLivestock().getTagNumber());
                if (s.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", s.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            Map<String, Object> buyer = new LinkedHashMap<>();
            if (s.getBuyer() != null) {
                buyer.put("name", s.getBuyer().getBuyerName());
                buyer.put("phone", s.getBuyer().getBuyerPhone());
            }
            m.put("buyer", buyer);
            m.put("buyerName", s.getBuyer() != null ? s.getBuyer().getBuyerName() : "Unknown Buyer");
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildDeathMaps(List<LivestockDeath> deaths) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockDeath d : safeList(deaths)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deathDate", formatDate(d.getDeathDate()));
            m.put("causeOfDeath", d.getCauseOfDeath());
            Map<String, Object> ls = new LinkedHashMap<>();
            if (d.getLivestock() != null) {
                ls.put("tagNumber", d.getLivestock().getTagNumber());
                ls.put("gender", d.getLivestock().getGender());
                if (d.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", d.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            list.add(m);
        }
        return list;
    }

    // ── Statistics (admin-wide, unfiltered) ────────────────────────────────────

    private DashboardStatistics calculateStatistics() {
        DashboardStatistics stats = new DashboardStatistics();
        List<LivestockBirth> births           = safeList(birthService.getAll());
        List<LivestockSick> sickRecords       = safeList(sickService.getAll());
        List<LivestockSale> sales             = safeList(saleService.getAll());
        List<LivestockDeath> deaths           = safeList(deathService.getAll());
        List<LivestockTreatment> treatments   = safeList(treatmentService.getAll());
        List<LivestockAbortion> abortions     = safeList(abortionService.getAll());
        List<Livestock> livestock             = safeList(livestockRepository.findAll());
        List<Users> users                     = safeList(usersService.getAllUsers());

        stats.setTotalLivestock((long) livestock.size());
        stats.setActiveLivestock(livestock.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count());
        stats.setPregnantLivestock(livestock.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count());
        stats.setSickLivestock(livestock.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count());
        stats.setDeadLivestock(livestock.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count());
        stats.setSoldLivestock(livestock.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count());

        stats.setTotalBirths((long) births.size());
        stats.setTotalMothers(births.stream()
                .map(b -> b.getLivestock() != null ? b.getLivestock().getId() : null)
                .filter(Objects::nonNull).distinct().count());
        stats.setTotalChildren(births.stream()
                .mapToLong(b -> b.getChildren() != null ? b.getChildren().size() : 0).sum());
        double avgOffspring = births.isEmpty() ? 0 :
                births.stream().mapToInt(b -> b.getOffspringCount() != null ? b.getOffspringCount() : 0)
                        .average().orElse(0);
        stats.setAverageOffspringPerBirth(Math.round(avgOffspring));

        stats.setTotalSick((long) sickRecords.size());
        stats.setCurrentlySick(sickRecords.stream()
                .filter(s -> s.getStatus() != null && !s.getStatus().name().equals("RECOVERED")).count());
        stats.setCriticalCount(sickRecords.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("CRITICAL")).count());
        stats.setRecoveringCount(sickRecords.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERING")).count());
        stats.setRecoveredCount(sickRecords.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERED")).count());

        stats.setTotalTreatments((long) treatments.size());
        stats.setTotalTreatmentCost(treatments.stream()
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.setUnpaidTreatmentCount(treatments.stream()
                .filter(t -> t.getIsPaid() != null && !t.getIsPaid()).count());
        stats.setOngoingTreatmentCount(treatments.stream()
                .filter(t -> t.getTreatmentStatus() != null
                        && t.getTreatmentStatus().name().equalsIgnoreCase("ONGOING")).count());

        stats.setTotalSales((long) sales.size());
        stats.setTotalSaleRevenue(sales.stream()
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        stats.setTotalDeaths((long) deaths.size());
        stats.setTotalAbortions((long) abortions.size());

        stats.setTotalUsers((long) users.size());
        stats.setActiveUsers(users.stream().filter(Users::isActive).count());
        stats.setInactiveUsers(users.stream().filter(u -> !u.isActive()).count());

        stats.setTotalBeneficiaries((long) safeList(beneficiariesAmatungoService.getAll()).size());
        stats.setTotalRepresentatives((long) safeList(representativesAbororaService.getAll()).size());

        BigDecimal netPosition = safeBigDecimal(stats.getTotalSaleRevenue())
                .subtract(safeBigDecimal(stats.getTotalTreatmentCost()));
        stats.setNetPosition(netPosition);
        stats.setProfit(netPosition.compareTo(BigDecimal.ZERO) >= 0);
        return stats;
    }

    private MonthlyTrends calculateMonthlyTrends(int year) {
        MonthlyTrends trends = new MonthlyTrends();
        int[] births = new int[12], sales = new int[12], deaths = new int[12], treatments = new int[12];
        double[] revenue = new double[12], costs = new double[12];

        for (LivestockBirth b : safeList(birthService.getAll()))
            if (b.getBirthDate() != null && b.getBirthDate().getYear() == year)
                births[b.getBirthDate().getMonthValue() - 1]++;

        for (LivestockSale s : safeList(saleService.getAll()))
            if (s.getSaleDate() != null && s.getSaleDate().getYear() == year) {
                int idx = s.getSaleDate().getMonthValue() - 1;
                sales[idx]++;
                if (s.getSalePrice() != null) revenue[idx] += s.getSalePrice().doubleValue();
            }

        for (LivestockDeath d : safeList(deathService.getAll()))
            if (d.getDeathDate() != null && d.getDeathDate().getYear() == year)
                deaths[d.getDeathDate().getMonthValue() - 1]++;

        for (LivestockTreatment t : safeList(treatmentService.getAll()))
            if (t.getTreatmentDate() != null && t.getTreatmentDate().getYear() == year) {
                int idx = t.getTreatmentDate().getMonthValue() - 1;
                treatments[idx]++;
                if (t.getTreatmentCost() != null) costs[idx] += t.getTreatmentCost().doubleValue();
            }

        trends.setMonthlyBirths(births);
        trends.setMonthlySales(sales);
        trends.setMonthlyDeaths(deaths);
        trends.setMonthlyTreatments(treatments);
        trends.setMonthlyRevenue(revenue);
        trends.setMonthlyCosts(costs);
        return trends;
    }

    private List<Map<String, String>> generateAlerts() {
        List<Map<String, String>> alertList = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate weekFromNow = today.plusDays(DAYS_FOR_ALERTS);

        for (LivestockTreatment t : safeList(treatmentService.getAll())) {
            if (t.getNextTreatmentDate() != null
                    && !t.getNextTreatmentDate().isBefore(today)
                    && t.getNextTreatmentDate().isBefore(weekFromNow)) {
                Map<String, String> alert = new LinkedHashMap<>();
                alert.put("type", "TREATMENT"); alert.put("severity", "WARNING");
                alert.put("message", "Treatment due for "
                        + (t.getLivestock() != null ? t.getLivestock().getTagNumber() : "Unknown")
                        + " on " + formatDate(t.getNextTreatmentDate()));
                alertList.add(alert);
            }
        }
        for (Livestock l : safeList(livestockRepository.findAll())) {
            if (Livestock.STATUS_PREGNANT.equals(l.getStatus())
                    && l.getExpectedDueDate() != null
                    && !l.getExpectedDueDate().isBefore(today)
                    && l.getExpectedDueDate().isBefore(weekFromNow)) {
                Map<String, String> alert = new LinkedHashMap<>();
                alert.put("type", "BIRTH"); alert.put("severity", "INFO");
                alert.put("message", "Expected birth for " + l.getTagNumber()
                        + " on " + formatDate(l.getExpectedDueDate()));
                alertList.add(alert);
            }
        }
        for (LivestockSick s : safeList(sickService.getAll())) {
            if (s.getStatus() != LivestockSick.SickStatus.RECOVERED
                    && s.getReportedDate() != null
                    && s.getReportedDate().isBefore(today.minusDays(3))) {
                Map<String, String> alert = new LinkedHashMap<>();
                alert.put("type", "SICK"); alert.put("severity", "CRITICAL");
                alert.put("message", "Animal "
                        + (s.getLivestock() != null ? s.getLivestock().getTagNumber() : "Unknown")
                        + " sick since " + formatDate(s.getReportedDate()));
                alertList.add(alert);
            }
        }
        for (LivestockTreatment t : safeList(treatmentService.getAll())) {
            if (t.getNextTreatmentDate() != null
                    && t.getNextTreatmentDate().isBefore(today)
                    && t.getTreatmentStatus() != null
                    && !t.getTreatmentStatus().name().equalsIgnoreCase("COMPLETED")) {
                Map<String, String> alert = new LinkedHashMap<>();
                alert.put("type", "OVERDUE_TREATMENT"); alert.put("severity", "CRITICAL");
                alert.put("message", "Treatment OVERDUE for "
                        + (t.getLivestock() != null ? t.getLivestock().getTagNumber() : "Unknown"));
                alertList.add(alert);
            }
        }
        return alertList;
    }

    // ── ADMIN DASHBOARD ────────────────────────────────────────────────────────
    // Enforced twice: SecurityConfig blocks /admin/** for non-admins at the URL
    // level, and @PreAuthorize blocks it here even if that mapping is ever
    // reused elsewhere. Belt and suspenders.

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        LocalDate today = LocalDate.now();
        int year = today.getYear();

        model.addAttribute("username", email);
        model.addAttribute("userInitial", email.substring(0, 1).toUpperCase());
        model.addAttribute("isSuperAdmin", true);

        DashboardStatistics stats = calculateStatistics();

        // ── Livestock totals ─────────────────────────────────────────────────
        model.addAttribute("totalLivestock",      stats.getTotalLivestock());
        model.addAttribute("totalMothers",         stats.getTotalMothers());
        model.addAttribute("totalBirths",          stats.getTotalBirths());
        model.addAttribute("totalChildren",        stats.getTotalChildren());
        model.addAttribute("avgOffspringPerBirth", stats.getAverageOffspringPerBirth());
        model.addAttribute("activeCount",          stats.getActiveLivestock());
        model.addAttribute("soldCount",            stats.getSoldLivestock());
        model.addAttribute("deadCount",            stats.getDeadLivestock());
        model.addAttribute("sickCount",            stats.getSickLivestock());
        model.addAttribute("pregnantCount",        stats.getPregnantLivestock());

        List<Livestock> allLivestock = safeList(livestockRepository.findAll());
        Map<String, Long> categoryBreakdown = categoryCountFromList(
                allLivestock, ls -> catName(ls));
        model.addAttribute("categoryBreakdown", toJson(categoryBreakdown));

        // ── Users / People ───────────────────────────────────────────────────
        model.addAttribute("totalUsers",           stats.getTotalUsers());
        model.addAttribute("activeUsers",          stats.getActiveUsers());
        model.addAttribute("inactiveUsers",        stats.getInactiveUsers());
        model.addAttribute("totalRepresentatives", stats.getTotalRepresentatives());
        model.addAttribute("totalBeneficiaries",   stats.getTotalBeneficiaries());

        long vetCount   = safeList(veterinarianRepository.findByIsDeletedFalseAndIsActiveTrue()).size();
        long buyerCount = buyerRepository.countActiveBuyers();
        model.addAttribute("vetCount",   vetCount);
        model.addAttribute("buyerCount", buyerCount);

        // ── Births ───────────────────────────────────────────────────────────
        List<LivestockBirth> allBirths = safeList(birthService.getAll());
        model.addAttribute("recentBirths", toJson(buildBirthMaps(allBirths)));

        Map<String, Long> birthBreakdown = categoryCountFromList(
                allBirths, b -> b.getLivestock() != null ? catName(b.getLivestock()) : null);
        model.addAttribute("birthBreakdown", toJson(birthBreakdown));

        // ── Treatments ───────────────────────────────────────────────────────
        List<LivestockTreatment> treatmentList = safeList(treatmentService.getAll());
        model.addAttribute("treatmentList",        treatmentList);
        model.addAttribute("totalTreatments",      stats.getTotalTreatments());
        model.addAttribute("totalTreatmentCost",   stats.getTotalTreatmentCost());
        model.addAttribute("unpaidTreatmentCount", stats.getUnpaidTreatmentCount());
        model.addAttribute("ongoingTreatmentCount",stats.getOngoingTreatmentCount());

        long treatmentsThisMonth = treatmentList.stream()
                .filter(t -> t.getTreatmentDate() != null
                        && t.getTreatmentDate().getMonthValue() == today.getMonthValue()
                        && t.getTreatmentDate().getYear() == year).count();
        model.addAttribute("treatmentsThisMonth", treatmentsThisMonth);

        String mostTreatedTag = treatmentList.stream()
                .filter(t -> t.getLivestock() != null && t.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostTreatedAnimalTag", mostTreatedTag);

        Map<String, Long> treatBreakdown = categoryCountFromList(
                treatmentList, t -> t.getLivestock() != null ? catName(t.getLivestock()) : null);
        model.addAttribute("treatBreakdown", toJson(treatBreakdown));

        // ── Sick Records ─────────────────────────────────────────────────────
        List<LivestockSick> sickList = safeList(sickService.getAll());
        model.addAttribute("sickRecords",     sickList);
        model.addAttribute("sickList",        toJson(buildSickMaps(sickList)));
        model.addAttribute("totalSick",       stats.getTotalSick());
        model.addAttribute("currentlySick",   stats.getCurrentlySick());
        model.addAttribute("criticalCount",   stats.getCriticalCount());
        model.addAttribute("recoveringCount", stats.getRecoveringCount());
        model.addAttribute("recoveredCount",  stats.getRecoveredCount());

        long sickThisMonth = sickList.stream()
                .filter(s -> s.getReportedDate() != null
                        && s.getReportedDate().getMonthValue() == today.getMonthValue()
                        && s.getReportedDate().getYear() == year).count();
        model.addAttribute("sickThisMonth", sickThisMonth);

        String mostSickTag = sickList.stream()
                .filter(s -> s.getLivestock() != null && s.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostSickAnimalTag", mostSickTag);

        BigDecimal totalSickTreatmentCost = treatmentList.stream()
                .filter(t -> t.getSickLivestock() != null && t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSickTreatmentCost", totalSickTreatmentCost);

        long lifeThreatCount = sickList.stream()
                .filter(s -> s.getSeverityLevel() != null
                        && s.getSeverityLevel().name().equals("LIFE_THREATENING")).count();
        model.addAttribute("lifeThreatCount", lifeThreatCount);

        Map<String, Long> sickBreakdown = categoryCountFromList(
                sickList, s -> s.getLivestock() != null ? catName(s.getLivestock()) : null);
        model.addAttribute("sickBreakdown", toJson(sickBreakdown));

        // ── Sick History ─────────────────────────────────────────────────────
        long yearSickCount      = sickHistoryRepository.countSickByYear(year);
        long yearCriticalCount  = sickHistoryRepository.countCriticalByYear(year);
        long yearRecoveredCount = sickHistoryRepository.countRecoveredByYear(year);
        model.addAttribute("yearSickCount",      yearSickCount);
        model.addAttribute("yearCriticalCount",  yearCriticalCount);
        model.addAttribute("yearRecoveredCount", yearRecoveredCount);

        long recoveryRate = yearSickCount > 0
                ? Math.round((yearRecoveredCount * 100.0) / yearSickCount) : 100L;
        model.addAttribute("recoveryRate", recoveryRate);

        LocalDateTime historyFrom = LocalDateTime.now().minusDays(30);
        LocalDateTime historyTo   = LocalDateTime.now();
        List<LivestockSickHistory> recentHistory = safeList(
                sickHistoryRepository.findByDateRange(historyFrom, historyTo));
        model.addAttribute("totalHistoryEvents30Days", recentHistory.size());
        model.addAttribute("recentSickHistory", recentHistory.stream().limit(20).collect(Collectors.toList()));

        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        model.addAttribute("criticalThisMonthCount",
                safeList(sickHistoryRepository.findCriticalCasesByDateRange(monthStart, historyTo)).size());
        model.addAttribute("recoveredThisMonthCount",
                safeList(sickHistoryRepository.findRecoveredCasesByDateRange(monthStart, historyTo)).size());

        // ── Available livestock for forms ────────────────────────────────────
        model.addAttribute("livestockList", allLivestock.stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus()) && !"SOLD".equals(ls.getStatus()))
                .collect(Collectors.toList()));
        model.addAttribute("sickRecord", new LivestockSick());

        // ── Abortions ────────────────────────────────────────────────────────
        List<LivestockAbortion> abortionList = safeList(abortionService.getAll());
        model.addAttribute("abortionList",   abortionList);
        model.addAttribute("totalAbortions", stats.getTotalAbortions());

        long totalAnimalsAffected = abortionList.stream()
                .filter(a -> a.getLivestock() != null)
                .map(a -> a.getLivestock().getId()).distinct().count();
        model.addAttribute("totalAnimalsAffected", totalAnimalsAffected);

        String mostAffectedTag = abortionList.stream()
                .filter(a -> a.getLivestock() != null && a.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(a -> a.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostAffectedAnimalTag", mostAffectedTag);

        long abortionsThisMonth = abortionList.stream()
                .filter(a -> a.getAbortionDate() != null
                        && a.getAbortionDate().getMonthValue() == today.getMonthValue()
                        && a.getAbortionDate().getYear() == year).count();
        model.addAttribute("abortionsThisMonth", abortionsThisMonth);

        Map<String, Long> abortBreakdown = categoryCountFromList(
                abortionList, a -> a.getLivestock() != null ? catName(a.getLivestock()) : null);
        model.addAttribute("abortBreakdown", toJson(abortBreakdown));

        // ── Sales ────────────────────────────────────────────────────────────
        List<LivestockSale> salesList = safeList(saleService.getAll());
        model.addAttribute("salesList",        toJson(buildSaleMaps(salesList)));
        model.addAttribute("totalSales",       stats.getTotalSales());
        model.addAttribute("totalSaleRevenue", stats.getTotalSaleRevenue());

        String mostSoldTag = salesList.stream()
                .filter(s -> s.getLivestock() != null && s.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostSoldAnimalTag", mostSoldTag);

        long salesThisMonth = salesList.stream()
                .filter(s -> s.getSaleDate() != null
                        && s.getSaleDate().getMonthValue() == today.getMonthValue()
                        && s.getSaleDate().getYear() == year).count();
        model.addAttribute("salesThisMonth", salesThisMonth);

        Map<String, Long> salesBreakdown = categoryCountFromList(
                salesList, s -> s.getLivestock() != null ? catName(s.getLivestock()) : null);
        model.addAttribute("salesBreakdown", toJson(salesBreakdown));

        // ── Deaths ───────────────────────────────────────────────────────────
        List<LivestockDeath> deathsList = safeList(deathService.getAll());
        model.addAttribute("deathsList",   toJson(buildDeathMaps(deathsList)));
        model.addAttribute("totalDeaths",  stats.getTotalDeaths());

        long deathsThisMonth = deathsList.stream()
                .filter(d -> d.getDeathDate() != null
                        && d.getDeathDate().getMonthValue() == today.getMonthValue()
                        && d.getDeathDate().getYear() == year).count();
        model.addAttribute("deathsThisMonth", deathsThisMonth);

        String mostCommonCause = deathsList.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .collect(Collectors.groupingBy(LivestockDeath::getCauseOfDeath, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostCommonCause", mostCommonCause);

        long distinctCausesCount = deathsList.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .map(LivestockDeath::getCauseOfDeath).distinct().count();
        model.addAttribute("distinctCausesCount", distinctCausesCount);

        Map<String, Long> deathBreakdown = categoryCountFromList(
                deathsList, d -> d.getLivestock() != null ? catName(d.getLivestock()) : null);
        model.addAttribute("deathBreakdown", toJson(deathBreakdown));

        // ── Monthly Trends ───────────────────────────────────────────────────
        MonthlyTrends trends = calculateMonthlyTrends(year);
        model.addAttribute("monthlyBirths",     toJson(trends.getMonthlyBirths()));
        model.addAttribute("monthlySales",      toJson(trends.getMonthlySales()));
        model.addAttribute("monthlyDeaths",     toJson(trends.getMonthlyDeaths()));
        model.addAttribute("monthlyTreatments", toJson(trends.getMonthlyTreatments()));
        model.addAttribute("monthlyRevenue",    toJson(trends.getMonthlyRevenue()));
        model.addAttribute("monthlyCosts",      toJson(trends.getMonthlyCosts()));

        // ── Alerts ───────────────────────────────────────────────────────────
        List<Map<String, String>> alerts = generateAlerts();
        model.addAttribute("alerts",        alerts);
        model.addAttribute("totalAlerts",   alerts.size());
        model.addAttribute("criticalAlerts",alerts.stream()
                .filter(a -> "CRITICAL".equals(a.get("severity"))).count());

        return "admin-dashboard";
    }

    // ── USER DASHBOARD ─────────────────────────────────────────────────────────
    // Regular users only ever see data tied to their own beneficiaryId.
    // No @PreAuthorize role restriction needed here — both ADMIN and USER can
    // reach /user/**, but an admin has no reason to; the login handler in
    // SecurityConfig always routes admins to /admin/dashboard instead.

    @GetMapping("/user/dashboard")
    public String userDashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("username", email);
        model.addAttribute("userInitial", email.substring(0, 1).toUpperCase());
        model.addAttribute("isSuperAdmin", false);

        Users currentUser = usersService.getUserByEmail(email).orElse(null);
        if (currentUser != null && currentUser.getBeneficiaryId() != null) {
            UUID beneficiaryId = currentUser.getBeneficiaryId();
            List<Livestock> userLivestock = livestockRepository.findByBeneficiaryId(beneficiaryId);

            model.addAttribute("totalLivestock", (long) userLivestock.size());
            model.addAttribute("userLivestockCount", userLivestock.size());
            model.addAttribute("userActiveLivestock", userLivestock.stream()
                    .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count());

            LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
            List<LivestockBirth> userBirths = new ArrayList<>();
            for (Livestock ls : userLivestock) userBirths.addAll(birthService.getBirthsByMother(ls.getId()));
            model.addAttribute("totalBirths", (long) userBirths.size());
            model.addAttribute("userRecentBirths", userBirths.stream()
                    .filter(b -> b.getBirthDate() != null && !b.getBirthDate().isBefore(thirtyDaysAgo)).count());

            List<LivestockSale> userSales = new ArrayList<>();
            for (Livestock ls : userLivestock) userSales.addAll(saleService.getByLivestock(ls.getId()));
            model.addAttribute("totalSales", (long) userSales.size());
            model.addAttribute("userRecentSales", userSales.size());
            model.addAttribute("userTotalRevenue", userSales.stream()
                    .map(LivestockSale::getSalePrice).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            List<LivestockSick> userSick = new ArrayList<>();
            for (Livestock ls : userLivestock) userSick.addAll(sickService.getByLivestock(ls.getId()));
            model.addAttribute("totalSick", (long) userSick.size());
            model.addAttribute("userSickAnimals", userSick.size());

            model.addAttribute("totalTreatments", 0L);
            model.addAttribute("totalAbortions", 0L);
            model.addAttribute("totalDeaths", 0L);

            // Beneficiaries/Representatives counts on the user dashboard are
            // deliberately NOT the system-wide totals — regular users should
            // not see the whole farm's headcount. Leave them at 0/omit, or
            // wire them to counts scoped to this beneficiary if your schema
            // supports it.
            model.addAttribute("totalRepresentatives", 0L);
            model.addAttribute("totalBeneficiaries", 0L);
        } else {
            model.addAttribute("totalLivestock", 0L);
            model.addAttribute("userLivestockCount", 0);
            model.addAttribute("userActiveLivestock", 0);
            model.addAttribute("totalBirths", 0L);
            model.addAttribute("userRecentBirths", 0);
            model.addAttribute("totalSales", 0L);
            model.addAttribute("userRecentSales", 0);
            model.addAttribute("userTotalRevenue", BigDecimal.ZERO);
            model.addAttribute("totalSick", 0L);
            model.addAttribute("userSickAnimals", 0);
            model.addAttribute("totalTreatments", 0L);
            model.addAttribute("totalAbortions", 0L);
            model.addAttribute("totalDeaths", 0L);
            model.addAttribute("totalRepresentatives", 0L);
            model.addAttribute("totalBeneficiaries", 0L);
            model.addAttribute("noBeneficiary", true);
        }
        return "user-dashboard";
    }

    @GetMapping("/access-denied")
    public String accessDenied() { return "access-denied"; }

    // ── SUMMARY REPORT ─────────────────────────────────────────────────────────
    // Shared endpoint: admins see the whole farm, regular users see only
    // their own beneficiary's livestock/sales/treatments.

    @GetMapping("/livestock/summary-report")
    public String summaryReport(Authentication authentication, Model model) {
        LocalDate today = LocalDate.now();
        boolean admin = isAdmin(authentication);
        model.addAttribute("isAdmin", admin);

        List<Livestock> scoped = scopedLivestock(authentication);
        Set<UUID> scopedIds = scoped.stream().map(Livestock::getId).collect(Collectors.toSet());

        List<Livestock> activeAnimals = scoped.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                .collect(Collectors.toList());
        BigDecimal totalActiveValue = activeAnimals.stream()
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("activeAnimals", activeAnimals);
        model.addAttribute("totalActiveValue", totalActiveValue);

        List<LivestockTreatment> allTreatments = safeList(treatmentService.getAll()).stream()
                .filter(t -> admin || (t.getLivestock() != null && scopedIds.contains(t.getLivestock().getId())))
                .collect(Collectors.toList());
        BigDecimal totalTreatmentSpend = allTreatments.stream()
                .filter(t -> t.getTreatmentCost() != null).map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalTreatmentSpend", totalTreatmentSpend);
        model.addAttribute("totalTreatmentsCount", (long) allTreatments.size());
        model.addAttribute("totalCareSpend", totalTreatmentSpend);

        List<LivestockSale> allSales = safeList(saleService.getAll()).stream()
                .filter(s -> admin || (s.getLivestock() != null && scopedIds.contains(s.getLivestock().getId())))
                .collect(Collectors.toList());
        BigDecimal totalSaleRevenue = allSales.stream()
                .filter(s -> s.getSalePrice() != null).map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSaleRevenue", totalSaleRevenue);
        model.addAttribute("totalSalesCount", allSales.size());

        model.addAttribute("countActive",   scoped.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count());
        model.addAttribute("countSold",     scoped.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count());
        model.addAttribute("countDead",     scoped.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count());
        model.addAttribute("countSick",     scoped.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count());
        model.addAttribute("countPregnant", scoped.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count());
        model.addAttribute("totalAll", (long) scoped.size());
        model.addAttribute("generatedAt", today);

        BigDecimal netPosition = safeBigDecimal(totalSaleRevenue).subtract(safeBigDecimal(totalTreatmentSpend));
        model.addAttribute("netPosition",    netPosition);
        model.addAttribute("isProfit",       netPosition.compareTo(BigDecimal.ZERO) >= 0);
        model.addAttribute("netPositionAbs", netPosition.abs());
        return "livestock-summary-report";
    }

    // ── ALERTS API ──────────────────────────────────────────────────────────────
    // NOTE: currently farm-wide for any authenticated caller. If regular users
    // should only see alerts for their own animals, filter generateAlerts()
    // results by scopedLivestock(authentication) tag numbers before returning.

    @GetMapping("/dashboard/alerts")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardAlerts() {
        List<Map<String, String>> alertList = generateAlerts();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("alerts", alertList);
        response.put("totalAlerts", alertList.size());
        response.put("criticalCount", alertList.stream()
                .filter(a -> "CRITICAL".equals(a.get("severity"))).count());
        response.put("generatedAt", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    // ── Inner Classes ─────────────────────────────────────────────────────────

    public static class DashboardStatistics {
        private Long totalLivestock, activeLivestock, pregnantLivestock, sickLivestock, deadLivestock, soldLivestock;
        private Long totalBirths, totalMothers, totalChildren;
        private double averageOffspringPerBirth;
        private Long totalSick, currentlySick, criticalCount, recoveringCount, recoveredCount;
        private Long totalTreatments, unpaidTreatmentCount, ongoingTreatmentCount;
        private BigDecimal totalTreatmentCost;
        private Long totalSales;
        private BigDecimal totalSaleRevenue;
        private Long totalDeaths, totalAbortions;
        private Long totalUsers, activeUsers, inactiveUsers;
        private Long totalBeneficiaries, totalRepresentatives;
        private BigDecimal netPosition;
        private boolean isProfit;

        public Long getTotalLivestock() { return totalLivestock; }
        public void setTotalLivestock(Long v) { this.totalLivestock = v; }
        public Long getActiveLivestock() { return activeLivestock; }
        public void setActiveLivestock(Long v) { this.activeLivestock = v; }
        public Long getPregnantLivestock() { return pregnantLivestock; }
        public void setPregnantLivestock(Long v) { this.pregnantLivestock = v; }
        public Long getSickLivestock() { return sickLivestock; }
        public void setSickLivestock(Long v) { this.sickLivestock = v; }
        public Long getDeadLivestock() { return deadLivestock; }
        public void setDeadLivestock(Long v) { this.deadLivestock = v; }
        public Long getSoldLivestock() { return soldLivestock; }
        public void setSoldLivestock(Long v) { this.soldLivestock = v; }
        public Long getTotalBirths() { return totalBirths; }
        public void setTotalBirths(Long v) { this.totalBirths = v; }
        public Long getTotalMothers() { return totalMothers; }
        public void setTotalMothers(Long v) { this.totalMothers = v; }
        public Long getTotalChildren() { return totalChildren; }
        public void setTotalChildren(Long v) { this.totalChildren = v; }
        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double v) { this.averageOffspringPerBirth = v; }
        public Long getTotalSick() { return totalSick; }
        public void setTotalSick(Long v) { this.totalSick = v; }
        public Long getCurrentlySick() { return currentlySick; }
        public void setCurrentlySick(Long v) { this.currentlySick = v; }
        public Long getCriticalCount() { return criticalCount; }
        public void setCriticalCount(Long v) { this.criticalCount = v; }
        public Long getRecoveringCount() { return recoveringCount; }
        public void setRecoveringCount(Long v) { this.recoveringCount = v; }
        public Long getRecoveredCount() { return recoveredCount; }
        public void setRecoveredCount(Long v) { this.recoveredCount = v; }
        public Long getTotalTreatments() { return totalTreatments; }
        public void setTotalTreatments(Long v) { this.totalTreatments = v; }
        public BigDecimal getTotalTreatmentCost() { return totalTreatmentCost; }
        public void setTotalTreatmentCost(BigDecimal v) { this.totalTreatmentCost = v; }
        public Long getUnpaidTreatmentCount() { return unpaidTreatmentCount; }
        public void setUnpaidTreatmentCount(Long v) { this.unpaidTreatmentCount = v; }
        public Long getOngoingTreatmentCount() { return ongoingTreatmentCount; }
        public void setOngoingTreatmentCount(Long v) { this.ongoingTreatmentCount = v; }
        public Long getTotalSales() { return totalSales; }
        public void setTotalSales(Long v) { this.totalSales = v; }
        public BigDecimal getTotalSaleRevenue() { return totalSaleRevenue; }
        public void setTotalSaleRevenue(BigDecimal v) { this.totalSaleRevenue = v; }
        public Long getTotalDeaths() { return totalDeaths; }
        public void setTotalDeaths(Long v) { this.totalDeaths = v; }
        public Long getTotalAbortions() { return totalAbortions; }
        public void setTotalAbortions(Long v) { this.totalAbortions = v; }
        public Long getTotalUsers() { return totalUsers; }
        public void setTotalUsers(Long v) { this.totalUsers = v; }
        public Long getActiveUsers() { return activeUsers; }
        public void setActiveUsers(Long v) { this.activeUsers = v; }
        public Long getInactiveUsers() { return inactiveUsers; }
        public void setInactiveUsers(Long v) { this.inactiveUsers = v; }
        public Long getTotalBeneficiaries() { return totalBeneficiaries; }
        public void setTotalBeneficiaries(Long v) { this.totalBeneficiaries = v; }
        public Long getTotalRepresentatives() { return totalRepresentatives; }
        public void setTotalRepresentatives(Long v) { this.totalRepresentatives = v; }
        public BigDecimal getNetPosition() { return netPosition; }
        public void setNetPosition(BigDecimal v) { this.netPosition = v; }
        public boolean isProfit() { return isProfit; }
        public void setProfit(boolean v) { isProfit = v; }
    }

    public static class MonthlyTrends {
        private int[] monthlyBirths = new int[12];
        private int[] monthlySales = new int[12];
        private int[] monthlyDeaths = new int[12];
        private int[] monthlyTreatments = new int[12];
        private double[] monthlyRevenue = new double[12];
        private double[] monthlyCosts = new double[12];
        public int[] getMonthlyBirths() { return monthlyBirths; }
        public void setMonthlyBirths(int[] v) { this.monthlyBirths = v; }
        public int[] getMonthlySales() { return monthlySales; }
        public void setMonthlySales(int[] v) { this.monthlySales = v; }
        public int[] getMonthlyDeaths() { return monthlyDeaths; }
        public void setMonthlyDeaths(int[] v) { this.monthlyDeaths = v; }
        public int[] getMonthlyTreatments() { return monthlyTreatments; }
        public void setMonthlyTreatments(int[] v) { this.monthlyTreatments = v; }
        public double[] getMonthlyRevenue() { return monthlyRevenue; }
        public void setMonthlyRevenue(double[] v) { this.monthlyRevenue = v; }
        public double[] getMonthlyCosts() { return monthlyCosts; }
        public void setMonthlyCosts(double[] v) { this.monthlyCosts = v; }
    }
}
