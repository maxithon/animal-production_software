package rw.animalproduct.animal.production.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickHistoryRepository;
import rw.animalproduct.animal.production.services.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final RepresentativeService      representativesAbororaService;
    private final BeneficiaryService         beneficiariesAmatungoService;
    private final UsersService               usersService;
    private final LivestockRepository        livestockRepository;
    private final LivestockBirthService      birthService;
    private final LivestockTreatmentService  treatmentService;
    private final LivestockSickService       sickService;
    private final LivestockAbortionService   abortionService;
    private final LivestockSaleService       saleService;
    private final LivestockDeathService      deathService;
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
        this.beneficiariesAmatungoService  = beneficiariesAmatungoService;
        this.usersService                  = usersService;
        this.livestockRepository           = livestockRepository;
        this.birthService                  = birthService;
        this.treatmentService              = treatmentService;
        this.sickService                   = sickService;
        this.abortionService               = abortionService;
        this.saleService                   = saleService;
        this.deathService                  = deathService;
        this.sickHistoryRepository         = sickHistoryRepository;
    }

    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Map<String, Object>> buildBirthMaps(List<LivestockBirth> births) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockBirth b : births) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("birthDate",      b.getBirthDate() != null ? b.getBirthDate().toString() : null);
            m.put("offspringCount", b.getOffspringCount());
            m.put("offspringGender", b.getOffspringGender());

            Map<String, Object> ls = new LinkedHashMap<>();
            if (b.getLivestock() != null) {
                ls.put("tagNumber", b.getLivestock().getTagNumber());
                ls.put("id",        b.getLivestock().getId());
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
        for (LivestockSick s : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reportedDate",   s.getReportedDate()   != null ? s.getReportedDate().toString()   : null);
            m.put("recoveryDate",   s.getRecoveryDate()   != null ? s.getRecoveryDate().toString()   : null);
            m.put("symptoms",       s.getSymptoms());
            m.put("diagnosis",      s.getDiagnosis());
            m.put("treatmentNotes", s.getTreatmentNotes());
            m.put("vetName",        s.getVetName());
            m.put("status",         s.getStatus()        != null ? s.getStatus().name()        : null);
            m.put("severityLevel",  s.getSeverityLevel() != null ? s.getSeverityLevel().name() : null);

            Map<String, Object> ls = new LinkedHashMap<>();
            if (s.getLivestock() != null) {
                ls.put("tagNumber", s.getLivestock().getTagNumber());
                ls.put("gender",    s.getLivestock().getGender() != null ? s.getLivestock().getGender() : null);
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
        for (LivestockSale s : sales) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("saleDate",   s.getSaleDate()   != null ? s.getSaleDate().toString()   : null);
            m.put("salePrice",  s.getSalePrice()  != null ? s.getSalePrice().doubleValue() : null);
            m.put("saleReason", s.getSaleReason());
            m.put("saleLocation", s.getSaleLocation());

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

            // Handle buyer information correctly
            Map<String, Object> buyer = new LinkedHashMap<>();
            if (s.getBuyer() != null) {
                buyer.put("name", s.getBuyer().getBuyerName());
                buyer.put("id", s.getBuyer().getId());
                buyer.put("phone", s.getBuyer().getBuyerPhone());
            }
            m.put("buyer", buyer);

            // Also add buyerName directly for easy access in templates
            String buyerName = s.getBuyer() != null ? s.getBuyer().getBuyerName() : null;
            if (buyerName != null) {
                m.put("buyerName", buyerName);
            } else {
                m.put("buyerName", "Unknown Buyer");
            }

            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildDeathMaps(List<LivestockDeath> deaths) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockDeath d : deaths) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deathDate",    d.getDeathDate()    != null ? d.getDeathDate().toString()    : null);
            m.put("causeOfDeath", d.getCauseOfDeath());

            Map<String, Object> ls = new LinkedHashMap<>();
            if (d.getLivestock() != null) {
                ls.put("tagNumber", d.getLivestock().getTagNumber());
                ls.put("gender",    d.getLivestock().getGender() != null ? d.getLivestock().getGender() : null);
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

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Authentication authentication, Model model) {

        String email = authentication.getName();
        model.addAttribute("username",    email);
        model.addAttribute("userInitial", email.substring(0, 1).toUpperCase());
        model.addAttribute("isSuperAdmin", true);

        model.addAttribute("totalRepresentatives", representativesAbororaService.getAll().size());
        model.addAttribute("totalBeneficiaries",   beneficiariesAmatungoService.getAll().size());

        List<Users> allUsers   = usersService.getAllUsers();
        long activeUsers       = allUsers.stream().filter(Users::isActive).count();
        long inactiveUsers     = allUsers.stream().filter(u -> !u.isActive()).count();
        model.addAttribute("totalUsers",    allUsers.size());
        model.addAttribute("activeUsers",   activeUsers);
        model.addAttribute("inactiveUsers", inactiveUsers);

        model.addAttribute("totalLivestock", livestockRepository.count());

        LocalDate today = LocalDate.now();
        int       year  = today.getYear();

        List<LivestockBirth> allBirths = birthService.getAll();
        model.addAttribute("totalBirths", allBirths.size());

        long totalMothers = allBirths.stream()
                .map(b -> b.getLivestock() != null ? b.getLivestock().getId() : null)
                .filter(Objects::nonNull).distinct().count();
        model.addAttribute("totalMothers", totalMothers);

        long totalLinkedChildren = allBirths.stream()
                .mapToLong(b -> b.getChildren() != null ? b.getChildren().size() : 0).sum();
        model.addAttribute("totalLinkedChildren", totalLinkedChildren);

        double avg = allBirths.isEmpty() ? 0 :
                allBirths.stream().mapToInt(b -> b.getOffspringCount() != null ? b.getOffspringCount() : 0)
                        .average().orElse(0);
        model.addAttribute("avgOffspringPerBirth", Math.round(avg));

        model.addAttribute("recentBirths", toJson(buildBirthMaps(allBirths)));

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

        long unpaidTreatmentCount = treatmentList.stream()
                .filter(t -> t.getIsPaid() != null && !t.getIsPaid()).count();
        model.addAttribute("unpaidTreatmentCount", unpaidTreatmentCount);

        long ongoingTreatmentCount = treatmentList.stream()
                .filter(t -> t.getTreatmentStatus() != null
                        && t.getTreatmentStatus().name().equalsIgnoreCase("ONGOING")).count();
        model.addAttribute("ongoingTreatmentCount", ongoingTreatmentCount);

        String mostTreatedAnimalTag = treatmentList.stream()
                .filter(t -> t.getLivestock() != null && t.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostTreatedAnimalTag", mostTreatedAnimalTag);

        List<LivestockSick> sickList = sickService.getAll();

        model.addAttribute("sickRecords", sickList);
        model.addAttribute("sickList", toJson(buildSickMaps(sickList)));
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

        long criticalCount = sickList.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("CRITICAL")).count();
        model.addAttribute("criticalCount", criticalCount);

        long recoveringCount = sickList.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERING")).count();
        model.addAttribute("recoveringCount", recoveringCount);

        long recoveredCount = sickList.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERED")).count();
        model.addAttribute("recoveredCount", recoveredCount);

        long sickThisMonth = sickList.stream()
                .filter(s -> s.getReportedDate() != null
                        && s.getReportedDate().getMonthValue() == today.getMonthValue()
                        && s.getReportedDate().getYear()        == today.getYear()).count();
        model.addAttribute("sickThisMonth", sickThisMonth);

        String mostSickAnimalTag = sickList.stream()
                .filter(s -> s.getLivestock() != null && s.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostSickAnimalTag", mostSickAnimalTag);

        BigDecimal totalSickTreatmentCost = treatmentList.stream()
                .filter(t -> t.getSickLivestock() != null && t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSickTreatmentCost", totalSickTreatmentCost);

        long lifeThreatCount = sickList.stream()
                .filter(s -> s.getSeverityLevel() != null
                        && s.getSeverityLevel().name().equals("LIFE_THREATENING")).count();
        model.addAttribute("lifeThreatCount", lifeThreatCount);

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

        List<LivestockSickHistory> recentHistory =
                sickHistoryRepository.findByDateRange(historyFrom, historyTo);
        model.addAttribute("totalHistoryEvents30Days", recentHistory.size());

        List<LivestockSickHistory> recentHistoryTop20 = recentHistory.stream()
                .limit(20).collect(Collectors.toList());
        model.addAttribute("recentSickHistory", recentHistoryTop20);

        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        List<LivestockSickHistory> criticalThisMonth =
                sickHistoryRepository.findCriticalCasesByDateRange(monthStart, historyTo);
        model.addAttribute("criticalThisMonthCount", criticalThisMonth.size());

        List<LivestockSickHistory> recoveredThisMonth =
                sickHistoryRepository.findRecoveredCasesByDateRange(monthStart, historyTo);
        model.addAttribute("recoveredThisMonthCount", recoveredThisMonth.size());

        List<LivestockAbortion> abortionList = abortionService.getAll();
        model.addAttribute("abortionList",   abortionList);
        model.addAttribute("totalAbortions", (long) abortionList.size());

        long totalAnimalsAffected = abortionList.stream()
                .filter(a -> a.getLivestock() != null)
                .map(a -> a.getLivestock().getId()).distinct().count();
        model.addAttribute("totalAnimalsAffected", totalAnimalsAffected);

        String mostAffectedAnimalTag = abortionList.stream()
                .filter(a -> a.getLivestock() != null && a.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(a -> a.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostAffectedAnimalTag", mostAffectedAnimalTag);

        long abortionsThisMonth = abortionList.stream()
                .filter(a -> a.getAbortionDate() != null
                        && a.getAbortionDate().getMonthValue() == today.getMonthValue()
                        && a.getAbortionDate().getYear()        == today.getYear()).count();
        model.addAttribute("abortionsThisMonth", abortionsThisMonth);

        List<LivestockSale> salesList = saleService.getAll();
        model.addAttribute("salesList",  toJson(buildSaleMaps(salesList)));
        model.addAttribute("totalSales", (long) salesList.size());

        BigDecimal totalSaleRevenue = salesList.stream()
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSaleRevenue", totalSaleRevenue);

        String mostSoldAnimalTag = salesList.stream()
                .filter(s -> s.getLivestock() != null && s.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostSoldAnimalTag", mostSoldAnimalTag);

        long salesThisMonth = salesList.stream()
                .filter(s -> s.getSaleDate() != null
                        && s.getSaleDate().getMonthValue() == today.getMonthValue()
                        && s.getSaleDate().getYear()        == today.getYear()).count();
        model.addAttribute("salesThisMonth", salesThisMonth);

        List<LivestockDeath> deathsList = deathService.getAll();
        model.addAttribute("deathsList",  toJson(buildDeathMaps(deathsList)));
        model.addAttribute("totalDeaths", (long) deathsList.size());

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

        long distinctCausesCount = deathsList.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .map(LivestockDeath::getCauseOfDeath).distinct().count();
        model.addAttribute("distinctCausesCount", distinctCausesCount);

        int[] mBirths     = new int[12];
        int[] mSales      = new int[12];
        int[] mDeaths     = new int[12];
        int[] mTreatments = new int[12];
        double[] mRevenue = new double[12];
        double[] mCosts   = new double[12];

        for (LivestockBirth b : allBirths) {
            if (b.getBirthDate() != null && b.getBirthDate().getYear() == year)
                mBirths[b.getBirthDate().getMonthValue() - 1]++;
        }

        for (LivestockSale s : salesList) {
            if (s.getSaleDate() != null && s.getSaleDate().getYear() == year) {
                int idx = s.getSaleDate().getMonthValue() - 1;
                mSales[idx]++;
                if (s.getSalePrice() != null) mRevenue[idx] += s.getSalePrice().doubleValue();
            }
        }

        for (LivestockDeath d : deathsList) {
            if (d.getDeathDate() != null && d.getDeathDate().getYear() == year)
                mDeaths[d.getDeathDate().getMonthValue() - 1]++;
        }

        for (LivestockTreatment t : treatmentList) {
            if (t.getTreatmentDate() != null && t.getTreatmentDate().getYear() == year) {
                int idx = t.getTreatmentDate().getMonthValue() - 1;
                mTreatments[idx]++;
                if (t.getTreatmentCost() != null) mCosts[idx] += t.getTreatmentCost().doubleValue();
            }
        }

        model.addAttribute("monthlyBirths",     toJson(mBirths));
        model.addAttribute("monthlySales",      toJson(mSales));
        model.addAttribute("monthlyDeaths",     toJson(mDeaths));
        model.addAttribute("monthlyTreatments", toJson(mTreatments));
        model.addAttribute("monthlyRevenue",    toJson(mRevenue));
        model.addAttribute("monthlyCosts",      toJson(mCosts));

        return "admin-dashboard";
    }

    @GetMapping("/user/dashboard")
    public String userDashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("username",    email);
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

        List<Livestock> activeAnimals = livestockRepository.findByStatus(Livestock.STATUS_ACTIVE);
        BigDecimal totalActiveValue = activeAnimals.stream()
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("activeAnimals",   activeAnimals);
        model.addAttribute("totalActiveValue", totalActiveValue);

        List<LivestockTreatment> allTreatments = treatmentService.getAll();
        BigDecimal totalTreatmentSpend = allTreatments.stream()
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalTreatmentSpend",  totalTreatmentSpend);
        model.addAttribute("totalTreatmentsCount", allTreatments.size());

        BigDecimal totalSickCareSpend = allTreatments.stream()
                .filter(t -> t.getSickLivestock() != null && t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalCareSpend",     totalTreatmentSpend);
        model.addAttribute("totalSickCareSpend", totalSickCareSpend);

        List<LivestockSale> allSales = saleService.getAll();
        BigDecimal totalSaleRevenue = allSales.stream()
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSaleRevenue", totalSaleRevenue);
        model.addAttribute("totalSalesCount",  allSales.size());

        List<Livestock> all = livestockRepository.findAll();
        model.addAttribute("countActive",   all.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count());
        model.addAttribute("countSold",     all.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count());
        model.addAttribute("countDead",     all.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count());
        model.addAttribute("countSick",     all.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count());
        model.addAttribute("countPregnant", all.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count());
        model.addAttribute("totalAll",      (long) all.size());
        model.addAttribute("generatedAt",   today);

        BigDecimal netPosition = totalSaleRevenue.subtract(totalTreatmentSpend);
        model.addAttribute("netPosition",    netPosition);
        model.addAttribute("isProfit",       netPosition.compareTo(BigDecimal.ZERO) >= 0);
        model.addAttribute("netPositionAbs", netPosition.abs());

        return "livestock-summary-report";
    }
    @GetMapping("/dashboard/alerts")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardAlerts() {
        Map<String, Object> alerts = new LinkedHashMap<>();
        List<Map<String, String>> alertList = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate weekFromNow = today.plusDays(7);

        // 1. Check for upcoming treatments
        List<LivestockTreatment> upcomingTreatments = treatmentService.getAll().stream()
                .filter(t -> t.getNextTreatmentDate() != null)
                .filter(t -> !t.getNextTreatmentDate().isBefore(today))
                .filter(t -> t.getNextTreatmentDate().isBefore(weekFromNow))
                .collect(Collectors.toList());

        for (LivestockTreatment t : upcomingTreatments) {
            Map<String, String> alert = new LinkedHashMap<>();
            alert.put("type", "TREATMENT");
            alert.put("severity", "WARNING");
            alert.put("message", "Treatment due for " +
                    (t.getLivestock() != null ? t.getLivestock().getTagNumber() : "Unknown") +
                    " on " + t.getNextTreatmentDate());
            alert.put("date", t.getNextTreatmentDate().toString());
            alertList.add(alert);
        }

        // 2. Check for expected births
        List<Livestock> pregnantAnimals = livestockRepository.findAll().stream()
                .filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus()))
                .filter(l -> l.getExpectedDueDate() != null)
                .filter(l -> !l.getExpectedDueDate().isBefore(today))
                .filter(l -> l.getExpectedDueDate().isBefore(weekFromNow))
                .collect(Collectors.toList());

        for (Livestock l : pregnantAnimals) {
            Map<String, String> alert = new LinkedHashMap<>();
            alert.put("type", "BIRTH");
            alert.put("severity", "INFO");
            alert.put("message", "Expected birth for " + l.getTagNumber() + " on " + l.getExpectedDueDate());
            alert.put("date", l.getExpectedDueDate().toString());
            alertList.add(alert);
        }

        // 3. Check for sick animals not treated in 3+ days
        List<LivestockSick> untreatedSick = sickService.getAll().stream()
                .filter(s -> s.getStatus() != LivestockSick.SickStatus.RECOVERED)
                .filter(s -> s.getReportedDate() != null)
                .filter(s -> s.getReportedDate().isBefore(today.minusDays(3)))
                .collect(Collectors.toList());

        for (LivestockSick s : untreatedSick) {
            Map<String, String> alert = new LinkedHashMap<>();
            alert.put("type", "SICK");
            alert.put("severity", "CRITICAL");
            alert.put("message", "Animal " +
                    (s.getLivestock() != null ? s.getLivestock().getTagNumber() : "Unknown") +
                    " has been sick since " + s.getReportedDate() + " - needs attention!");
            alert.put("date", s.getReportedDate().toString());
            alertList.add(alert);
        }

        alerts.put("alerts", alertList);
        alerts.put("totalAlerts", alertList.size());
        alerts.put("criticalCount", alertList.stream().filter(a -> "CRITICAL".equals(a.get("severity"))).count());

        return ResponseEntity.ok(alerts);
    }
}