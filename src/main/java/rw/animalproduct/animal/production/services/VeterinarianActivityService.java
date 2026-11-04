package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.dto.VeterinarianActivityDtos.*;
import rw.animalproduct.animal.production.entity.LivestockSick;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VeterinarianActivityService {

    private final VeterinarianRepository vetRepo;
    private final LivestockTreatmentRepository treatmentRepo;
    private final LivestockSickRepository sickRepo;

    /* ------------------------------------------------------------------ */
    /*  MAIN ENTRY POINT                                                  */
    /* ------------------------------------------------------------------ */
    @Cacheable(value = "vetActivityReport",
            key = "#from.toString() + ':' + #to.toString()")
    @Transactional(readOnly = true)
    public Report generateReport(LocalDate from, LocalDate to) {
        validateRange(from, to);

        Report r = new Report();
        r.setFromDate(from);
        r.setToDate(to);

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo   = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        r.setPreviousFromDate(prevFrom);
        r.setPreviousToDate(prevTo);

        // ---- Summary ----
        r.setActiveVets(vetRepo.findByIsDeletedFalseAndIsActiveTrue().size());
        r.setTotalTreatmentsInPeriod((int) treatmentRepo.countInRange(from, to));
        r.setDistinctAnimalsTreated((int) treatmentRepo.countDistinctAnimalsInRange(from, to));
        r.setTotalSickCasesInPeriod((int) sickRepo.countInRange(from, to));

        BigDecimal totalCost = treatmentRepo.sumCostInRange(from, to);
        r.setTotalTreatmentCost(totalCost == null ? BigDecimal.ZERO : totalCost);
        r.setAvgCostPerAnimal(avg(r.getTotalTreatmentCost(), r.getDistinctAnimalsTreated()));
        r.setAvgCostPerTreatment(avg(r.getTotalTreatmentCost(), r.getTotalTreatmentsInPeriod()));

        // ---- Outcomes ----
        r.setOutcomes(buildOutcomes(from, to));

        // ---- Rates ----
        int recovered  = r.getOutcomes().getRecovered();
        int recovering = r.getOutcomes().getRecovering();
        int dead       = r.getOutcomes().getDead();
        int attended   = r.getOutcomes().getTotal();
        int completed  = recovered + dead;

        r.setTotalRecovered(recovered);
        r.setTotalDeaths(dead);
        r.setRecoveryRatePercent(rate(recovered, completed));
        r.setSuccessRatePercent(rate(recovered + recovering, attended));
        r.setCaseClosureRatePercent(rate(completed, attended));

        // ---- Rows ----
        r.setRows(buildRows(from, to));

        // ---- Analysis ----
        r.setAnalysis(buildAnalysis(from, to));

        // ---- Follow-ups & attention ----
        LocalDate today = LocalDate.now();
        r.setFollowUpsDueList(buildFollowUpsDue(today));
        r.setCasesRequiringAttention(buildCasesRequiringAttention());
        r.setFollowUpsDue(r.getFollowUpsDueList().size());
        r.setFollowUpsOverdue((int) r.getFollowUpsDueList().stream()
                .filter(f -> f.getDaysOverdue() > 0).count());

        // ---- Trends ----
        r.setTrends(buildTrends(from, to));

        // ---- Deltas ----
        applyDeltas(r, prevFrom, prevTo);

        // ---- Headline ----
        r.setMostImportantChange(buildHeadline(r));

        return r;
    }

    /* ------------------------------------------------------------------ */
    /*  HELPERS                                                           */
    /* ------------------------------------------------------------------ */
    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null)
            throw new IllegalArgumentException("Both dates are required");
        if (from.isAfter(to))
            throw new IllegalArgumentException("'from' must be on or before 'to'");
        if (ChronoUnit.DAYS.between(from, to) > 366)
            throw new IllegalArgumentException("Range cannot exceed 366 days");
    }

    private double rate(int num, int den) {
        if (den == 0) return 0.0;
        return BigDecimal.valueOf(num * 100.0 / den)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double pct(int n, int total) { return rate(n, total); }

    private BigDecimal avg(BigDecimal total, int count) {
        if (count == 0 || total == null) return null;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /* ---------- Outcomes ---------- */
    private Outcome buildOutcomes(LocalDate from, LocalDate to) {
        Outcome o = new Outcome();
        Map<String, Integer> byStatus = new HashMap<>();
        for (Object[] row : sickRepo.countByStatusInRange(from, to)) {
            String status = row[0] == null ? "SICK" : row[0].toString();
            byStatus.put(status, ((Number) row[1]).intValue());
        }
        o.setRecovered (byStatus.getOrDefault("RECOVERED",  0));
        o.setRecovering(byStatus.getOrDefault("RECOVERING", 0));
        o.setSick      (byStatus.getOrDefault("SICK",       0));
        o.setCritical  (byStatus.getOrDefault("CRITICAL",   0));
        o.setDead      (byStatus.getOrDefault("DEAD",       0));

        int total = o.getRecovered() + o.getRecovering() + o.getSick()
                + o.getCritical()  + o.getDead();
        o.setTotal(total);
        o.setCompleted(o.getRecovered() + o.getDead());
        o.setPositiveProgress(o.getRecovered() + o.getRecovering());

        o.setRecoveredPct (pct(o.getRecovered(),  total));
        o.setRecoveringPct(pct(o.getRecovering(), total));
        o.setSickPct      (pct(o.getSick(),       total));
        o.setCriticalPct  (pct(o.getCritical(),   total));
        o.setDeadPct      (pct(o.getDead(),       total));

        try { o.setAvgDaysToRecovery(sickRepo.getAverageRecoveryDays()); }
        catch (Exception ignored) { /* native query may not exist in test DB */ }
        return o;
    }

    /* ---------- Rows ---------- */
    private List<VetRow> buildRows(LocalDate from, LocalDate to) {
        List<VetAggregate> aggs = vetRepo.findActivitySummary(from, to);
        List<VetRow> rows = new ArrayList<>();
        for (VetAggregate a : aggs) {
            VetRow row = new VetRow();
            row.setVeterinarianId(a.getId().toString());
            row.setFullName(safe(a.getFirstName()) + " " + safe(a.getLastName()));
            row.setLicenseNumber(a.getLicenseNumber());
            row.setClinicName(a.getClinicName());
            row.setSpecialization(a.getSpecialization());
            row.setActive(Boolean.TRUE.equals(a.getActive()));

            row.setTreatmentRecords((int) a.getTreatmentRecords());
            row.setDistinctAnimalsTreated((int) a.getDistinctAnimalsTreated());
            row.setTotalTreatmentCost(a.getTotalTreatmentCost() == null
                    ? BigDecimal.ZERO : a.getTotalTreatmentCost());

            row.setSickCasesAttended  ((int) a.getSickCasesAttended());
            row.setSickCasesRecovered ((int) a.getSickCasesRecovered());
            row.setSickCasesRecovering((int) a.getSickCasesRecovering());
            row.setSickCasesStillSick ((int) a.getSickCasesStillSick());
            row.setSickCasesCritical  ((int) a.getSickCasesCritical());
            row.setSickCasesDead      ((int) a.getSickCasesDead());
            row.setFollowUpsDue       ((int) a.getFollowUpsDue());
            row.setFollowUpsOverdue   ((int) a.getFollowUpsOverdue());

            rows.add(row);
        }
        rows.sort(Comparator.comparingInt(VetRow::getTreatmentRecords).reversed());
        for (int i = 0; i < rows.size(); i++) rows.get(i).setRankByTreatments(i + 1);
        return rows;
    }

    /* ---------- Analysis ---------- */
    private Analysis buildAnalysis(LocalDate from, LocalDate to) {
        Analysis a = new Analysis();

        List<Dimension> cat = treatmentRepo.aggregateByCategory(from, to);
        List<Dimension> dis = treatmentRepo.aggregateByDisease(from, to);
        List<Dimension> loc = treatmentRepo.aggregateByLocation(from, to);

        cat.forEach(Dimension::computeAvg);
        dis.forEach(Dimension::computeAvg);
        loc.forEach(Dimension::computeAvg);

        a.setByLivestockCategory(cat);
        a.setByDisease(dis);
        a.setByLocation(loc);
        a.setTopCostDrivers(dis.stream()
                .sorted(Comparator.comparing(Dimension::getTotalCost).reversed())
                .limit(3).toList());
        a.setTopDiseaseBurdens(dis.stream()
                .sorted(Comparator.comparingInt(Dimension::getCount).reversed())
                .limit(3).toList());
        return a;
    }

    /* ---------- Follow-ups (treatment-level) ---------- */
    private List<FollowUp> buildFollowUpsDue(LocalDate asOf) {
        List<FollowUp> list = new ArrayList<>();
        for (LivestockTreatment t : treatmentRepo.findFollowUpsDue(asOf)) {
            FollowUp f = new FollowUp();
            f.setAnimalTag(tagOf(t));
            f.setAnimalId(idOf(t));
            f.setVeterinarianName(vetNameOf(t));
            f.setIssue("Follow-up for: " + safe(t.getDiagnosisName()));
            f.setDueDate(t.getFollowUpDate());
            f.setStatus(t.getTreatmentStatus() == null
                    ? "PENDING" : t.getTreatmentStatus().toString());

            long overdue = ChronoUnit.DAYS.between(t.getFollowUpDate(), asOf);
            f.setDaysOverdue(overdue);
            if (overdue >= 7)     f.setPriority("CRITICAL");
            else if (overdue > 0) f.setPriority("OVERDUE");
            else                  f.setPriority("DUE_SOON");
            list.add(f);
        }
        return list;
    }

    /* ---------- Cases requiring attention (sick-level) ----------
     *  NOTE: LivestockSick has no followUpDate — we use reportedDate
     *  and the sick status to prioritise. Diagnosis is `s.getDiagnosis()`.
     * ------------------------------------------------------------- */
    private List<FollowUp> buildCasesRequiringAttention() {
        List<FollowUp> list = new ArrayList<>();
        for (LivestockSick s : sickRepo.findCasesRequiringAttention()) {
            FollowUp f = new FollowUp();
            f.setAnimalTag(tagOf(s));
            f.setAnimalId(idOf(s));
            f.setVeterinarianName(vetNameOf(s));
            f.setIssue("Reassess: " + safe(s.getDiagnosis())
                    + " [" + (s.getStatus() == null ? "SICK" : s.getStatus()) + "]");
            f.setStatus(s.getStatus() == null ? "SICK" : s.getStatus().toString());

            String status = s.getStatus() == null ? "SICK" : s.getStatus().toString();
            if ("CRITICAL".equals(status))   f.setPriority("CRITICAL");
            else if ("SICK".equals(status))  f.setPriority("OVERDUE");
            else                             f.setPriority("DUE_SOON");

            f.setDueDate(s.getReportedDate());
            f.setDaysOverdue(s.getReportedDate() == null ? 0
                    : ChronoUnit.DAYS.between(s.getReportedDate(), LocalDate.now()));
            list.add(f);
        }
        return list;
    }

    /* ---------- Small entity helpers ---------- */
    private String tagOf(LivestockTreatment t) {
        try { return t.getLivestock() != null ? t.getLivestock().getTagNumber() : "—"; }
        catch (Exception e) { return "—"; }
    }
    private String idOf(LivestockTreatment t) {
        try { return t.getLivestock() != null ? t.getLivestock().getId().toString() : null; }
        catch (Exception e) { return null; }
    }
    private String vetNameOf(LivestockTreatment t) {
        try { return t.getVeterinarian() == null ? "Unassigned"
                : t.getVeterinarian().getFullName(); }
        catch (Exception e) { return "Unassigned"; }
    }

    private String tagOf(LivestockSick s) {
        try { return s.getLivestock() != null ? s.getLivestock().getTagNumber() : "—"; }
        catch (Exception e) { return "—"; }
    }
    private String idOf(LivestockSick s) {
        try { return s.getLivestock() != null ? s.getLivestock().getId().toString() : null; }
        catch (Exception e) { return null; }
    }
    private String vetNameOf(LivestockSick s) {
        try { return s.getVeterinarian() == null ? "Unassigned"
                : s.getVeterinarian().getFullName(); }
        catch (Exception e) { return "Unassigned"; }
    }

    /* ---------- Trends ---------- */
    private Trends buildTrends(LocalDate from, LocalDate to) {
        Trends t = new Trends();
        t.setGranularity("DAY");
        for (Object[] row : treatmentRepo.dailyTreatmentCounts(from, to))
            t.getTreatments().add(new Point((LocalDate) row[0],
                    BigDecimal.valueOf(((Number) row[1]).longValue())));
        for (Object[] row : treatmentRepo.dailyCostSums(from, to))
            t.getCosts().add(new Point((LocalDate) row[0],
                    row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]));
        for (Object[] row : sickRepo.dailySickCounts(from, to))
            t.getSickCases().add(new Point((LocalDate) row[0],
                    BigDecimal.valueOf(((Number) row[1]).longValue())));
        for (Object[] row : sickRepo.dailyRecovery(from, to)) {
            LocalDate day = (LocalDate) row[0];
            long recovered = ((Number) row[1]).longValue();
            long total     = ((Number) row[2]).longValue();
            BigDecimal rate = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(recovered * 100.0 / total)
                    .setScale(1, RoundingMode.HALF_UP);
            t.getRecovery().add(new Point(day, rate));
        }
        return t;
    }

    /* ---------- Deltas ---------- */
    private void applyDeltas(Report r, LocalDate prevFrom, LocalDate prevTo) {
        long prevTreatments = treatmentRepo.countInRange(prevFrom, prevTo);
        long prevAnimals    = treatmentRepo.countDistinctAnimalsInRange(prevFrom, prevTo);
        BigDecimal prevCost = treatmentRepo.sumCostInRange(prevFrom, prevTo);

        r.setTreatmentsDeltaPercent(deltaPct(r.getTotalTreatmentsInPeriod(), prevTreatments));
        r.setDistinctAnimalsDeltaPercent(deltaPct(r.getDistinctAnimalsTreated(), prevAnimals));
        r.setCostDeltaPercent(deltaPct(r.getTotalTreatmentCost(), prevCost));

        Outcome prev = buildOutcomes(prevFrom, prevTo);
        double prevRecovery = rate(prev.getRecovered(),
                prev.getRecovered() + prev.getDead());
        r.setRecoveryRateDeltaPp(round1(r.getRecoveryRatePercent() - prevRecovery));
    }

    private Double deltaPct(Number current, Number previous) {
        if (previous == null || previous.doubleValue() == 0) return null;
        double change = (current.doubleValue() - previous.doubleValue())
                / previous.doubleValue() * 100.0;
        return round1(change);
    }

    private double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /* ---------- Headline ---------- */
    private Headline buildHeadline(Report r) {
        Headline h = new Headline();
        h.setMetricName("Recovery Rate");
        double current = r.getRecoveryRatePercent();
        double delta = r.getRecoveryRateDeltaPp() == null ? 0 : r.getRecoveryRateDeltaPp();
        h.setFromValue(String.format("%.1f%% (prev.)", current - delta));
        h.setToValue  (String.format("%.1f%% (current)", current));
        h.setDirection(delta > 0.5 ? "UP" : delta < -0.5 ? "DOWN" : "FLAT");

        if ("DOWN".equals(h.getDirection())) {
            h.setWhyItMatters("A drop in recovery rate means more animals stay sick longer, "
                    + "raising cost per recovery and mortality risk.");
            h.setRecommendedAction("Review the top disease burdens; reassign vets to the "
                    + "location with the lowest recovery rate.");
        } else if ("UP".equals(h.getDirection())) {
            h.setWhyItMatters("Improving recovery rate reduces cost per animal and follow-up load.");
            h.setRecommendedAction("Document what changed and replicate it in lower-performing areas.");
        } else {
            h.setWhyItMatters("Stable recovery — look at cost and follow-up compliance for gains.");
            h.setRecommendedAction("Focus on the Cases Requiring Attention worklist.");
        }
        return h;
    }

    private String safe(String s) { return s == null ? "" : s; }
}