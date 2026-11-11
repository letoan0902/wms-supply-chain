package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.GeneratePickingListRequest;
import vn.wms.inventory.dto.PickingItemDemand;
import vn.wms.inventory.dto.PickingItemResponse;
import vn.wms.inventory.dto.PickingListResponse;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service tính toán Thuật toán Sinh Picking List & Gợi ý FEFO/FIFO theo Lộ trình ngắn nhất (Zone -> Rack -> Bin) (WMS-12.1 - FR12, FR17).
 */
@Service
public class PickingService {

    private static final Logger log = LoggerFactory.getLogger(PickingService.class);

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final WarehouseRepository warehouseRepository;

    public PickingService(StockItemRepository stockItemRepository,
                          StockReservationRepository stockReservationRepository,
                          WarehouseRepository warehouseRepository) {
        this.stockItemRepository = stockItemRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * Tự động sinh danh sách lấy hàng (Picking List) gợi ý vị trí ô kệ theo lộ trình ngắn nhất
     * (Zone -> Rack -> Bin) và ưu tiên lô theo FEFO (hạn gần nhất xuất trước) / FIFO (nhập trước xuất trước).
     *
     * @param request Yêu cầu lấy hàng gồm kho xuất và danh sách mặt hàng
     * @return Danh sách lấy hàng đã được sắp xếp tối ưu thứ tự di chuyển
     */
    @Transactional(readOnly = true)
    public PickingListResponse generatePickingList(GeneratePickingListRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Yêu cầu sinh danh sách lấy hàng không được để trống");
        }
        if (request.warehouseId() == null) {
            throw ApiException.badRequest("warehouseId không được để trống");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("Danh sách mặt hàng cần lấy không được để trống");
        }

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho hàng ID: " + request.warehouseId()));

        if (!warehouse.isActive()) {
            throw ApiException.badRequest("Kho hàng '" + warehouse.getName() + "' đang tạm ngưng hoạt động");
        }

        log.info("Bắt đầu sinh Picking List tối ưu FEFO/FIFO và lộ trình cho đơn {} tại kho {}",
                request.orderNumber(), warehouse.getCode());

        LocalDate today = LocalDate.now();

        // Nạp danh sách giữ chỗ ACTIVE của đơn này (nếu có) để tính lượng hàng đã dành riêng cho đơn
        Map<Long, BigDecimal> reservedForThisOrder = request.orderId() != null
                ? stockReservationRepository.findByOrderIdAndStatus(request.orderId(), "ACTIVE").stream()
                .collect(Collectors.toMap(
                        r -> r.getStockItem().getId(),
                        StockReservation::getReservedQuantity,
                        BigDecimal::add
                ))
                : Map.of();

        List<PickingCandidate> candidates = new ArrayList<>();

        for (PickingItemDemand demand : request.items()) {
            Long productId = demand.productId();
            BigDecimal remainingQty = demand.quantity() != null ? demand.quantity() : BigDecimal.ZERO;
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Truy vấn các vị trí ô/kệ có sẵn hàng theo FEFO (expDate ASC) kết hợp FIFO (id ASC)
            List<StockItem> stockItems = stockItemRepository
                    .findAvailableStockItemsByProductIdAndWarehouseId(productId, warehouse.getId(), today);

            BigDecimal allocatedForDemand = BigDecimal.ZERO;

            for (StockItem stock : stockItems) {
                if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal currentQty = stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
                BigDecimal currentReserved = stock.getReservedQuantity() != null ? stock.getReservedQuantity() : BigDecimal.ZERO;

                // Lượng giữ chỗ của chính đơn này được xem là khả dụng cho chính nó
                BigDecimal orderHold = reservedForThisOrder.getOrDefault(stock.getId(), BigDecimal.ZERO);
                BigDecimal netReserved = currentReserved.subtract(orderHold).max(BigDecimal.ZERO);
                BigDecimal availableToPick = currentQty.subtract(netReserved).max(BigDecimal.ZERO);

                if (availableToPick.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal pickQty = remainingQty.min(availableToPick);
                candidates.add(new PickingCandidate(
                        demand.productId(),
                        demand.skuCode() != null ? demand.skuCode() : (stock.getLotNo() != null ? stock.getLotNo() : "SKU-" + productId),
                        demand.productName() != null ? demand.productName() : "Sản phẩm #" + productId,
                        demand.uom(),
                        stock.getBinLocation(),
                        stock.getLotNo(),
                        stock.getExpDate(),
                        stock.getMfgDate(),
                        pickQty,
                        currentQty
                ));

                allocatedForDemand = allocatedForDemand.add(pickQty);
                remainingQty = remainingQty.subtract(pickQty);
            }

            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("Đơn {} thiếu {} đơn vị cho sản phẩm ID {} tại kho {}",
                        request.orderNumber(), remainingQty, productId, warehouse.getCode());
            }
        }

        // =========================================================================
        // THUẬT TOÁN TỐI ƯU LỘ TRÌNH LẤY HÀNG (ROUTING ALGORITHM): Zone -> Rack -> Bin
        // Giúp nhân viên kho di chuyển theo một đường thẳng liên tục, giảm thiểu thời gian đi vòng
        // =========================================================================
        candidates.sort(Comparator
                .comparing((PickingCandidate c) -> normalizeZone(c.binLocation != null ? c.binLocation.getZone() : null), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(c -> parseNumericPrefix(c.binLocation != null ? c.binLocation.getRack() : null))
                .thenComparingInt(c -> parseNumericPrefix(c.binLocation != null ? c.binLocation.getBin() : null))
                .thenComparing(c -> c.expDate != null ? c.expDate : LocalDate.MAX)
                .thenComparing(c -> c.lotNo != null ? c.lotNo : "")
                .thenComparing(c -> c.skuCode != null ? c.skuCode : "")
        );

        // Gán số thứ tự chặng di chuyển (Step #1, Step #2...)
        List<PickingItemResponse> orderedItems = new ArrayList<>();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        int step = 1;

        for (PickingCandidate c : candidates) {
            orderedItems.add(new PickingItemResponse(
                    step++,
                    c.productId,
                    c.skuCode,
                    c.productName,
                    c.uom,
                    c.binLocation != null ? c.binLocation.getId() : null,
                    c.binLocation != null ? c.binLocation.getCode() : "N/A",
                    c.binLocation != null ? c.binLocation.getZone() : null,
                    c.binLocation != null ? c.binLocation.getRack() : null,
                    c.binLocation != null ? c.binLocation.getBin() : null,
                    c.lotNo,
                    c.expDate,
                    c.mfgDate,
                    c.quantityToPick,
                    c.availableQuantity
            ));
            totalQuantity = totalQuantity.add(c.quantityToPick);
        }

        log.info("Hoàn tất sinh Picking List cho đơn {}: {} chặng, tổng số lượng: {}",
                request.orderNumber(), orderedItems.size(), totalQuantity);

        return new PickingListResponse(
                request.orderId(),
                request.orderNumber(),
                warehouse.getId(),
                warehouse.getName(),
                orderedItems.size(),
                totalQuantity,
                orderedItems
        );
    }

    private static String normalizeZone(String zone) {
        if (zone == null || zone.isBlank()) return "ZZZ";
        String s = zone.trim().toUpperCase();
        if (s.startsWith("ZONE-")) {
            return s.substring(5);
        }
        if (s.startsWith("ZONE")) {
            return s.substring(4);
        }
        return s;
    }

    private static int parseNumericPrefix(String s) {
        if (s == null || s.isBlank()) return 999999;
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return 999999;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 999999;
        }
    }

    private record PickingCandidate(
            Long productId,
            String skuCode,
            String productName,
            String uom,
            BinLocation binLocation,
            String lotNo,
            LocalDate expDate,
            LocalDate mfgDate,
            BigDecimal quantityToPick,
            BigDecimal availableQuantity
    ) {
    }
}
