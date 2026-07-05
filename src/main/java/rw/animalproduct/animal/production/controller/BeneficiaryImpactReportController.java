package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Representative;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.BeneficiaryService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the beneficiary impact report shown under /livestock.
 *
 * This was previously missing entirely, which is why requests to
 * GET /livestock/beneficiary-impact-report fell through to Spring's
 * static-resource handler and produced a 404 (NoResourceFoundException).
 */
@Controller
@RequestMapping("/livestock")
public class BeneficiaryImpactReportController {

    private final BeneficiaryService beneficiaryService;
    private final LocationRepository locationRepository;

    public BeneficiaryImpactReportController(BeneficiaryService beneficiaryService,
                                             LocationRepository locationRepository) {
        this.beneficiaryService = beneficiaryService;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/beneficiary-impact-report")
    public String beneficiaryImpactReport(Model model) {

        List<Beneficiary> allBeneficiaries = beneficiaryService.getAll();

        long totalBeneficiaries = allBeneficiaries.size();

        // ---- Gender distribution ----
        Map<String, Long> genderDistribution = allBeneficiaries.stream()
                .map(b -> isBlank(b.getGender()) ? "Not specified" : b.getGender())
                .collect(Collectors.groupingBy(g -> g, LinkedHashMap::new, Collectors.counting()));

        // ---- Marital status distribution ----
        Map<String, Long> maritalStatusDistribution = allBeneficiaries.stream()
                .map(b -> isBlank(b.getMaritialStatus()) ? "Not specified" : b.getMaritialStatus())
                .collect(Collectors.groupingBy(m -> m, LinkedHashMap::new, Collectors.counting()));

        // ---- Contract agreement completion ----
        long withContract = allBeneficiaries.stream()
                .filter(b -> !isBlank(b.getContractAgreement()))
                .count();
        double contractCompletionRate = totalBeneficiaries == 0
                ? 0
                : (withContract * 100.0) / totalBeneficiaries;

        // ---- Beneficiaries per location ----
        Map<String, Long> locationCounts = allBeneficiaries.stream()
                .filter(b -> b.getLocation() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getLocation().getName(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        List<Map.Entry<String, Long>> locationDistribution = locationCounts.entrySet().stream()
                .sorted((a, c) -> Long.compare(c.getValue(), a.getValue()))
                .collect(Collectors.toList());

        // ---- Beneficiaries per representative ----
        Map<String, Long> representativeCounts = allBeneficiaries.stream()
                .filter(b -> b.getRepresentative() != null)
                .collect(Collectors.groupingBy(
                        b -> representativeLabel(b.getRepresentative()),
                        LinkedHashMap::new,
                        Collectors.counting()));

        List<Map.Entry<String, Long>> representativeDistribution = representativeCounts.entrySet().stream()
                .sorted((a, c) -> Long.compare(c.getValue(), a.getValue()))
                .collect(Collectors.toList());

        // ---- Recent registrations (last 30 days) ----
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        long recentRegistrations = allBeneficiaries.stream()
                .filter(b -> b.getCreatedDate() != null &&
                        b.getCreatedDate().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .isAfter(thirtyDaysAgo))
                .count();

        long totalLocations = locationRepository.count();
        long activeLocations = locationCounts.size();

        model.addAttribute("totalBeneficiaries", totalBeneficiaries);
        model.addAttribute("totalLocations", totalLocations);
        model.addAttribute("activeLocations", activeLocations);
        model.addAttribute("genderDistribution", genderDistribution);
        model.addAttribute("maritalStatusDistribution", maritalStatusDistribution);
        model.addAttribute("withContract", withContract);
        model.addAttribute("contractCompletionRate", String.format("%.1f", contractCompletionRate));
        model.addAttribute("locationDistribution", locationDistribution);
        model.addAttribute("representativeDistribution", representativeDistribution);
        model.addAttribute("recentRegistrations", recentRegistrations);
        model.addAttribute("generatedDate", new Date());

        return "beneficiary-impact-report";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    /**
     * NOTE: Adjust this to match the real fields on your Representative entity.
     * I don't have that entity's source, so I've assumed getFirstName()/getLastName().
     * If it doesn't compile, swap in whatever fields Representative actually exposes
     * (e.g. getName(), getFullName()), or fall back to representative.getId().
     */
    private String representativeLabel(Representative representative) {
        String first = representative.getFirstName();
        String last = representative.getLastName();
        if (isBlank(first) && isBlank(last)) {
            return "Representative " + representative.getId();
        }
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }
}
