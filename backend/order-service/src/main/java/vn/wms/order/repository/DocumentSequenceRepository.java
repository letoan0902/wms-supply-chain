package vn.wms.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.order.entity.DocumentSequence;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    /**
     * Khóa dòng bộ đếm (SELECT ... FOR UPDATE) của doanh nghiệp hiện tại. Truy vấn JPQL nên Hibernate tự thêm
     * điều kiện company_id: doanh nghiệp khác dùng cùng tiền tố có dòng riêng và không bị khóa theo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentSequence s WHERE s.prefix = :prefix")
    Optional<DocumentSequence> findByPrefixForUpdate(@Param("prefix") String prefix);
}
