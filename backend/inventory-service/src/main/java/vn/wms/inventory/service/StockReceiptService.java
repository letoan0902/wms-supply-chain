package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.IncomingStock;
import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReceipt;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.IncomingStockRepository;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReceiptRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service xử lý Nhập kho thực tế (Stock Receipt): Tăng OnHand Stock, Giảm Incoming Stock,
 * lưu trữ vị trí ô/kệ và quản lý Lô hàng (FR11, FR12, FR13 - WMS-10.3).
 */
@Service
public class StockReceiptService {

    private static final Logger log = LoggerFactory.getLogger(StockReceiptService.class);

    private final IncomingStockRepository incomingStockRepository;
    private final StockItemRepository stockItemRepository;
    private final InventoryLotRepository inventoryLotRepository;
    private final BinLocationRepository binLocationRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockReceiptRepository stockReceiptRepository;

    public StockReceiptService(IncomingStockRepository incomingStockRepository,
                               StockItemRepository stockItemRepository,
                               InventoryLotRepository inventoryLotRepository,
                               BinLocationRepository binLocationRepository,
                               WarehouseRepository warehouseRepository,
                               StockReceiptRepository stockReceiptRepository) {
        this.incomingStockRepository = incomingStockRepository;
        this.stockItemRepository = stockItemRepository;
        this.inventoryLotRepository = inventoryLotRepository;
        this.binLocationRepository = binLocationRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockReceiptRepository = stockReceiptRepository;
    }

