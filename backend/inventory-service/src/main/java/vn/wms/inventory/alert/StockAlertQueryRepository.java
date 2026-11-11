package vn.wms.inventory.alert;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StockItem;

import java.util.Collection;
import java.util.List;

/**
 * Truy vấn chỉ đọc của phần cảnh báo tồn kho, tách khỏi {@code StockItemRepository} để không đụng code của các task
 * khác. Toàn bộ là JPQL nên Hibernate tự thêm điều kiện {@code company_id} của doanh nghiệp hiện tại cho cả
 * {@code stock_items}, {@code bin_locations} và {@code warehouses}.
 */
public interface StockAlertQueryRepository extends Repository<StockItem, Long> {

    /**
     * Mọi dòng tồn (kể cả dòng đã về 0, vì dòng về 0 gần đây vẫn là một lần biến động) của các kho trong phạm vi,
     * nạp sẵn ô kệ và kho.
     *
     * @param allWarehouses true thì bỏ qua {@code warehouseIds} (lấy mọi kho của doanh nghiệp)
     * @param warehouseIds  danh sách kho, không được rỗng (truyền một giá trị giả khi {@code allWarehouses = true})
     */
    @Query("SELECT s FROM StockItem s JOIN FETCH s.binLocation b JOIN FETCH b.warehouse w " +
           "WHERE (:allWarehouses = true OR w.id IN :warehouseIds)")
    List<StockItem> findStockRows(@Param("allWarehouses") boolean allWarehouses,
                                  @Param("warehouseIds") Collection<Long> warehouseIds);
}
