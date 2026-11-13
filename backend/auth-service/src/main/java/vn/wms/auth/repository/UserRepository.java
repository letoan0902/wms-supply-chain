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

    /** Tìm tài khoản theo ID trong phạm vi một doanh nghiệp; tài khoản của doanh nghiệp khác coi như không tồn tại. */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.company.id = :companyId")
    Optional<User> findByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    /** Tìm tài khoản đại diện đầu tiên (Admin) thuộc doanh nghiệp. */
    @Query("SELECT u FROM User u WHERE u.company.id = :companyId ORDER BY u.id ASC")
    Optional<User> findFirstByCompanyId(@Param("companyId") Long companyId);

    /** Số ADMIN đang hoạt động của một doanh nghiệp (quy tắc "ADMIN cuối cùng" tính trong từng doanh nghiệp). */
    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r " +
            "WHERE r.name = 'ADMIN' AND u.enabled = true AND u.company.id = :companyId")
    long countActiveAdmins(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.company.id = :companyId AND u.enabled = true")
    long countByCompanyIdAndEnabledTrue(@Param("companyId") Long companyId);

    @Query(value = "SELECT DISTINCT u FROM User u " +
            "LEFT JOIN u.roles r " +
            "LEFT JOIN u.warehouseIds w " +
            "WHERE u.company.id = :companyId " +
            "  AND (:keyword IS NULL OR :keyword = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "      OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "      OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "  AND (:role IS NULL OR :role = '' OR r.name = :role) " +
            "  AND (:warehouseId IS NULL OR w = :warehouseId) " +
            "  AND (:enabled IS NULL OR u.enabled = :enabled)",
            countQuery = "SELECT COUNT(DISTINCT u) FROM User u " +
            "LEFT JOIN u.roles r " +
            "LEFT JOIN u.warehouseIds w " +
            "WHERE u.company.id = :companyId " +
            "  AND (:keyword IS NULL OR :keyword = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "      OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "      OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "  AND (:role IS NULL OR :role = '' OR r.name = :role) " +
            "  AND (:warehouseId IS NULL OR w = :warehouseId) " +
            "  AND (:enabled IS NULL OR u.enabled = :enabled)")
    Page<User> searchUsers(
            @Param("companyId") Long companyId,
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("warehouseId") Long warehouseId,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );
}
