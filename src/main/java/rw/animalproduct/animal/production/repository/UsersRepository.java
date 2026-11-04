package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsersRepository extends JpaRepository<Users, UUID> {

    /**
     * Find a user by email - used for authentication context
     */
    Optional<Users> findByEmail(String email);

    /**
     * Find a user by email with user type eagerly loaded
     * Prevents N+1 queries when accessing user type
     */
    @Query("SELECT u FROM Users u LEFT JOIN FETCH u.userTypeId WHERE u.email = :email")
    Optional<Users> findByEmailWithUserType(@Param("email") String email);

    /**
     * Check if a user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Find all active users
     */
    @Query("SELECT u FROM Users u WHERE u.isActive = true")
    List<Users> findActiveUsers();

    /**
     * Find all active users ordered by email
     */
    @Query("SELECT u FROM Users u WHERE u.isActive = true ORDER BY u.email ASC")
    List<Users> findActiveUsersOrdered();

    /**
     * Count active users
     */
    @Query("SELECT COUNT(u) FROM Users u WHERE u.isActive = true")
    long countActiveUsers();

    /**
     * Find users by email containing the search term (for autocomplete)
     */
    @Query("SELECT u FROM Users u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Users> searchByEmail(@Param("search") String search);

    /**
     * Find a user by email with beneficiary eagerly loaded
     */
    @Query("SELECT u FROM Users u LEFT JOIN FETCH u.beneficiaryId WHERE u.email = :email")
    Optional<Users> findByEmailWithBeneficiary(@Param("email") String email);
}