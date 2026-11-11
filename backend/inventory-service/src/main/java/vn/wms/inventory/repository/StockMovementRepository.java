package vn.wms.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.inventory.entity.StockMovement;
import vn.wms.inventory.entity.StockMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Truy vấn sổ kho bằng JPQL, được Hibernate tự lọc theo doanh nghiệp hiện tại (@TenantId).
 * Phần tổng hợp có GROUP BY và phân trang của báo cáo Xuất - Nhập - Tồn nằm ở {@link StockReportQueryRepository}.
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** Tồn của một sản phẩm tại một kho tính đến trước thời điểm {@code before} (không gồm {@code before}). */
    @Query("SELECT COALESCE(SUM(m.quantityIn - m.quantityOut), 0) FROM StockMovement m " +
            "WHERE m.warehouseId = :warehouseId AND m.productId = :productId AND m.occurredAt < :before")
    BigDecimal sumBalanceBefore(@Param("warehouseId") Long warehouseId,
                                @Param("productId") Long productId,
                                @Param("before") Instant before);

    /**
     * Tồn của một sản phẩm tại một kho tính đến ngay trước một dòng sổ cụ thể, theo thứ tự (thời điểm, id).
     * Dùng để tính số dư lũy kế cho trang thứ hai trở đi của thẻ kho.
     */
    @Query("SELECT COALESCE(SUM(m.quantityIn - m.quantityOut), 0) FROM StockMovement m " +
            "WHERE m.warehouseId = :warehouseId AND m.productId = :productId " +
            "AND (m.occurredAt < :occurredAt OR (m.occurredAt = :occurredAt AND m.id < :id))")
    BigDecimal sumBalanceBeforeLine(@Param("warehouseId") Long warehouseId,
                                    @Param("productId") Long productId,
                                    @Param("occurredAt") Instant occurredAt,
                                    @Param("id") Long id);

    /** Tổng nhập, tổng xuất của một sản phẩm tại một kho trong [from, to). Trả một dòng gồm hai giá trị. */
    @Query("SELECT COALESCE(SUM(m.quantityIn), 0), COALESCE(SUM(m.quantityOut), 0) FROM StockMovement m " +
            "WHERE m.warehouseId = :warehouseId AND m.productId = :productId " +
            "AND m.occurredAt >= :from AND m.occurredAt < :to")
    List<Object[]> sumInOutBetween(@Param("warehouseId") Long warehouseId,
                                   @Param("productId") Long productId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    /** Các dòng sổ của một sản phẩm tại một kho trong [from, to), thứ tự sắp xếp lấy từ {@code pageable}. */
    @Query(value = "SELECT m FROM StockMovement m " +
            "WHERE m.warehouseId = :warehouseId AND m.productId = :productId " +
            "AND m.occurredAt >= :from AND m.occurredAt < :to",
            countQuery = "SELECT COUNT(m) FROM StockMovement m " +
                    "WHERE m.warehouseId = :warehouseId AND m.productId = :productId " +
                    "AND m.occurredAt >= :from AND m.occurredAt < :to")
    Page<StockMovement> findLines(@Param("warehouseId") Long warehouseId,
                                  @Param("productId") Long productId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);

    /**
     * Ảnh chụp mã SKU, tên, đơn vị tính gần nhất của từng sản phẩm (dòng sổ mới nhất có mã SKU).
     * Kết quả: productId, skuCode, productName, uom.
     */
    @Query("SELECT m.productId, m.skuCode, m.productName, m.uom FROM StockMovement m WHERE m.id IN (" +
            "SELECT MAX(m2.id) FROM StockMovement m2 WHERE m2.productId IN :productIds AND m2.skuCode IS NOT NULL " +
            "GROUP BY m2.productId)")
    List<Object[]> findLatestProductSnapshots(@Param("productIds") Collection<Long> productIds);

    /** Các sản phẩm khác nhau có dòng sổ mang số lô này. */
    @Query("SELECT DISTINCT m.productId FROM StockMovement m WHERE m.lotNo = :lotNo")
    List<Long> findDistinctProductIdsByLotNo(@Param("lotNo") String lotNo);

    /** Các sản phẩm khác nhau có dòng sổ mang số lô này trong các kho được xem (WMS-17.2). */
    @Query("SELECT DISTINCT m.productId FROM StockMovement m WHERE m.lotNo = :lotNo AND m.warehouseId IN :warehouseIds")
    List<Long> findDistinctProductIdsByLotNoInWarehouses(@Param("lotNo") String lotNo,
                                                         @Param("warehouseIds") Collection<Long> warehouseIds);

    /** Toàn bộ dòng sổ của một số lô của một sản phẩm, theo thứ tự thời gian. */
    List<StockMovement> findByLotNoAndProductIdOrderByOccurredAtAscIdAsc(String lotNo, Long productId);

    /** Các dòng sổ của một chứng từ cho một sản phẩm theo loại biến động (ví dụ các dòng nhận của phiếu điều chuyển). */
    List<StockMovement> findByDocumentTypeAndDocumentIdAndProductIdAndMovementTypeOrderByOccurredAtAscIdAsc(
            String documentType, Long documentId, Long productId, StockMovementType movementType);
}
