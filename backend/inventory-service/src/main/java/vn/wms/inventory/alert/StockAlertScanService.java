package vn.wms.inventory.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Một lần quét cảnh báo cho doanh nghiệp hiện tại (WMS-15.1): tính cảnh báo, rồi với từng loại phát một bản tổng hợp
 * {@code alert.stock-summary} cho mỗi kho có cảnh báo, kèm các dòng nghiêm trọng nhất.
 *
 * <p>Chống gửi trùng: trước khi phát một loại, giữ chỗ (doanh nghiệp, ngày, loại) trong {@code stock_alert_runs}. Đã có
 * dấu thì bỏ qua loại đó ({@link StockAlertScanResponse.Status#ALREADY_SENT}). Loại không có cảnh báo nào thì không
 * ghi dấu, để nếu trong ngày phát sinh cảnh báo thì lần quét tay sau vẫn gửi được. Phát lỗi thì gỡ dấu.
 *
 * <p>Không đặt {@code @Transactional}: người gọi đặt TenantContext (TenantFilter của request, hoặc
 * {@code TenantContext.callAs} của tác vụ nền) rồi mới gọi, mỗi bước đọc hoặc ghi tự mở transaction của nó.
 */
@Service
public class StockAlertScanService {

    /** Giá trị {@code triggeredBy} của tác vụ chạy theo lịch. */
    public static final String SCHEDULER = "SCHEDULER";

    private static final Logger log = LoggerFactory.getLogger(StockAlertScanService.class);

    private final StockAlertDetector detector;
    private final ProductCatalog productCatalog;
    private final StockAlertRunGuard guard;
    private final WmsEventPublisher eventPublisher;
    private final StockAlertClock clock;
    private final StockAlertProperties properties;

    public StockAlertScanService(StockAlertDetector detector, ProductCatalog productCatalog, StockAlertRunGuard guard,
                                 WmsEventPublisher eventPublisher, StockAlertClock clock,
                                 StockAlertProperties properties) {
        this.detector = detector;
        this.productCatalog = productCatalog;
        this.guard = guard;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.properties = properties;
    }

    public StockAlertScanResponse scanCurrentCompany(String triggeredBy) {
        Long companyId = TenantContext.requireCompanyId();
        LocalDate today = clock.today();
        Instant detectedAt = clock.now();
        ProductLookup products = productCatalog.load(false);
        List<StockAlertResponse> alerts = detector.detect(today, Set.of(), products);

        List<StockAlertScanResponse.KindResult> results = new ArrayList<>();
        for (StockAlertKind kind : StockAlertKind.values()) {
            results.add(processKind(companyId, today, detectedAt, kind, alerts, products, triggeredBy));
        }
        log.info("Quét cảnh báo tồn kho doanh nghiệp {} ngày {} ({}): {}", companyId, today, triggeredBy, results);
        return new StockAlertScanResponse(today, triggeredBy, results);
    }

    private StockAlertScanResponse.KindResult processKind(Long companyId, LocalDate today, Instant detectedAt,
                                                          StockAlertKind kind, List<StockAlertResponse> alerts,
                                                          ProductLookup products, String triggeredBy) {
        if (kind == StockAlertKind.LOW_STOCK && !products.available()) {
            return new StockAlertScanResponse.KindResult(kind, StockAlertScanResponse.Status.UNAVAILABLE, 0, 0,
                    "Chưa tính được cảnh báo tồn thấp: " + products.note());
        }
        List<StockAlertResponse> ofKind = alerts.stream().filter(a -> a.kind() == kind).toList();
        if (ofKind.isEmpty()) {
            return new StockAlertScanResponse.KindResult(kind, StockAlertScanResponse.Status.NOTHING_FOUND, 0, 0,
                    "Không có cảnh báo");
        }

        Optional<Long> runId;
        try {
            runId = guard.claim(today, kind, triggeredBy);
        } catch (DataIntegrityViolationException ex) {
            // Instance khác vừa giữ chỗ cùng (doanh nghiệp, ngày, loại)
            runId = Optional.empty();
        }
        if (runId.isEmpty()) {
            return new StockAlertScanResponse.KindResult(kind, StockAlertScanResponse.Status.ALREADY_SENT, 0,
                    ofKind.size(), "Hôm nay đã gửi cảnh báo loại này, không gửi lại");
        }

        List<StockAlertSummaryEvent> summaries = summarize(companyId, today, detectedAt, kind, ofKind);
        try {
            for (StockAlertSummaryEvent summary : summaries) {
                eventPublisher.publish(WmsEvents.RoutingKeys.ALERT_STOCK_SUMMARY, summary);
            }
        } catch (RuntimeException ex) {
            log.error("Phát cảnh báo {} của doanh nghiệp {} thất bại, gỡ dấu để thử lại: {}",
                    kind, companyId, ex.getMessage());
            guard.release(runId.get());
            return new StockAlertScanResponse.KindResult(kind, StockAlertScanResponse.Status.FAILED,
                    summaries.size(), ofKind.size(), "Không gửi được sự kiện cảnh báo: " + ex.getMessage());
        }
        guard.complete(runId.get(), summaries.size(), ofKind.size(), 0, null);
        return new StockAlertScanResponse.KindResult(kind, StockAlertScanResponse.Status.PUBLISHED, summaries.size(),
                ofKind.size(), "Đã gửi " + summaries.size() + " bản tổng hợp theo kho");
    }

    /** Một bản tổng hợp cho mỗi kho, giữ thứ tự nghiêm trọng của danh sách đầu vào. */
    List<StockAlertSummaryEvent> summarize(Long companyId, LocalDate today, Instant detectedAt, StockAlertKind kind,
                                           List<StockAlertResponse> alertsOfKind) {
        Map<Long, List<StockAlertResponse>> byWarehouse = new LinkedHashMap<>();
        for (StockAlertResponse alert : alertsOfKind) {
            byWarehouse.computeIfAbsent(alert.warehouseId(), id -> new ArrayList<>()).add(alert);
        }
        Integer thresholdDays = switch (kind) {
            case NEAR_EXPIRY -> properties.nearExpiryDays();
            case DEAD_STOCK -> properties.deadStockDays();
            default -> null;
        };
        List<StockAlertSummaryEvent> summaries = new ArrayList<>();
        for (List<StockAlertResponse> group : byWarehouse.values()) {
            StockAlertResponse first = group.get(0);
            StockAlertSeverity severity = null;
            BigDecimal totalQuantity = BigDecimal.ZERO;
            for (StockAlertResponse alert : group) {
                severity = StockAlertSeverity.max(severity, alert.severity());
                totalQuantity = totalQuantity.add(alert.quantity() == null ? BigDecimal.ZERO : alert.quantity());
            }
            summaries.add(new StockAlertSummaryEvent(companyId, today, kind, severity, first.warehouseId(),
                    first.warehouseCode(), first.warehouseName(), group.size(), totalQuantity, thresholdDays,
                    group.stream().limit(properties.topItems()).map(StockAlertResponse::toItem).toList(),
                    detectedAt));
        }
        return summaries;
    }
}
