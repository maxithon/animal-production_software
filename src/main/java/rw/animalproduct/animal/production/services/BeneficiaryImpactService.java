package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.dto.BeneficiaryImpactReportDto;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BeneficiaryImpactService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockSaleRepository livestockSaleRepository;
    private final LivestockDeathRepository livestockDeathRepository;

    @Transactional(readOnly = true)
    public BeneficiaryImpactReportDto buildReport(LocalDate fromDate, LocalDate toDate) {

        BeneficiaryImpactReportDto r = new BeneficiaryImpactReportDto();
        r.setFromDate(fromDate);
        r.setToDate(toDate);
        r.setPeriodLabel(buildPeriodLabel(fromDate, toDate));

        // ── Load all data once ─────────────────────────────────────────
        List<Beneficiary> allBeneficiaries = beneficiaryRepository.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .collect(Collectors.toList());

        List<Livestock> allLivestock = livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> l.getBeneficiary() != null)
                .collect(Collectors.toList());

        List<LivestockSale> allSales = livestockSaleRepository.findAll().stream()
                .filter(s -> s.getLivestock() != null
                        && s.getLivestock().getBeneficiary() != null)
                .filter(s -> inRange(s.getSaleDate(), fromDate, toDate))
                .collect(Collectors.toList());

        List<LivestockDeath> allDeaths = livestockDeathRepository.findAll().stream()
                .filter(d -> d.getLivestock() != null
                        && d.getLivestock().getBeneficiary() != null)
                .filter(d -> inRange(d.getDeathDate(), fromDate, toDate))
                .collect(Collectors.toList());

        Map<UUID, LivestockSale> saleByLivestockId = new HashMap<>();
        for (LivestockSale s : allSales) saleByLivestockId.put(s.getLivestock().getId(), s);
        Map<UUID, LivestockDeath> deathByLivestockId = new HashMap<>();
        for (LivestockDeath d : allDeaths) deathByLivestockId.put(d.getLivestock().getId(), d);

        // ── 1. Program reach ───────────────────────────────────────────
        r.setTotalBeneficiaries(allBeneficiaries.size());
        r.setActiveBeneficiaries((int) allBeneficiaries.stream().filter(Beneficiary::isActive).count());
        r.setInactiveBeneficiaries(r.getTotalBeneficiaries() - r.getActiveBeneficiaries());

        for (Beneficiary b : allBeneficiaries) {
            String g = b.getGender() == null ? "" : b.getGender().toUpperCase();
            if ("FEMALE".equals(g)) r.setFemaleHeadedHouseholds(r.getFemaleHeadedHouseholds() + 1);
            else if ("MALE".equals(g)) r.setMaleHeadedHouseholds(r.getMaleHeadedHouseholds() + 1);
        }

        long withContract = allBeneficiaries.stream()
                .filter(b -> notBlank(b.getContractAgreement()))
                .count();
        r.setWithContract((int) withContract);
        r.setWithoutContract(r.getTotalBeneficiaries() - (int) withContract);
        r.setContractCompletionRatePercent(
                percent((int) withContract, r.getTotalBeneficiaries()));

        // ── Build per-beneficiary rows ─────────────────────────────────
        Map<UUID, BeneficiaryImpactReportDto.BeneficiaryRow> rowByBeneficiary = new LinkedHashMap<>();

        for (Beneficiary b : allBeneficiaries) {
            BeneficiaryImpactReportDto.BeneficiaryRow row =
                    new BeneficiaryImpactReportDto.BeneficiaryRow();
            row.setBeneficiaryId(b.getId().toString());
            row.setFullName(b.getFullName());
            row.setPhone(b.getPhone());                                   // ✅ phone
            row.setLocationName(b.getLocation() != null
                    ? b.getLocation().getName() : "—");                   // leaf (kept)
            row.setFullLocation(buildFullLocation(b.getLocation()));      // ✅ full breadcrumb
            row.setStatus(b.isActive() ? "ACTIVE" : "INACTIVE");
            rowByBeneficiary.put(b.getId(), row);
        }

        for (Livestock l : allLivestock) {
            UUID benId = l.getBeneficiary().getId();
            BeneficiaryImpactReportDto.BeneficiaryRow row = rowByBeneficiary.get(benId);
            if (row == null) continue;

            BigDecimal value = nz(l.getCurrentValue());
            LivestockSale sale = saleByLivestockId.get(l.getId());
            LivestockDeath death = deathByLivestockId.get(l.getId());

            boolean hasSale = sale != null;
            boolean hasDeath = death != null;
            boolean hasBirth = "BIRTH".equals(l.getAcquisitionMethod());
            boolean isAlive = !hasSale && !hasDeath;

            if (!hasBirth) {
                row.setAnimalsReceived(row.getAnimalsReceived() + 1);
                row.setOriginalValue(row.getOriginalValue().add(value));
            } else {
                row.setBirths(row.getBirths() + 1);
            }

            if (isAlive) {
                row.setAnimalsNow(row.getAnimalsNow() + 1);
                row.setCurrentValue(row.getCurrentValue().add(value));
            } else if (hasSale) {
                row.setSold(row.getSold() + 1);
                row.setSaleRevenue(row.getSaleRevenue().add(nz(sale.getSalePrice())));
            } else if (hasDeath) {
                row.setDeaths(row.getDeaths() + 1);
            }
        }

        for (BeneficiaryImpactReportDto.BeneficiaryRow row : rowByBeneficiary.values()) {
            BigDecimal totalGain = nz(row.getCurrentValue())
                    .add(nz(row.getSaleRevenue()))
                    .subtract(nz(row.getOriginalValue()));
            if (row.getOriginalValue().signum() > 0) {
                row.setAssetGrowthRatePercent(
                        round1(totalGain.doubleValue() * 100.0
                                / row.getOriginalValue().doubleValue()));
            } else {
                row.setAssetGrowthRatePercent(0.0);
            }

            int received = row.getAnimalsReceived();
            int alive = row.getAnimalsNow();
            int sold = row.getSold();
            int dead = row.getDeaths();
            int nonDead = alive + sold;
            row.setSurvivalRatePercent(percent(nonDead, received));
            if (dead > received) {
                row.setSurvivalRatePercent(0.0);
            }
        }

        r.setRows(new ArrayList<>(rowByBeneficiary.values()));

        // ── 2. Asset transfer totals ───────────────────────────────────
        int distributed = rowByBeneficiary.values().stream()
                .mapToInt(BeneficiaryImpactReportDto.BeneficiaryRow::getAnimalsReceived).sum();
        BigDecimal originalValue = rowByBeneficiary.values().stream()
                .map(BeneficiaryImpactReportDto.BeneficiaryRow::getOriginalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentValue = rowByBeneficiary.values().stream()
                .map(BeneficiaryImpactReportDto.BeneficiaryRow::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int currentAnimals = rowByBeneficiary.values().stream()
                .mapToInt(BeneficiaryImpactReportDto.BeneficiaryRow::getAnimalsNow).sum();
        int births = rowByBeneficiary.values().stream()
                .mapToInt(BeneficiaryImpactReportDto.BeneficiaryRow::getBirths).sum();
        int sold = rowByBeneficiary.values().stream()
                .mapToInt(BeneficiaryImpactReportDto.BeneficiaryRow::getSold).sum();
        int dead = rowByBeneficiary.values().stream()
                .mapToInt(BeneficiaryImpactReportDto.BeneficiaryRow::getDeaths).sum();
        BigDecimal revenue = rowByBeneficiary.values().stream()
                .map(BeneficiaryImpactReportDto.BeneficiaryRow::getSaleRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        r.setTotalAnimalsDistributed(distributed);
        r.setTotalOriginalValue(originalValue);
        r.setTotalCurrentValue(currentValue);
        r.setTotalCurrentAnimals(currentAnimals);
        r.setTotalBornAnimals(births);
        r.setTotalSoldAnimals(sold);
        r.setTotalDeadAnimals(dead);
        r.setHouseholdsWithNoAnimals((int) rowByBeneficiary.values().stream()
                .filter(row -> row.getAnimalsNow() == 0).count());

        // ── 3. Retention rates ─────────────────────────────────────────
        r.setMortalityRatePercent(percent(dead, distributed));
        r.setBirthRatePercent(percent(births, distributed));
        int nonDeadTotal = currentAnimals + sold;
        r.setSurvivalRatePercent(percent(nonDeadTotal, distributed));
        r.setOfftakeRatePercent(percent(sold, nonDeadTotal));

        // ── 4. Asset growth ────────────────────────────────────────────
        if (originalValue.signum() > 0) {
            double growth = currentValue.subtract(originalValue).doubleValue()
                    * 100.0 / originalValue.doubleValue();
            r.setAssetGrowthRatePercent(round1(growth));
        } else {
            r.setAssetGrowthRatePercent(0.0);
        }
        r.setHouseholdsDoubled((int) r.getRows().stream()
                .filter(BeneficiaryImpactReportDto.BeneficiaryRow::hasDoubled).count());

        // ── 5. Income ──────────────────────────────────────────────────
        r.setTotalSales(sold);
        r.setTotalSaleRevenue(revenue);
        if (r.getTotalBeneficiaries() > 0) {
            r.setAverageRevenuePerBeneficiary(revenue.divide(
                    BigDecimal.valueOf(r.getTotalBeneficiaries()), 0, RoundingMode.HALF_UP));
        }
        if (sold > 0) {
            r.setAverageSalePrice(revenue.divide(
                    BigDecimal.valueOf(sold), 0, RoundingMode.HALF_UP));
        }

        // ── 6. Breakdowns ──────────────────────────────────────────────
        // ✅ Location breakdown now uses the FULL breadcrumb
        r.setByLocation(buildBreakdown(
                allBeneficiaries,
                b -> buildFullLocation(b.getLocation()),
                r.getTotalBeneficiaries()));

        // ✅ Representative breakdown removed — never populated

        r.setByGender(buildBreakdown(
                allBeneficiaries,
                b -> b.getGender() == null ? "Not specified" : b.getGender(),
                r.getTotalBeneficiaries()));

        r.setByMaritalStatus(buildBreakdown(
                allBeneficiaries,
                b -> b.getMaritialStatus() == null ? "Not specified" : b.getMaritialStatus(),
                r.getTotalBeneficiaries()));

        // ── 7. Exceptions ──────────────────────────────────────────────
        r.setExceptions(buildExceptions(allBeneficiaries, rowByBeneficiary));

        return r;
    }

    /* ================================================================
     *  Helpers
     * ================================================================ */

    private String buildPeriodLabel(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "All time";
        if (from == null) return "Up to " + to;
        if (to == null) return "From " + from;
        return from + " → " + to;
    }

    private boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) return false;
        if (from != null && date.isBefore(from)) return false;
        if (to != null && date.isAfter(to)) return false;
        return true;
    }

    private double percent(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return round1(numerator * 100.0 / denominator);
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * ✅ NEW — Walk the Location parent chain and produce a full breadcrumb:
     *     Province › District › Sector › Cell › Village
     * Null-safe, cycle-safe, never returns null.
     */
    private String buildFullLocation(Location location) {
        if (location == null) return "Unspecified";
        LinkedList<String> chain = new LinkedList<>();
        Location current = location;
        int guard = 0;
        while (current != null && guard++ < 20) {
            if (current.getName() != null && !current.getName().isBlank()) {
                chain.addFirst(current.getName().trim());
            }
            current = current.getParent();
        }
        return chain.isEmpty() ? "Unspecified" : String.join(" › ", chain);
    }

    private List<BeneficiaryImpactReportDto.Breakdown> buildBreakdown(
            List<Beneficiary> all,
            java.util.function.Function<Beneficiary, String> keyFn,
            int total) {

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Beneficiary b : all) {
            counts.merge(keyFn.apply(b), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, c) -> Integer.compare(c.getValue(), a.getValue()))
                .map(e -> new BeneficiaryImpactReportDto.Breakdown(
                        e.getKey(), e.getValue(),
                        total > 0 ? round1(e.getValue() * 100.0 / total) : 0.0))
                .collect(Collectors.toList());
    }

    private List<BeneficiaryImpactReportDto.ExceptionRow> buildExceptions(
            List<Beneficiary> all,
            Map<UUID, BeneficiaryImpactReportDto.BeneficiaryRow> rows) {

        List<BeneficiaryImpactReportDto.ExceptionRow> list = new ArrayList<>();

        for (Beneficiary b : all) {
            BeneficiaryImpactReportDto.BeneficiaryRow row = rows.get(b.getId());
            if (row == null) continue;
            if (!b.isActive()) continue;

            String name  = b.getFullName();
            String phone = b.getPhone();
            String loc   = buildFullLocation(b.getLocation());   // ✅ full location
            String id    = b.getId().toString();

            if (!notBlank(b.getContractAgreement())) {
                list.add(new BeneficiaryImpactReportDto.ExceptionRow(
                        "WARN", "NO_CONTRACT", id, name, phone, loc,
                        "No signed contract on file."));
            }

            if (row.getAnimalsReceived() > 0 && row.getAnimalsNow() == 0 && row.getSold() == 0) {
                list.add(new BeneficiaryImpactReportDto.ExceptionRow(
                        "ERROR", "NO_ANIMALS", id, name, phone, loc,
                        "Received " + row.getAnimalsReceived()
                                + " animal(s) but has none alive and none sold."));
            }

            if (row.getAnimalsReceived() >= 3
                    && row.getDeaths() * 100.0 / row.getAnimalsReceived() > 30.0) {
                list.add(new BeneficiaryImpactReportDto.ExceptionRow(
                        "ERROR", "HIGH_MORTALITY", id, name, phone, loc,
                        row.getDeaths() + " of " + row.getAnimalsReceived()
                                + " animals died (" + Math.round(row.getDeaths() * 100.0
                                / row.getAnimalsReceived()) + "%)."));
            }

            if (row.getAnimalsReceived() > 0
                    && row.getAnimalsNow() == 0
                    && row.getSold() == row.getAnimalsReceived()) {
                list.add(new BeneficiaryImpactReportDto.ExceptionRow(
                        "WARN", "SOLD_ALL", id, name, phone, loc,
                        "Sold all " + row.getSold() + " animal(s) received."));
            }
        }

        list.sort(Comparator.comparing(BeneficiaryImpactReportDto.ExceptionRow::getSeverity));
        return list;
    }
}