package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.VeterinarianActivityReportDto;
import rw.animalproduct.animal.production.dto.VeterinarianActivityRow;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-veterinarian workload & effectiveness — caseload, revenue attributed,
 * and (most useful for a co-op/NGO) recovery rate of the sick cases each
 * vet personally handled.
 *
 * NOTE: assumes VeterinarianRepository / LivestockTreatmentRepository /
 * LivestockSickRepository / LivestockBreedingRepository / LivestockAbortionRepository
 * all exist with a standard findAll(). Adjust getters if your entities differ.
 */
@Service
@RequiredArgsConstructor
public class VeterinarianActivityService {

    private final VeterinarianRepository veterinarianRepository;
    private final LivestockTreatmentRepository treatmentRepository;
    private final LivestockSickRepository sickRepository;
    private final LivestockBreedingRepository breedingRepository;
    private final LivestockAbortionRepository abortionRepository;

    public VeterinarianActivityReportDto generateReport(LocalDate from, LocalDate to) {
        VeterinarianActivityReportDto report = new VeterinarianActivityReportDto();
        report.setFromDate(from);
        report.setToDate(to);

        List<Veterinarian> vets = veterinarianRepository.findAll().stream()
                .filter(v -> v.getIsDeleted() == null || !v.getIsDeleted())
                .collect(Collectors.toList());

        List<LivestockTreatment> treatments = treatmentRepository.findAll().stream()
                .filter(t -> t.getTreatmentDate() != null
                        && !t.getTreatmentDate().isBefore(from) && !t.getTreatmentDate().isAfter(to))
                .collect(Collectors.toList());

        List<LivestockSick> sickCases = sickRepository.findAll().stream()
                .filter(s -> s.getReportedDate() != null
                        && !s.getReportedDate().isBefore(from) && !s.getReportedDate().isAfter(to))
                .collect(Collectors.toList());

        List<LivestockBreeding> breedings = breedingRepository.findAll().stream()
                .filter(b -> b.getBreedingDate() != null
                        && !b.getBreedingDate().isBefore(from) && !b.getBreedingDate().isAfter(to))
                .collect(Collectors.toList());

        List<LivestockAbortion> abortions = abortionRepository.findAll().stream()
                .filter(a -> a.getAbortionDate() != null
                        && !a.getAbortionDate().isBefore(from) && !a.getAbortionDate().isAfter(to))
                .collect(Collectors.toList());

        List<VeterinarianActivityRow> rows = new ArrayList<>();

        for (Veterinarian v : vets) {
            UUID vetId = v.getId();
            VeterinarianActivityRow row = new VeterinarianActivityRow();
            row.setVeterinarianId(vetId.toString());
            row.setFullName(safe(v.getFirstName()) + " " + safe(v.getLastName()));
            row.setLicenseNumber(v.getLicenseNumber());
            row.setClinicName(v.getClinicName());
            row.setActive(Boolean.TRUE.equals(v.getIsActive()));

            for (LivestockTreatment t : treatments) {
                if (t.getVeterinarian() != null && vetId.equals(t.getVeterinarian().getId())) {
                    row.setTreatmentsHandled(row.getTreatmentsHandled() + 1);
                    if (t.getTreatmentCost() != null) {
                        row.setTreatmentRevenueHandled(row.getTreatmentRevenueHandled().add(t.getTreatmentCost()));
                    }
                }
            }

            for (LivestockSick s : sickCases) {
                if (s.getVeterinarian() != null && vetId.equals(s.getVeterinarian().getId())) {
                    row.setSickCasesAttended(row.getSickCasesAttended() + 1);
                    if ("RECOVERED".equals(s.getStatus() != null ? s.getStatus().toString() : null)) {
                        row.setSickCasesRecovered(row.getSickCasesRecovered() + 1);
                    }
                }
            }

            for (LivestockBreeding b : breedings) {
                if (b.getVeterinarian() != null && vetId.equals(b.getVeterinarian().getId())) {
                    row.setBreedingsAssisted(row.getBreedingsAssisted() + 1);
                }
            }

            for (LivestockAbortion a : abortions) {
                if (a.getVeterinarian() != null && vetId.equals(a.getVeterinarian().getId())) {
                    row.setAbortionsAttended(row.getAbortionsAttended() + 1);
                }
            }

            rows.add(row);
        }

        rows.sort(Comparator.comparing(VeterinarianActivityRow::getTreatmentsHandled).reversed());
        report.setRows(rows);
        report.setActiveVets((int) vets.stream().filter(Veterinarian::getIsActive).count());
        report.setTotalTreatmentsInPeriod(treatments.size());
        report.setTotalSickCasesInPeriod(sickCases.size());

        return report;
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
