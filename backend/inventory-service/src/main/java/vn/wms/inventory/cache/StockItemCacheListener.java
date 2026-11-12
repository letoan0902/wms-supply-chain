package vn.wms.inventory.cache;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import vn.wms.inventory.entity.StockItem;

/**
 * Callback JPA của {@code StockItem}: mỗi lần Hibernate thêm, sửa hoặc xóa một dòng tồn thì báo cho
 * {@link StockCacheInvalidator}, nơi gom lại và vô hiệu bộ nhớ đệm sau khi giao dịch commit (WMS-19.1).
 *
 * <p>Hibernate tạo listener qua {@code SpringBeanContainer} mà Spring Boot cấu hình sẵn, nên phụ thuộc được tiêm qua
 * hàm khởi tạo. Câu {@code UPDATE} JPQL hàng loạt hoặc SQL gốc sẽ không qua callback này; hiện inventory-service không
 * có câu nào như vậy trên {@code stock_items}, ai thêm thì phải tự gọi {@link StockCacheInvalidator#stockChanged}.
 */
public class StockItemCacheListener {

    private final StockCacheInvalidator invalidator;

    public StockItemCacheListener(StockCacheInvalidator invalidator) {
        this.invalidator = invalidator;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    void stockItemChanged(StockItem item) {
        invalidator.stockChanged(item.getCompanyId(), item.getProductId());
    }
}
