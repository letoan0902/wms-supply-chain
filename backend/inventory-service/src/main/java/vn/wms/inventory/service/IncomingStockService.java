package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.inventory.dto.IncomingStockResponse;
import vn.wms.inventory.dto.ProductIncomingSummaryResponse;
import vn.wms.inventory.entity.IncomingStock;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.IncomingStockRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý nghiệp vụ quản lý Tồn hàng đang trên đường về kho (Incoming Stock - WMS-09.2).
 */
@Service
public class IncomingStockService {

    private static final Logger log = LoggerFactory.getLogger(IncomingStockService.class);

    private final IncomingStockRepository incomingStockRepository;
    private final WarehouseRepository warehouseRepository;

    public IncomingStockService(IncomingStockRepository incomingStockRepository,
                                WarehouseRepository warehouseRepository) {
        this.incomingStockRepository = incomingStockRepository;
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * Xử lý sự kiện Đơn mua hàng (PO) được duyệt (APPROVED) từ RabbitMQ:
     * Tự động cộng dồn số lượng hàng sắp về kho vào chỉ tiêu 'Incoming Stock' (FR10, WMS-09.2).
     * Đảm bảo tính Idempotent: nhận lại cùng đơn mua sẽ không bị cộng dồn trùng lặp.
     */
    @Transactional
    public List<IncomingStock> processPurchaseOrderCreated(OrderCreatedEvent event, String messageId) {
        if (event == null) {
            log.warn("Bỏ qua sự kiện rỗng");
            return List.of();
        }

        if (event.orderType() != OrderType.PURCHASE) {
            log.debug("Bỏ qua sự kiện không phải đơn mua: orderNumber={}, type={}", event.orderNumber(), event.orderType());
            return List.of();
        }

        Long warehouseId = event.warehouseId();
        if (warehouseId == null) {
            log.warn("Đơn mua hàng {} không có thông tin kho nhận hàng, bỏ qua cập nhật incoming stock", event.orderNumber());
            return List.of();
        }

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho hàng với id: " + warehouseId));

        if (!warehouse.isActive()) {
            log.warn("Kho hàng '{}' ({}) đang bị khóa/ngừng hoạt động, nhưng vẫn ghi nhận incoming stock cho đơn {}",
                    warehouse.getName(), warehouse.getCode(), event.orderNumber());
        }

        List<IncomingStock> savedList = new ArrayList<>();
        List<OrderLine> items = event.items();
        if (items == null || items.isEmpty()) {
            log.info("Đơn mua hàng {} không có dòng sản phẩm nào", event.orderNumber());
            return List.of();
        }

        for (OrderLine item : items) {
            if (item.productId() == null) {
                log.warn("Bỏ qua dòng hàng không có productId trong đơn {}", event.orderNumber());
                continue;
            }

            // Kiểm tra trùng lặp (Idempotency check theo order_id và product_id)
            if (incomingStockRepository.existsByOrderIdAndProductId(event.orderId(), item.productId())) {
                log.info("Dòng sản phẩm productId={} của đơn mua {} đã được ghi nhận incoming stock trước đó, bỏ qua",
                        item.productId(), event.orderNumber());
                continue;
            }

            BigDecimal qty = item.quantity() != null ? item.quantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Số lượng dòng sản phẩm {} trong đơn {} <= 0, bỏ qua", item.skuCode(), event.orderNumber());
                continue;
            }

            IncomingStock incomingStock = IncomingStock.builder()
                    .warehouse(warehouse)
                    .productId(item.productId())
                    .skuCode(item.skuCode() != null ? item.skuCode() : "UNKNOWN")
                    .productName(item.productName())
                    .orderId(event.orderId())
                    .orderNumber(event.orderNumber() != null ? event.orderNumber() : "PO-" + event.orderId())
                    .quantity(qty)
                    .uom(item.uom())
                    .status("INCOMING")
                    .messageId(messageId)
                    .build();

            IncomingStock saved = incomingStockRepository.save(incomingStock);
            savedList.add(saved);

            log.info("Đã cập nhật Incoming Stock thành công: kho='{}', mã đơn='{}', sku='{}', số lượng={}",
                    warehouse.getCode(), incomingStock.getOrderNumber(), incomingStock.getSkuCode(), incomingStock.getQuantity());
        }

        return savedList;
    }

    /**
     * Tra cứu danh sách hàng đang về theo kho và trạng thái.
     */
    @Transactional(readOnly = true)
    public List<IncomingStockResponse> getIncomingStocksByWarehouse(Long warehouseId, String status) {
        String targetStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : "INCOMING";
        return incomingStockRepository.findByWarehouseIdAndStatus(warehouseId, targetStatus)
                .stream()
                .map(IncomingStockResponse::from)
                .toList();
    }

    /**
     * Tra cứu tổng số lượng hàng đang về (Incoming Quantity) của một SKU tại một kho.
     */
    @Transactional(readOnly = true)
    public ProductIncomingSummaryResponse getProductIncomingSummary(Long warehouseId, Long productId) {
        BigDecimal incomingQty = incomingStockRepository.calculateIncomingQuantity(warehouseId, productId);
        return new ProductIncomingSummaryResponse(productId, warehouseId, incomingQty);
    }

    /**
     * Tra cứu tổng số lượng hàng đang về của một SKU trên toàn bộ hệ thống kho.
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalIncomingQuantityByProduct(Long productId) {
        return incomingStockRepository.calculateTotalIncomingQuantityByProduct(productId);
    }

    /**
     * Tra cứu tổng số lượng hàng đang về của tất cả các mặt hàng tại một kho.
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalIncomingQuantityByWarehouse(Long warehouseId) {
        return incomingStockRepository.calculateTotalIncomingQuantityByWarehouse(warehouseId);
    }
}

