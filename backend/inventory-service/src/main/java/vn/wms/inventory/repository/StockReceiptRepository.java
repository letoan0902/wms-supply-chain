package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.wms.inventory.entity.StockReceipt;

@Repository
public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    /** Truy vấn dẫn xuất nên được Hibernate lọc theo doanh nghiệp hiện tại (@TenantId). */
    boolean existsByGrnId(Long grnId);
}
