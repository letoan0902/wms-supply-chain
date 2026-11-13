package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.wms.inventory.entity.StockLedgerStart;

import java.util.Optional;

@Repository
public interface StockLedgerStartRepository extends JpaRepository<StockLedgerStart, Long> {

    /** Mốc bắt đầu sổ kho của doanh nghiệp hiện tại (truy vấn dẫn xuất nên được lọc theo @TenantId). */
    Optional<StockLedgerStart> findFirstByOrderByStartedAtAsc();
}
