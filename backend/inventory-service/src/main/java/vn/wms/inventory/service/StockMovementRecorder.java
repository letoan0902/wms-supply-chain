package vn.wms.inventory.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.DeductedItemDetail;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockMovement;
import vn.wms.inventory.entity.StockMovementType;
import vn.wms.inventory.entity.StockTransfer;
import vn.wms.inventory.entity.StockTransferItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockMovementRepository;
import vn.wms.inventory.repository.StockTransferRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ghi sổ kho {@code stock_movements} (WMS-17.1). Mỗi đường thay đổi {@code stock_items.quantity} gọi đúng một
 * phương thức ở đây, ngay sau khi đổi số lượng và trong cùng transaction ({@link Propagation#MANDATORY}): nghiệp
 * vụ rollback thì dòng sổ cũng rollback, sự kiện giao lại bị chặn trùng (stock_receipts, stock_dispatches) thì
 * cũng không sinh dòng sổ thứ hai.
 *
 * <p>Chức năng kiểm kê, điều chỉnh kho dùng {@link #recordAdjustment} khi áp chênh lệch vào tồn.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class StockMovementRecorder {

    private static final String REASON_SALES_DISPATCH = "SALES_DISPATCH";
    private static final String REASON_TRANSFER_OUT = "TRANSFER_OUT";

    private final StockMovementRepository stockMovementRepository;
    private final StockItemRepository stockItemRepository;
    private final StockTransferRepository stockTransferRepository;

    public StockMovementRecorder(StockMovementRepository stockMovementRepository,
                                 StockItemRepository stockItemRepository,
                                 StockTransferRepository stockTransferRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.stockItemRepository = stockItemRepository;
        this.stockTransferRepository = stockTransferRepository;
    }

    /** Nhập kho một dòng hàng của phiếu nhập GRN (sự kiện order.received). */
    public void recordReceipt(OrderReceivedEvent event, BinLocation binLocation, ReceivedItemLine line,
                              String lotNo, BigDecimal quantity) {
        StockMovement m = base(StockMovementType.RECEIPT, Instant.now(), binLocation, line.productId(), quantity);
        stockItemRepository.findByBinLocationIdAndProductIdAndLotNoForUpdate(binLocation.getId(), line.productId(), lotNo)
                .stream().findFirst().map(StockItem::getId).ifPresent(m::setStockItemId);
        m.setSkuCode(truncate(line.skuCode(), 64));
        m.setProductName(truncate(line.productName(), 255));
        m.setUom(truncate(line.uom(), 20));
        m.setLotNo(lotNo);
        m.setMfgDate(line.mfgDate());
        m.setExpDate(line.expDate());
        m.setDocumentType(StockMovement.DOC_GOODS_RECEIPT);
        m.setDocumentId(event.grnId());
        m.setDocumentNumber(truncate(event.grnNumber(), 64));
        m.setReferenceType(event.poId() != null || event.poNumber() != null ? StockMovement.REF_PURCHASE_ORDER : null);
        m.setReferenceId(event.poId());
        m.setReferenceNumber(truncate(event.poNumber(), 64));
        m.setPartnerId(event.supplierId());
        m.setPerformedBy(truncate(event.receivedBy(), 50));
        stockMovementRepository.save(m);
    }

    /**
     * Các dòng đã trừ tồn của một lần gọi {@code StockDeductionService.deductStock}: xuất bán, xuất điều chuyển
     * hoặc xuất kho khác tùy {@code request.reason()} (để trống được service coi là xuất bán, sổ kho làm giống vậy).
     */
    public void recordIssue(DeductStockRequest request, Warehouse warehouse, List<DeductedItemDetail> details,
                            String performedBy, Instant occurredAt) {
        if (details == null || details.isEmpty()) {
            return;
        }
        String reason = request.reason() != null ? request.reason().trim() : REASON_SALES_DISPATCH;
        StockMovementType type = REASON_SALES_DISPATCH.equalsIgnoreCase(reason) ? StockMovementType.SALES_DISPATCH
                : REASON_TRANSFER_OUT.equalsIgnoreCase(reason) ? StockMovementType.TRANSFER_OUT
                : StockMovementType.ISSUE_OTHER;

        StockTransfer transfer = null;
        Map<Long, StockTransferItem> transferItemByProduct = new HashMap<>();
        if (type == StockMovementType.TRANSFER_OUT && request.orderNumber() != null) {
            transfer = stockTransferRepository.findByTransferNumber(request.orderNumber()).orElse(null);
            if (transfer != null) {
                for (StockTransferItem item : transfer.getItems()) {
                    transferItemByProduct.putIfAbsent(item.getProductId(), item);
                }
            }
        }
        Map<Long, String> skuByProduct = new HashMap<>();
        if (request.items() != null) {
            for (DeductStockItemRequest item : request.items()) {
                if (item.productId() != null && item.skuCode() != null) {
                    skuByProduct.putIfAbsent(item.productId(), item.skuCode());
                }
            }
        }

        List<StockMovement> movements = new ArrayList<>(details.size());
        for (DeductedItemDetail detail : details) {
            if (detail.deductedQuantity() == null || detail.deductedQuantity().signum() == 0) {
                continue;
            }
            StockMovement m = new StockMovement();
            m.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
            m.setMovementType(type);
            m.setWarehouseId(warehouse.getId());
            m.setBinLocationId(detail.binLocationId());
            m.setBinLocationCode(truncate(detail.binLocationCode(), 80));
            m.setStockItemId(detail.stockItemId());
            m.setProductId(detail.productId());
            m.setSkuCode(truncate(detail.skuCode() != null ? detail.skuCode() : skuByProduct.get(detail.productId()), 64));
            m.setLotNo(detail.lotNo());
            m.setQuantityIn(BigDecimal.ZERO);
            m.setQuantityOut(detail.deductedQuantity());
            m.setPerformedBy(truncate(performedBy, 50));
            Optional<StockItem> stockItem = detail.stockItemId() != null
                    ? stockItemRepository.findById(detail.stockItemId()) : Optional.empty();
            stockItem.ifPresent(s -> {
                m.setMfgDate(s.getMfgDate());
                m.setExpDate(s.getExpDate());
            });
            switch (type) {
                case SALES_DISPATCH -> {
                    m.setDocumentType(StockMovement.DOC_SALES_ORDER);
                    m.setDocumentId(request.orderId());
                    m.setDocumentNumber(truncate(request.orderNumber(), 64));
                    m.setReason(truncate(request.note(), 255));
                }
                case TRANSFER_OUT -> {
                    m.setDocumentType(StockMovement.DOC_STOCK_TRANSFER);
                    m.setDocumentNumber(truncate(request.orderNumber(), 64));
                    m.setReason(truncate(request.note(), 255));
                    if (transfer != null) {
                        m.setDocumentId(transfer.getId());
                        m.setCounterpartWarehouseId(transfer.getDestinationWarehouse().getId());
                        StockTransferItem item = transferItemByProduct.get(detail.productId());
                        if (item != null) {
                            m.setProductName(truncate(item.getProductName(), 255));
                            m.setUom(truncate(item.getUom(), 20));
                        }
                    }
                }
                default -> {
                    m.setDocumentType(StockMovement.DOC_OTHER);
                    m.setDocumentId(request.orderId());
                    m.setDocumentNumber(truncate(request.orderNumber(), 64));
                    m.setReason(truncate(reason + (request.note() != null ? ": " + request.note() : ""), 255));
                }
            }
            movements.add(m);
        }
        stockMovementRepository.saveAll(movements);
    }

    /** Nhận một dòng hàng điều chuyển vào vị trí của kho đích. */
    public void recordTransferIn(StockTransfer transfer, StockTransferItem item, BinLocation binLocation,
                                 StockItem stockItem, BigDecimal quantity, String performedBy) {
        StockMovement m = base(StockMovementType.TRANSFER_IN, Instant.now(), binLocation, item.getProductId(), quantity);
        m.setStockItemId(stockItem != null ? stockItem.getId() : null);
        m.setSkuCode(truncate(item.getSkuCode(), 64));
        m.setProductName(truncate(item.getProductName(), 255));
        m.setUom(truncate(item.getUom(), 20));
        // Phiếu điều chuyển chưa lưu số lô xuất đi, hàng nhận được cộng vào dòng tồn không theo lô
        m.setLotNo(stockItem != null ? stockItem.getLotNo() : null);
        m.setDocumentType(StockMovement.DOC_STOCK_TRANSFER);
        m.setDocumentId(transfer.getId());
        m.setDocumentNumber(truncate(transfer.getTransferNumber(), 64));
        m.setCounterpartWarehouseId(transfer.getSourceWarehouse().getId());
        m.setPerformedBy(truncate(performedBy, 50));
        stockMovementRepository.save(m);
    }

    /**
     * Điều chỉnh tồn sau kiểm kê: {@code delta > 0} là điều chỉnh tăng, {@code delta < 0} là điều chỉnh giảm.
     * Gọi ngay sau khi đã cộng {@code delta} vào {@code stockItem.quantity}, trong cùng transaction.
     */
    public void recordAdjustment(StockItem stockItem, BigDecimal delta, String documentType, Long documentId,
                                 String documentNumber, String reason, String performedBy) {
        if (stockItem == null || delta == null || delta.signum() == 0) {
            return;
        }
        BinLocation bin = stockItem.getBinLocation();
        StockMovement m = new StockMovement();
        m.setOccurredAt(Instant.now());
        m.setMovementType(delta.signum() > 0 ? StockMovementType.ADJUSTMENT_IN : StockMovementType.ADJUSTMENT_OUT);
        m.setWarehouseId(bin.getWarehouse().getId());
        m.setBinLocationId(bin.getId());
        m.setBinLocationCode(truncate(bin.getCode(), 80));
        m.setStockItemId(stockItem.getId());
        m.setProductId(stockItem.getProductId());
        m.setLotNo(stockItem.getLotNo());
        m.setMfgDate(stockItem.getMfgDate());
        m.setExpDate(stockItem.getExpDate());
        m.setQuantityIn(delta.signum() > 0 ? delta : BigDecimal.ZERO);
        m.setQuantityOut(delta.signum() < 0 ? delta.negate() : BigDecimal.ZERO);
        m.setDocumentType(documentType != null ? truncate(documentType, 30) : StockMovement.DOC_OTHER);
        m.setDocumentId(documentId);
        m.setDocumentNumber(truncate(documentNumber, 64));
        m.setReason(truncate(reason, 255));
        m.setPerformedBy(truncate(performedBy, 50));
        stockMovementRepository.save(m);
    }

    private static StockMovement base(StockMovementType type, Instant occurredAt, BinLocation bin, Long productId,
                                      BigDecimal quantity) {
        StockMovement m = new StockMovement();
        m.setOccurredAt(occurredAt);
        m.setMovementType(type);
        m.setWarehouseId(bin.getWarehouse().getId());
        m.setBinLocationId(bin.getId());
        m.setBinLocationCode(truncate(bin.getCode(), 80));
        m.setProductId(productId);
        m.setQuantityIn(type.isInbound() ? quantity : BigDecimal.ZERO);
        m.setQuantityOut(type.isInbound() ? BigDecimal.ZERO : quantity);
        return m;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
