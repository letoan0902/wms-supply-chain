package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StockItem;

import java.math.BigDecimal;
import java.util.List;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    List<StockItem> findByProductId(Long productId);

    List<StockItem> findByBinLocationId(Long binLocationId);

    @Query("SELECT COUNT(s) > 0 FROM StockItem s WHERE s.binLocation.warehouse.id = :warehouseId AND s.quantity > 0")
    boolean existsActiveStockByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COUNT(s) > 0 FROM StockItem s WHERE s.binLocation.id = :binLocationId AND s.quantity > 0")
    boolean existsActiveStockByBinLocationId(@Param("binLocationId") Long binLocationId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockItem s")
    BigDecimal sumTotalQuantity();

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockItem s WHERE s.binLocation.warehouse.id = :warehouseId")
    BigDecimal sumQuantityByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COUNT(DISTINCT s.productId) FROM StockItem s WHERE s.quantity > 0")
    long countDistinctActiveProducts();

    @Query("SELECT COUNT(DISTINCT s.binLocation.id) FROM StockItem s WHERE s.binLocation.warehouse.id = :warehouseId AND s.quantity > 0")
    long countOccupiedLocationsByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COUNT(DISTINCT s.binLocation.id) FROM StockItem s WHERE s.quantity > 0")
    long countTotalOccupiedLocations();

    @Query("SELECT s.binLocation.id, COUNT(s.id), COALESCE(SUM(s.quantity), 0) FROM StockItem s WHERE s.binLocation.warehouse.id = :warehouseId GROUP BY s.binLocation.id")
    List<Object[]> findStockStatsByWarehouseId(@Param("warehouseId") Long warehouseId);
}
