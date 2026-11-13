package vn.wms.inventory.service.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.report.LotTraceResponse;
import vn.wms.inventory.dto.report.LotTraceResponse.Flow;
import vn.wms.inventory.dto.report.LotTraceResponse.Location;
import vn.wms.inventory.dto.report.LotTraceResponse.Summary;
import vn.wms.inventory.dto.report.LotTraceResponse.TimelineEvent;
import vn.wms.inventory.dto.report.LotTraceResponse.TraceNode;
import vn.wms.inventory.dto.report.LotTraceResponse.TransferInfo;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockMovement;
import vn.wms.inventory.entity.StockMovementType;
import vn.wms.inventory.entity.StockTransfer;
import vn.wms.inventory.entity.StockTransferItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.LotStockItemRepository;
import vn.wms.inventory.repository.StockMovementRepository;
import vn.wms.inventory.repository.StockTransferRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Truy vết nguồn gốc theo số lô (WMS-17.2), tính từ sổ kho {@code stock_movements} và tồn hiện tại
 * {@code stock_items}. Tên nhà cung cấp, khách hàng không lưu ở inventory-service và chưa có client sang
 * order-service nên chỉ trả ID và số chứng từ (nêu rõ trong {@code notes}).
 */
@Service
public class LotTraceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3);
    private static final Set<StockMovementType> ORIGIN_TYPES = EnumSet.of(
            StockMovementType.RECEIPT, StockMovementType.OPENING_BALANCE, StockMovementType.ADJUSTMENT_IN);
    private static final Set<StockMovementType> OUTFLOW_TYPES = EnumSet.of(
            StockMovementType.SALES_DISPATCH, StockMovementType.TRANSFER_OUT,
            StockMovementType.ISSUE_OTHER, StockMovementType.ADJUSTMENT_OUT);

    static final String NOTE_PARTNER_NAMES = "Tên nhà cung cấp và khách hàng không lưu ở dịch vụ kho; truy vết hiển thị "
            + "ID nhà cung cấp, số đơn mua, số đơn bán để tra cứu ở màn hình đơn hàng.";
    static final String NOTE_TRANSFER_LOT = "Phiếu điều chuyển chưa lưu số lô: hàng nhận tại kho đích được cộng vào tồn "
            + "không theo lô nên không truy tiếp được các lần xuất sau tại kho đích.";
    static final String NOTE_OPENING = "Một phần lô là số dư mở sổ kho: tồn có sẵn trước khi hệ thống ghi sổ kho, không còn "
            + "chứng từ nhập gốc trong hệ thống.";
    static final String NOTE_SALES_DOCUMENT = "Xuất bán ghi theo số đơn bán vì sự kiện xuất kho chưa mang mã phiếu xuất (GDN).";

    private final ReportAccess reportAccess;
    private final ReportLookups reportLookups;
    private final StockMovementRepository stockMovementRepository;
    private final LotStockItemRepository lotStockItemRepository;
    private final StockTransferRepository stockTransferRepository;

    public LotTraceService(ReportAccess reportAccess,
                           ReportLookups reportLookups,
                           StockMovementRepository stockMovementRepository,
                           LotStockItemRepository lotStockItemRepository,
                           StockTransferRepository stockTransferRepository) {
        this.reportAccess = reportAccess;
        this.reportLookups = reportLookups;
        this.stockMovementRepository = stockMovementRepository;
        this.lotStockItemRepository = lotStockItemRepository;
        this.stockTransferRepository = stockTransferRepository;
    }

    /**
     * Truy vết một số lô. Số lô trùng giữa nhiều sản phẩm mà không chỉ định {@code productId} thì trả
     * {@code 409 CONFLICT} kèm {@code data.candidates} để người dùng chọn.
     */
    @Transactional(readOnly = true)
    public LotTraceResponse trace(String lotNo, Long productId) {
        if (lotNo == null || lotNo.isBlank()) {
            throw ApiException.badRequest("Thiếu tham số bắt buộc: lotNo");
        }
        String lot = lotNo.trim();
        ReportAccess.Scope scope = reportAccess.resolve(null);

        Long product = productId != null ? productId : resolveSingleProduct(lot, scope);

        List<StockMovement> movements = stockMovementRepository.findByLotNoAndProductIdOrderByOccurredAtAscIdAsc(lot, product)
                .stream().filter(m -> scope.allows(m.getWarehouseId())).toList();
        List<StockItem> stock = lotStockItemRepository.findByLotNoAndProductId(lot, product).stream()
                .filter(s -> scope.allows(s.getBinLocation().getWarehouse().getId()))
                .toList();
        if (movements.isEmpty() && stock.isEmpty()) {
            throw ApiException.notFound("Không tìm thấy số lô '" + lot + "' trong phạm vi kho được xem");
        }

        Set<Long> warehouseIds = new HashSet<>();
        movements.forEach(m -> {
            warehouseIds.add(m.getWarehouseId());
            warehouseIds.add(m.getCounterpartWarehouseId());
        });
        Map<Long, StockTransfer> transfers = loadTransfers(movements);
        transfers.values().forEach(t -> warehouseIds.add(t.getDestinationWarehouse().getId()));
        Map<Long, Warehouse> warehouses = reportLookups.warehouses(warehouseIds);
        ReportLookups.ProductDisplay display = reportLookups.productDisplay(List.of(product))
                .getOrDefault(product, ReportLookups.ProductDisplay.EMPTY);

        LocalDate today = LocalDate.now(StockReportService.REPORT_ZONE);
        List<Location> locations = new ArrayList<>();
        BigDecimal onHand = ZERO;
        BigDecimal reserved = ZERO;
        for (StockItem s : stock) {
            BigDecimal qty = nz(s.getQuantity());
            if (qty.signum() == 0) {
                continue;
            }
            Warehouse w = s.getBinLocation().getWarehouse();
            BigDecimal res = nz(s.getReservedQuantity());
            onHand = onHand.add(qty);
            reserved = reserved.add(res);
            locations.add(new Location(s.getId(), w.getId(), w.getCode(), w.getName(), s.getBinLocation().getId(),
                    s.getBinLocation().getCode(), qty, res, s.getAvailableQuantity(), s.isLocked(), s.getLockedReason(),
                    s.getExpDate(), s.isExpired(today), s.getDaysUntilExpiry(today)));
        }

        List<Flow> origins = new ArrayList<>();
        List<Flow> outflows = new ArrayList<>();
        for (List<StockMovement> group : groupByDocument(movements).values()) {
            StockMovement first = group.get(0);
            if (ORIGIN_TYPES.contains(first.getMovementType())) {
                origins.add(toFlow(group, warehouses, null));
            } else if (OUTFLOW_TYPES.contains(first.getMovementType())) {
                TransferInfo transfer = first.getMovementType() == StockMovementType.TRANSFER_OUT
                        ? transferInfo(transfers.get(first.getDocumentId()), product, sumOut(group), warehouses) : null;
                outflows.add(toFlow(group, warehouses, transfer));
            }
        }

        List<TimelineEvent> timeline = new ArrayList<>(movements.size());
        BigDecimal running = ZERO;
        for (StockMovement m : movements) {
            running = running.add(nz(m.getQuantityIn())).subtract(nz(m.getQuantityOut()));
            Warehouse w = warehouses.get(m.getWarehouseId());
            timeline.add(new TimelineEvent(m.getId(), m.getOccurredAt(), m.getMovementType().name(),
                    m.getMovementType().getLabel(), m.getDocumentType(), m.getDocumentId(), m.getDocumentNumber(),
                    m.getReferenceType(), m.getReferenceNumber(), m.getPartnerId(), m.getWarehouseId(),
                    w != null ? w.getCode() : null, w != null ? w.getName() : null, m.getBinLocationCode(),
                    m.getCounterpartWarehouseId(), nz(m.getQuantityIn()), nz(m.getQuantityOut()), running,
                    m.getPerformedBy()));
        }

        Summary summary = new Summary(
                sumIn(movements, StockMovementType.RECEIPT),
                sumIn(movements, StockMovementType.OPENING_BALANCE),
                sumIn(movements, StockMovementType.ADJUSTMENT_IN),
                sumOut(movements, StockMovementType.SALES_DISPATCH),
                sumOut(movements, StockMovementType.TRANSFER_OUT),
                sumOut(movements, StockMovementType.ISSUE_OTHER).add(sumOut(movements, StockMovementType.ADJUSTMENT_OUT)),
                running,
                onHand,
                reserved);

        LocalDate mfgDate = stock.stream().map(StockItem::getMfgDate).filter(Objects::nonNull).findFirst()
                .orElse(movements.stream().map(StockMovement::getMfgDate).filter(Objects::nonNull).findFirst().orElse(null));
        LocalDate expDate = stock.stream().map(StockItem::getExpDate).filter(Objects::nonNull).findFirst()
                .orElse(movements.stream().map(StockMovement::getExpDate).filter(Objects::nonNull).findFirst().orElse(null));

        List<String> notes = new ArrayList<>();
        notes.add(NOTE_PARTNER_NAMES);
        if (outflows.stream().anyMatch(f -> StockMovementType.SALES_DISPATCH.name().equals(f.movementType()))) {
            notes.add(NOTE_SALES_DOCUMENT);
        }
        if (outflows.stream().anyMatch(f -> f.transfer() != null)) {
            notes.add(NOTE_TRANSFER_LOT);
        }
        if (origins.stream().anyMatch(f -> StockMovementType.OPENING_BALANCE.name().equals(f.movementType()))) {
            notes.add(NOTE_OPENING);
        }

        TraceNode tree = buildTree(lot, product, display, onHand, origins, locations, outflows);

        return new LotTraceResponse(lot, product, display.skuCode(), display.productName(), display.uom(),
                mfgDate, expDate,
                scope.warehouseIds() != null ? scope.warehouseIds().stream().sorted().toList() : null,
                summary, origins, locations, outflows, timeline, tree, notes,
                reportLookups.ledgerStartedAt(), Instant.now());
    }

    private Long resolveSingleProduct(String lot, ReportAccess.Scope scope) {
        Set<Long> candidates = new LinkedHashSet<>();
        if (scope.warehouseIds() == null) {
            candidates.addAll(stockMovementRepository.findDistinctProductIdsByLotNo(lot));
            candidates.addAll(lotStockItemRepository.findDistinctProductIdsByLotNo(lot));
        } else {
            candidates.addAll(stockMovementRepository.findDistinctProductIdsByLotNoInWarehouses(lot, scope.warehouseIds()));
            candidates.addAll(lotStockItemRepository.findDistinctProductIdsByLotNoInWarehouses(lot, scope.warehouseIds()));
        }
        if (candidates.isEmpty()) {
            throw ApiException.notFound("Không tìm thấy số lô '" + lot + "' trong phạm vi kho được xem");
        }
        if (candidates.size() > 1) {
            Map<Long, ReportLookups.ProductDisplay> names = reportLookups.productDisplay(candidates);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Long id : candidates) {
                ReportLookups.ProductDisplay p = names.getOrDefault(id, ReportLookups.ProductDisplay.EMPTY);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", id);
                item.put("skuCode", p.skuCode());
                item.put("productName", p.productName());
                list.add(item);
            }
            throw ApiException.withData(ErrorCode.CONFLICT,
                    "Số lô '" + lot + "' được dùng cho nhiều sản phẩm, vui lòng chọn sản phẩm cần truy vết",
                    Map.of("candidates", list));
        }
        return candidates.iterator().next();
    }

    private Map<Long, StockTransfer> loadTransfers(List<StockMovement> movements) {
        Set<Long> ids = new HashSet<>();
        for (StockMovement m : movements) {
            if (m.getMovementType() == StockMovementType.TRANSFER_OUT && m.getDocumentId() != null) {
                ids.add(m.getDocumentId());
            }
        }
        Map<Long, StockTransfer> result = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            stockTransferRepository.findAllById(ids).forEach(t -> result.put(t.getId(), t));
        }
        return result;
    }

    /** Gộp các dòng sổ cùng loại, cùng chứng từ, cùng kho (ví dụ một đơn bán lấy hàng từ nhiều vị trí). */
    private static Map<String, List<StockMovement>> groupByDocument(List<StockMovement> movements) {
        Map<String, List<StockMovement>> groups = new LinkedHashMap<>();
        for (StockMovement m : movements) {
            String key = m.getMovementType() + "|" + m.getDocumentType() + "|"
                    + (m.getDocumentId() != null ? "id:" + m.getDocumentId() : "no:" + m.getDocumentNumber())
                    + "|" + m.getWarehouseId()
                    // Dòng không có chứng từ (số dư mở sổ) thì gộp theo vị trí
                    + (m.getDocumentId() == null && m.getDocumentNumber() == null ? "|" + m.getBinLocationId() : "");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        return groups;
    }

    private static Flow toFlow(List<StockMovement> group, Map<Long, Warehouse> warehouses, TransferInfo transfer) {
        StockMovement first = group.get(0);
        Warehouse w = warehouses.get(first.getWarehouseId());
        BigDecimal qty = ZERO;
        Set<String> bins = new LinkedHashSet<>();
        for (StockMovement m : group) {
            qty = qty.add(first.getMovementType().isInbound() ? nz(m.getQuantityIn()) : nz(m.getQuantityOut()));
            if (m.getBinLocationCode() != null) {
                bins.add(m.getBinLocationCode());
            }
        }
        return new Flow(first.getMovementType().name(), first.getMovementType().getLabel(), first.getOccurredAt(),
                first.getDocumentType(), first.getDocumentId(), first.getDocumentNumber(),
                first.getReferenceType(), first.getReferenceId(), first.getReferenceNumber(), first.getPartnerId(),
                first.getWarehouseId(), w != null ? w.getCode() : null, w != null ? w.getName() : null,
                List.copyOf(bins), qty, first.getPerformedBy(), transfer);
    }

    private static TransferInfo transferInfo(StockTransfer transfer, Long productId, BigDecimal lotQuantity,
                                             Map<Long, Warehouse> warehouses) {
        if (transfer == null) {
            return null;
        }
        Warehouse dest = warehouses.get(transfer.getDestinationWarehouse().getId());
        BigDecimal received = null;
        BigDecimal damaged = null;
        String destBin = null;
        for (StockTransferItem item : transfer.getItems()) {
            if (productId.equals(item.getProductId())) {
                received = (received == null ? ZERO : received).add(nz(item.getReceivedQuantity()));
                damaged = (damaged == null ? ZERO : damaged).add(nz(item.getDamagedQuantity()));
                if (destBin == null && item.getDestinationBinLocation() != null) {
                    destBin = item.getDestinationBinLocation().getCode();
                }
            }
        }
        boolean isReceived = transfer.getReceivedAt() != null;
        return new TransferInfo(transfer.getId(), transfer.getTransferNumber(), transfer.getStatus().name(),
                transfer.getDestinationWarehouse().getId(),
                dest != null ? dest.getCode() : null, dest != null ? dest.getName() : null,
                lotQuantity,
                isReceived ? received : null,
                isReceived ? damaged : null,
                destBin, transfer.getReceivedAt(), transfer.getReceivedBy(), false);
    }

    private static TraceNode buildTree(String lot, Long productId, ReportLookups.ProductDisplay display,
                                       BigDecimal onHand, List<Flow> origins, List<Location> locations,
                                       List<Flow> outflows) {
        List<TraceNode> children = new ArrayList<>();
        int seq = 0;
        for (Flow f : origins) {
            seq++;
            if (StockMovementType.RECEIPT.name().equals(f.movementType())) {
                List<TraceNode> upstream = new ArrayList<>();
                if (f.referenceNumber() != null || f.referenceId() != null) {
                    List<TraceNode> supplier = f.supplierId() == null ? List.of() : List.of(node(
                            "supplier:" + f.supplierId() + ":" + seq, "SUPPLIER", "UPSTREAM",
                            "Nhà cung cấp ID " + f.supplierId(), null, null, null, f.supplierId(), null,
                            null, null, null, List.of()));
                    upstream.add(node("po:" + seq, "PURCHASE_ORDER", "UPSTREAM",
                            "Đơn mua " + (f.referenceNumber() != null ? f.referenceNumber() : "ID " + f.referenceId()),
                            null, null, f.referenceType(), f.referenceId(), f.referenceNumber(), null, null, null,
                            supplier));
                } else if (f.supplierId() != null) {
                    upstream.add(node("supplier:" + f.supplierId() + ":" + seq, "SUPPLIER", "UPSTREAM",
                            "Nhà cung cấp ID " + f.supplierId(), null, null, null, f.supplierId(), null,
                            null, null, null, List.of()));
                }
                children.add(node("origin:" + seq, "GOODS_RECEIPT", "UPSTREAM",
                        "Phiếu nhập " + nvl(f.documentNumber()) + " vào " + nvl(f.warehouseName()), f.quantity(),
                        f.occurredAt(), f.documentType(), f.documentId(), f.documentNumber(), f.warehouseId(),
                        f.warehouseName(), String.join(", ", f.binLocationCodes()), upstream));
            } else if (StockMovementType.OPENING_BALANCE.name().equals(f.movementType())) {
                children.add(node("origin:" + seq, "OPENING_BALANCE", "UPSTREAM",
                        "Số dư mở sổ kho tại " + nvl(f.warehouseName()) + " (không có chứng từ gốc)", f.quantity(),
                        f.occurredAt(), f.documentType(), null, null, f.warehouseId(), f.warehouseName(),
                        String.join(", ", f.binLocationCodes()), List.of()));
            } else {
                children.add(node("origin:" + seq, "ADJUSTMENT_IN", "UPSTREAM",
                        "Điều chỉnh tăng " + nvl(f.documentNumber()), f.quantity(), f.occurredAt(),
                        f.documentType(), f.documentId(), f.documentNumber(), f.warehouseId(), f.warehouseName(),
                        String.join(", ", f.binLocationCodes()), List.of()));
            }
        }
        for (Location l : locations) {
            String flags = (l.locked() ? ", đang khóa" : "") + (l.expired() ? ", đã hết hạn" : "");
            children.add(node("location:" + l.stockItemId(), "LOCATION", "CURRENT",
                    "Đang ở " + l.binLocationCode() + " - " + nvl(l.warehouseName()) + flags, l.quantity(), null,
                    null, null, null, l.warehouseId(), l.warehouseName(), l.binLocationCode(), List.of()));
        }
        seq = 0;
        for (Flow f : outflows) {
            seq++;
            String type = f.movementType();
            if (StockMovementType.TRANSFER_OUT.name().equals(type)) {
                List<TraceNode> dest = new ArrayList<>();
                TransferInfo t = f.transfer();
                if (t != null) {
                    String receivedText = t.receivedAt() != null
                            ? "đã nhận " + t.itemReceivedQuantity() + " (cả dòng sản phẩm của phiếu)"
                            : "chưa nhận, phiếu " + t.status();
                    dest.add(node("transfer-dest:" + seq, "TRANSFER_DESTINATION", "DOWNSTREAM",
                            "Kho đích " + nvl(t.destinationWarehouseName()) + ": " + receivedText
                                    + "; số lô không được giữ tại kho đích",
                            t.itemReceivedQuantity(), t.receivedAt(), null, null, null, t.destinationWarehouseId(),
                            t.destinationWarehouseName(), t.destinationBinLocationCode(), List.of()));
                }
                children.add(node("outflow:" + seq, "TRANSFER", "DOWNSTREAM",
                        "Điều chuyển " + nvl(f.documentNumber()) + " từ " + nvl(f.warehouseName()), f.quantity(),
                        f.occurredAt(), f.documentType(), f.documentId(), f.documentNumber(), f.warehouseId(),
                        f.warehouseName(), String.join(", ", f.binLocationCodes()), dest));
            } else if (StockMovementType.SALES_DISPATCH.name().equals(type)) {
                children.add(node("outflow:" + seq, "SALES_ORDER", "DOWNSTREAM",
                        "Xuất bán theo đơn " + nvl(f.documentNumber()) + " từ " + nvl(f.warehouseName()), f.quantity(),
                        f.occurredAt(), f.documentType(), f.documentId(), f.documentNumber(), f.warehouseId(),
                        f.warehouseName(), String.join(", ", f.binLocationCodes()), List.of()));
            } else {
                String nodeType = StockMovementType.ADJUSTMENT_OUT.name().equals(type) ? "ADJUSTMENT_OUT" : "OTHER_ISSUE";
                children.add(node("outflow:" + seq, nodeType, "DOWNSTREAM",
                        f.movementTypeLabel() + " " + nvl(f.documentNumber()), f.quantity(), f.occurredAt(),
                        f.documentType(), f.documentId(), f.documentNumber(), f.warehouseId(), f.warehouseName(),
                        String.join(", ", f.binLocationCodes()), List.of()));
            }
        }
        String productText = display.skuCode() != null ? display.skuCode()
                + (display.productName() != null ? " - " + display.productName() : "") : "sản phẩm ID " + productId;
        return node("lot:" + lot + ":" + productId, "LOT", "ROOT", "Lô " + lot + " (" + productText + ")", onHand,
                null, null, null, null, null, null, null, children);
    }

    private static TraceNode node(String key, String nodeType, String direction, String title, BigDecimal quantity,
                                  Instant occurredAt, String documentType, Long documentId, String documentNumber,
                                  Long warehouseId, String warehouseName, String binLocationCode,
                                  List<TraceNode> children) {
        return new TraceNode(key, nodeType, direction, title, quantity, occurredAt, documentType, documentId,
                documentNumber, warehouseId, warehouseName,
                binLocationCode != null && !binLocationCode.isEmpty() ? binLocationCode : null, children);
    }

    private static BigDecimal sumIn(List<StockMovement> movements, StockMovementType type) {
        return movements.stream().filter(m -> m.getMovementType() == type).map(m -> nz(m.getQuantityIn()))
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal sumOut(List<StockMovement> movements, StockMovementType type) {
        return movements.stream().filter(m -> m.getMovementType() == type).map(m -> nz(m.getQuantityOut()))
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal sumOut(List<StockMovement> group) {
        return group.stream().map(m -> nz(m.getQuantityOut())).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(3, java.math.RoundingMode.UNNECESSARY);
    }

    private static String nvl(String value) {
        return value != null ? value : "(không rõ)";
    }
}
