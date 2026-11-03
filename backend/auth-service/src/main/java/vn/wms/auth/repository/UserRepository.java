package vn.wms.auth.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.auth.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = 'ADMIN' AND u.enabled = true")
    long countActiveAdmins();

    @Query(value = "SELECT DISTINCT u FROM User u " +
            "LEFT JOIN u.roles r " +
            "LEFT JOIN u.warehouseIds w " +
            "WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "      OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "      OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "  AND (:role IS NULL OR :role = '' OR r.name = :role) " +
            "  AND (:warehouseId IS NULL OR w = :warehouseId) " +
            "  AND (:enabled IS NULL OR u.enabled = :enabled)",
            countQuery = "SELECT COUNT(DISTINCT u) FROM User u " +
            "LEFT JOIN u.roles r " +
            "LEFT JOIN u.warehouseIds w " +
            "WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "      OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "      OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "  AND (:role IS NULL OR :role = '' OR r.name = :role) " +
            "  AND (:warehouseId IS NULL OR w = :warehouseId) " +
            "  AND (:enabled IS NULL OR u.enabled = :enabled)")
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("warehouseId") Long warehouseId,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );
}
