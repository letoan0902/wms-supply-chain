package vn.wms.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.inventory.entity.StockTransfer;
import vn.wms.inventory.entity.StockTransferStatus;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long>, JpaSpecificationExecutor<StockTransfer> {

    Optional<StockTransfer> findByTransferNumber(String transferNumber);

    boolean existsByTransferNumber(String transferNumber);

    Page<StockTransfer> findByStatus(StockTransferStatus status, Pageable pageable);

    Page<StockTransfer> findBySourceWarehouseId(Long sourceWarehouseId, Pageable pageable);

    Page<StockTransfer> findByDestinationWarehouseId(Long destinationWarehouseId, Pageable pageable);

    Page<StockTransfer> findBySourceWarehouseIdInOrDestinationWarehouseIdIn(
            Collection<Long> sourceIds, Collection<Long> destIds, Pageable pageable);

    @Query("SELECT COUNT(t) FROM StockTransfer t WHERE t.transferNumber LIKE CONCAT(:prefix, '%')")
    long countByTransferNumberPrefix(@Param("prefix") String prefix);
}

