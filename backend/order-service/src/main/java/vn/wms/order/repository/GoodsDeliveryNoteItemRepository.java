package vn.wms.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.wms.order.entity.GoodsDeliveryNoteItem;

import java.util.List;

@Repository
public interface GoodsDeliveryNoteItemRepository extends JpaRepository<GoodsDeliveryNoteItem, Long> {

    List<GoodsDeliveryNoteItem> findByGoodsDeliveryNoteId(Long gdnId);

    List<GoodsDeliveryNoteItem> findBySalesOrderItemId(Long soItemId);
}
