package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.MaleReadyToBreedDTO;
import rw.animalproduct.animal.production.repository.MalesReadyToBreedRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MalesReadyToBreedService {

    private final MalesReadyToBreedRepository repository;

    public MalesReadyToBreedService(MalesReadyToBreedRepository repository) {
        this.repository = repository;
    }

    public List<MaleReadyToBreedDTO> getAllReadyToBreed() {
        return mapRows(repository.findAllReadyToBreedRaw());
    }

    public List<MaleReadyToBreedDTO> search(String term) {
        if (term == null || term.isBlank()) return getAllReadyToBreed();
        return mapRows(repository.searchReadyToBreedRaw(term));
    }

    // ── Stats helpers ─────────────────────────────────────────────────────────

    public long countReadyToBreed() {
        return getAllReadyToBreed().size();
    }

    public double getAverageSuccessRate() {
        List<MaleReadyToBreedDTO> list = getAllReadyToBreed();
        if (list.isEmpty()) return 0.0;
        return list.stream()
                .mapToDouble(MaleReadyToBreedDTO::getSuccessRate)
                .average()
                .orElse(0.0);
    }

    public long countNeverBred() {
        return getAllReadyToBreed().stream()
                .filter(MaleReadyToBreedDTO::isNeverBred)
                .count();
    }

    // ── Mapper ────────────────────────────────────────────────────────────────
    // Column order matches MalesReadyToBreedRepository SELECT:
    //  0  id
    //  1  tag_number
    //  2  category_name
    //  3  age_months
    //  4  total_breedings
    //  5  successful_breedings
    //  6  date_received
    //  7  birth_date
    //  8  status
    //  9  gender
    // 10  current_value   (fetched but not mapped to DTO — ignored)
    // 11  acquisition_method

    private List<MaleReadyToBreedDTO> mapRows(List<Object[]> rows) {
        return rows.stream().map(row -> {
            MaleReadyToBreedDTO dto = new MaleReadyToBreedDTO();

            // col 0: id
            if (row[0] instanceof UUID u)        dto.setId(u);
            else if (row[0] != null)             dto.setId(UUID.fromString(row[0].toString()));

            // col 1: tag_number
            dto.setTagNumber(row[1] != null ? row[1].toString() : null);

            // col 2: category_name
            dto.setCategoryName(row[2] != null ? row[2].toString() : null);

            // col 3: age_months
            if (row[3] instanceof Number n)      dto.setAgeMonths(n.intValue());

            // col 4: total_breedings
            if (row[4] instanceof Number n)      dto.setTotalBreedings(n.longValue());

            // col 5: successful_breedings
            if (row[5] instanceof Number n)      dto.setSuccessfulBreedings(n.longValue());

            // col 6: date_received
            dto.setDateReceived(toLocalDate(row[6]));

            // col 7: birth_date
            dto.setBirthDate(toLocalDate(row[7]));

            // col 8: status
            dto.setStatus(row[8] != null ? row[8].toString() : null);

            // col 9: gender
            dto.setGender(row[9] != null ? row[9].toString() : null);

            // col 10: current_value — skip (not in DTO)

            // col 11: acquisition_method
            if (row.length > 11 && row[11] != null)
                dto.setAcquisitionMethod(row[11].toString());

            return dto;
        }).collect(Collectors.toList());
    }

    private LocalDate toLocalDate(Object val) {
        if (val == null)                        return null;
        if (val instanceof LocalDate ld)        return ld;
        if (val instanceof java.sql.Date sd)    return sd.toLocalDate();
        return LocalDate.parse(val.toString());
    }
}