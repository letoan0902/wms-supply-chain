package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.wms.inventory.entity.StockDispatch;

@Repository
public interface StockDispatchRepository extends JpaRepository<StockDispatch, Long> {

    /** Truy vấn dẫn xuất nên được Hibernate lọc theo doanh nghiệp hiện tại (@TenantId). */
    boolean existsByMessageId(String messageId);

    /** Bản ghi trừ tồn theo khóa (messageId của sự kiện cũ hoặc dispatchKey của phiếu xuất, WMS-20.4). */
    java.util.Optional<StockDispatch> findByMessageId(String messageId);
}
