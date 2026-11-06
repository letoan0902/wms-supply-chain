package vn.wms.common.tenant.sample;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SampleItemRepository extends JpaRepository<SampleItem, Long> {

    Optional<SampleItem> findByCode(String code);

    @Query("SELECT s FROM SampleItem s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY s.code")
    List<SampleItem> searchByName(@Param("keyword") String keyword);

    @Modifying
    @Transactional
    @Query("UPDATE SampleItem s SET s.name = :name")
    int renameAll(@Param("name") String name);

    @Modifying
    @Transactional
    @Query("DELETE FROM SampleItem s")
    int deleteEverything();

    /** Truy vấn SQL gốc không được Hibernate lọc theo doanh nghiệp: đếm toàn bộ dòng của mọi doanh nghiệp. */
    @Query(value = "SELECT COUNT(*) FROM sample_items", nativeQuery = true)
    long countAllNative();

    @Query(value = "SELECT company_id FROM sample_items WHERE id = :id", nativeQuery = true)
    Long companyIdOfNative(@Param("id") Long id);
}