    /**
     * Xử lý sự kiện order.received từ order-service khi Phiếu nhập kho (GRN) được xác nhận:
     * 1. Giảm tồn hàng đang về (Incoming Stock) của đơn PO tương ứng.
     * 2. Tăng tồn thực tế (OnHand Stock) tại vị trí ô/kệ lưu trữ.
     * 3. Lưu trữ và cập nhật tồn theo Lô hàng (Batch/Lot, MfgDate, ExpDate).
     */
    @Transactional
    public void processOrderReceived(OrderReceivedEvent event, String messageId) {
        if (event == null || event.warehouseId() == null) {
            log.warn("Bỏ qua sự kiện order.received do dữ liệu rỗng hoặc thiếu warehouseId");
            return;
        }

        Warehouse warehouse = warehouseRepository.findById(event.warehouseId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho hàng với ID: " + event.warehouseId()));

        log.info("Bắt đầu xử lý nhập kho cho GRN '{}' (Đơn PO: '{}', Kho: '{}', messageId={})",
                event.grnNumber(), event.poNumber(), warehouse.getCode(), messageId);

        List<ReceivedItemLine> items = event.items();
        if (items == null || items.isEmpty()) {
            log.warn("GRN {} không có dòng mặt hàng nào được ghi nhận", event.grnNumber());
            return;
        }

        // Chống cộng tồn hai lần cho cùng một GRN (idempotency lưu trong DB, không mất khi restart):
        // sự kiện giao lại thấy GRN đã có trong stock_receipts thì bỏ qua. Hai lần giao đồng thời thì lần sau
        // vấp ràng buộc duy nhất (company_id, grn_id), transaction rollback, lần thử lại sẽ thấy bản ghi và bỏ qua.
        if (event.grnId() != null) {
            if (stockReceiptRepository.existsByGrnId(event.grnId())) {
                log.info("Bỏ qua sự kiện order.received: phiếu nhập {} (grnId={}) đã được cộng tồn trước đó (messageId={})",
                        event.grnNumber(), event.grnId(), messageId);
                return;
            }
            StockReceipt receipt = new StockReceipt();
            receipt.setGrnId(event.grnId());
            receipt.setGrnNumber(event.grnNumber());
            receipt.setPoId(event.poId());
            receipt.setWarehouseId(warehouse.getId());
            receipt.setMessageId(messageId);
            stockReceiptRepository.saveAndFlush(receipt);
        } else {
            log.warn("Sự kiện order.received của phiếu nhập {} thiếu grnId, không kiểm tra được trùng lặp", event.grnNumber());
        }

        for (ReceivedItemLine line : items) {
            BigDecimal acceptedQty = line.acceptedQuantity() != null ? line.acceptedQuantity() : BigDecimal.ZERO;
            if (acceptedQty.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("Dòng hàng {} không có số lượng đạt (Accepted = {}), bỏ qua cập nhật tồn kho",
                        line.skuCode(), acceptedQty);
                continue;
            }

            // 1. Giảm tồn hàng đang về (Incoming Stock) của Đơn mua hàng PO
            reduceIncomingStock(event.poId(), event.poNumber(), warehouse.getId(), line.productId(), line.skuCode(), acceptedQty);

            // 2. Xác định vị trí ô/kệ lưu kho (Bin Location)
            BinLocation binLocation = resolveBinLocation(warehouse, line.locationId(), line.locationCode());

            // Cùng một số lô cho stock_items và inventory_lots để LotService đồng bộ khóa/trừ tồn giữa hai bảng
            // theo (số lô, ô, sản phẩm); dòng không có số lô thì dùng số lô sinh từ mã GRN.
            String lotNumber = (line.batchNumber() != null && !line.batchNumber().isBlank())
                    ? line.batchNumber().trim()
                    : ("LOT-" + event.grnNumber());

            // 3. Tăng tồn OnHand vào bảng stock_items
            updateStockItem(binLocation, line, lotNumber, acceptedQty);

            // 4. Lưu tồn chi tiết theo Lô & Vị trí ô vào bảng inventory_lots (WMS-10.2 - FR12)
            updateInventoryLot(binLocation, line, lotNumber, acceptedQty);
        }

        log.info("Đã hoàn tất xử lý tăng OnHand và giảm Incoming stock cho phiếu nhập GRN '{}'", event.grnNumber());
    }

    private void reduceIncomingStock(Long poId, String poNumber, Long warehouseId, Long productId, String skuCode, BigDecimal acceptedQty) {
        if (poId == null || productId == null) {
            return;
        }

        Optional<IncomingStock> incOpt = incomingStockRepository.findByOrderIdAndProductIdForUpdate(poId, productId)
                .stream().findFirst();
        if (incOpt.isPresent()) {
            IncomingStock inc = incOpt.get();
            BigDecimal currentQty = inc.getQuantity() != null ? inc.getQuantity() : BigDecimal.ZERO;
            BigDecimal newQty = currentQty.subtract(acceptedQty).max(BigDecimal.ZERO);
            inc.setQuantity(newQty);
            if (newQty.compareTo(BigDecimal.ZERO) == 0) {
                inc.setStatus("RECEIVED");
            }
            incomingStockRepository.save(inc);
            log.info("Đã giảm Incoming stock cho PO {} (SKU: {}): giảm {} -> còn lại {}",
                    poNumber, skuCode, acceptedQty, newQty);
        } else {
            log.warn("Không tìm thấy Incoming stock cho PO {} (ID: {}) với sản phẩm ID {} để giảm tồn đang về",
                    poNumber, poId, productId);
        }
    }

    private BinLocation resolveBinLocation(Warehouse warehouse, Long locationId, String locationCode) {
        if (locationId != null) {
            Optional<BinLocation> opt = binLocationRepository.findById(locationId);
            if (opt.isPresent()) {
                BinLocation bin = opt.get();
                if (bin.getWarehouse() != null && !warehouse.getId().equals(bin.getWarehouse().getId())) {
                    log.warn("Vị trí ô kệ ID {} thuộc kho ID {}, khác với kho nhập {}",
                            locationId, bin.getWarehouse().getId(), warehouse.getId());
                } else {
                    return bin;
                }
            }
        }

        if (locationCode != null && !locationCode.isBlank()) {
            Optional<BinLocation> opt = binLocationRepository.findByWarehouseIdAndCode(warehouse.getId(), locationCode.trim());
            if (opt.isPresent()) {
                return opt.get();
            }
            Optional<BinLocation> globalOpt = binLocationRepository.findByCode(locationCode.trim());
            if (globalOpt.isPresent() && globalOpt.get().getWarehouse() != null
                    && warehouse.getId().equals(globalOpt.get().getWarehouse().getId())) {
                return globalOpt.get();
            }
        }

        // Nếu không chỉ định vị trí hoặc vị trí không thuộc kho, lấy ô đầu tiên có sẵn trong kho
        List<BinLocation> availableBins = binLocationRepository.findByWarehouseIdOrderByZoneAscRackAscBinAsc(warehouse.getId());
        if (!availableBins.isEmpty()) {
            return availableBins.get(0);
        }

        throw ApiException.badRequest("Kho '" + warehouse.getName() + "' chưa có vị trí ô kệ nào để lưu cất hàng");
    }

    private void updateStockItem(BinLocation binLocation, ReceivedItemLine line, String lotNo, BigDecimal quantity) {
        // Khóa bi quan dòng tồn để không ghi đè số lượng do phiếu xuất/nhập khác đang cập nhật đồng thời
        Optional<StockItem> opt = stockItemRepository.findByBinLocationIdAndProductIdAndLotNoForUpdate(
                binLocation.getId(), line.productId(), lotNo).stream().findFirst();

        if (opt.isPresent()) {
            StockItem stockItem = opt.get();
            stockItem.setQuantity(stockItem.getQuantity().add(quantity));
            if (stockItem.getMfgDate() == null && line.mfgDate() != null) {
                stockItem.setMfgDate(line.mfgDate());
            }
            if (stockItem.getExpDate() == null && line.expDate() != null) {
                stockItem.setExpDate(line.expDate());
            }
            stockItemRepository.save(stockItem);
            log.info("Cộng thêm {} vào StockItem ID: {}, vị trí: {}, tồn mới: {}",
                    quantity, stockItem.getId(), binLocation.getCode(), stockItem.getQuantity());
        } else {
            StockItem newItem = new StockItem();
            newItem.setProductId(line.productId());
            newItem.setBinLocation(binLocation);
            newItem.setLotNo(lotNo);
            newItem.setMfgDate(line.mfgDate());
            newItem.setExpDate(line.expDate());
            newItem.setQuantity(quantity);
            newItem.setReservedQuantity(BigDecimal.ZERO);
            newItem.setLocked(false);
            StockItem saved = stockItemRepository.save(newItem);
            log.info("Tạo mới StockItem ID: {}, vị trí: {}, số lượng: {}",
                    saved.getId(), binLocation.getCode(), saved.getQuantity());
        }
    }

    private void updateInventoryLot(BinLocation binLocation, ReceivedItemLine line, String lotNumber, BigDecimal quantity) {
        Optional<InventoryLot> opt = inventoryLotRepository.findByLotNumberAndBinLocationIdAndProductIdForUpdate(
                lotNumber, binLocation.getId(), line.productId());

        if (opt.isPresent()) {
            InventoryLot lot = opt.get();
            lot.setQuantity(lot.getQuantity().add(quantity));
            if (lot.getMfgDate() == null && line.mfgDate() != null) {
                lot.setMfgDate(line.mfgDate());
            }
            if (lot.getExpDate() == null && line.expDate() != null) {
                lot.setExpDate(line.expDate());
            }
            inventoryLotRepository.save(lot);
            log.info("Cộng thêm {} vào InventoryLot ID: {}, Lô số: {}, vị trí: {}, tồn mới: {}",
                    quantity, lot.getId(), lot.getLotNumber(), binLocation.getCode(), lot.getQuantity());
        } else {
            InventoryLot newLot = new InventoryLot();
            newLot.setLotNumber(lotNumber);
            newLot.setProductId(line.productId());
            newLot.setBinLocation(binLocation);
            newLot.setMfgDate(line.mfgDate());
            newLot.setExpDate(line.expDate());
            newLot.setQuantity(quantity);
            newLot.setReservedQuantity(BigDecimal.ZERO);
            newLot.setLocked(false);
            InventoryLot saved = inventoryLotRepository.save(newLot);
            log.info("Tạo mới InventoryLot ID: {}, Lô số: {}, vị trí: {}, số lượng: {}",
                    saved.getId(), saved.getLotNumber(), binLocation.getCode(), saved.getQuantity());
        }
    }
}
