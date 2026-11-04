package rw.animalproduct.animal.production.repository.spec;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import rw.animalproduct.animal.production.entity.LivestockSale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a dynamic {@link Specification} for LivestockSale filtering.
 * Each filter predicate is added ONLY when the corresponding argument is
 * non-null, so no null value is ever bound into the generated SQL. This
 * sidesteps the Postgres "could not determine data type of parameter $1"
 * error that occurs with static JPQL "(:param IS NULL OR ...)" patterns.
 */
public final class LivestockSaleSpecifications {

    private LivestockSaleSpecifications() {
    }

    public static Specification<LivestockSale> withFilters(LocalDate fromDate,
                                                           LocalDate toDate,
                                                           UUID categoryId,
                                                           String gender,
                                                           UUID buyerId,
                                                           BigDecimal minPrice,
                                                           BigDecimal maxPrice,
                                                           String paymentStatus) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(
                    cb.isFalse(root.get("isDeleted")),
                    cb.isNull(root.get("isDeleted"))
            ));

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("saleDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("saleDate"), toDate));
            }

            // Join to livestock once, reused for category and gender filters
            Join<Object, Object> livestockJoin = null;
            if (categoryId != null || (gender != null && !gender.isBlank())) {
                livestockJoin = root.join("livestock", JoinType.INNER);
            }
            if (categoryId != null) {
                Join<Object, Object> categoryJoin = livestockJoin.join("livestockCategory", JoinType.INNER);
                predicates.add(cb.equal(categoryJoin.get("id"), categoryId));
            }
            if (gender != null && !gender.isBlank()) {
                predicates.add(cb.equal(livestockJoin.get("gender"), gender));
            }

            if (buyerId != null) {
                predicates.add(cb.equal(root.get("buyer").get("id"), buyerId));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("salePrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("salePrice"), maxPrice));
            }
            if (paymentStatus != null && !paymentStatus.isBlank()) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}