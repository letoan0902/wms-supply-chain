package vn.wms.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.order.entity.GoodsReceiptNoteItem;

import java.util.List;

public interface GoodsReceiptNoteItemRepository extends JpaRepository<GoodsReceiptNoteItem, Long> {

    List<GoodsReceiptNoteItem> findByGoodsReceiptNoteId(Long grnId);

    List<GoodsReceiptNoteItem> findByPurchaseOrderItemId(Long poItemId);
}
