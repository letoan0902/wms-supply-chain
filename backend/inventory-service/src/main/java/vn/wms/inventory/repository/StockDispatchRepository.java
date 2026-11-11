package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.wms.inventory.entity.StockDispatch;

@Repository
public interface StockDispatchRepository extends JpaRepository<StockDispatch, Long> {

    /** Truy vấn dẫn xuất nên được Hibernate lọc theo doanh nghiệp hiện tại (@TenantId). */
    boolean existsByMessageId(String messageId);
}
