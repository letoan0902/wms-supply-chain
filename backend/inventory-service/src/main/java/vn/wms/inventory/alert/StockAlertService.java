package vn.wms.inventory.alert;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * API đọc cảnh báo tồn kho (WMS-15.1): tính trực tiếp từ tồn hiện tại mỗi lần gọi, nên bảng tồn kho hiện thẻ cảnh báo
 * ngay mà không phải chờ tác vụ 00:00. Phạm vi: doanh nghiệp của người gọi (Hibernate tự lọc) và các kho người gọi
 * được xem ({@link UserContext#resolveAllowedWarehouses}, như các API kho khác).
 */
@Service
public class StockAlertService {

    private static final int SUMMARY_TOP_ITEMS = 5;

    private final StockAlertDetector detector;
    private final ProductCatalog productCatalog;
    private final StockAlertClock clock;
    private final StockAlertProperties properties;

    public StockAlertService(StockAlertDetector detector, ProductCatalog productCatalog, StockAlertClock clock,
                             StockAlertProperties properties) {
        this.detector = detector;
        this.productCatalog = productCatalog;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Danh sách cảnh báo có lọc và phân trang. Thứ tự cố định: khẩn cấp trước, theo loại, hạn gần trước.
     * Khi không đọc được danh mục sản phẩm: lọc riêng LOW_STOCK trả 503, không lọc loại thì trả các loại còn lại.
     */
    public PageResponse<StockAlertResponse> search(StockAlertKind kind, StockAlertSeverity severity, Long warehouseId,
                                                   Long productId, String keyword, Pageable pageable) {
        Set<Long> scope = resolveScope(warehouseId);
        ProductLookup products = productCatalog.load(true);
        if (kind == StockAlertKind.LOW_STOCK && !products.available()) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Chưa tính được cảnh báo tồn thấp: " + products.note());
        }
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<StockAlertResponse> filtered = detector.detect(clock.today(), scope, products).stream()
                .filter(a -> kind == null || a.kind() == kind)
                .filter(a -> severity == null || a.severity() == severity)
                .filter(a -> productId == null || productId.equals(a.productId()))
                .filter(a -> normalizedKeyword == null || matches(a, normalizedKeyword))
                .toList();
        int size = pageable.getPageSize();
        int page = pageable.getPageNumber();
        int from = (int) Math.min((long) page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return PageResponse.of(filtered.subList(from, to), page, size, filtered.size());
    }

    /** Số cảnh báo theo loại và mức trong phạm vi kho của người gọi. */
    public StockAlertSummaryResponse summary(Long warehouseId) {
        Set<Long> scope = resolveScope(warehouseId);
        ProductLookup products = productCatalog.load(true);
        List<StockAlertResponse> alerts = detector.detect(clock.today(), scope, products);
        Map<StockAlertKind, Long> byKind = new EnumMap<>(StockAlertKind.class);
        for (StockAlertKind kind : StockAlertKind.values()) {
            byKind.put(kind, 0L);
        }
        Map<StockAlertSeverity, Long> bySeverity = new EnumMap<>(StockAlertSeverity.class);
        for (StockAlertSeverity severity : StockAlertSeverity.values()) {
            bySeverity.put(severity, 0L);
        }
        for (StockAlertResponse alert : alerts) {
            byKind.merge(alert.kind(), 1L, Long::sum);
            bySeverity.merge(alert.severity(), 1L, Long::sum);
        }
        return new StockAlertSummaryResponse(clock.today(), properties.nearExpiryDays(), properties.deadStockDays(),
                alerts.size(), byKind, bySeverity, products.available(),
                products.available() ? null : "Chưa tính được cảnh báo tồn thấp: " + products.note(),
                alerts.stream().limit(SUMMARY_TOP_ITEMS).toList());
    }

    /** Tập kho được xét; rỗng nghĩa là mọi kho của doanh nghiệp (ADMIN, ACCOUNTANT hoặc gọi nội bộ không có người dùng). */
    private Set<Long> resolveScope(Long warehouseId) {
        Optional<CurrentUser> user = UserContext.getCurrentUser();
        if (user.isPresent()) {
            return UserContext.resolveAllowedWarehouses(user.get(), warehouseId);
        }
        return warehouseId != null ? Set.of(warehouseId) : Set.of();
    }

    private static boolean matches(StockAlertResponse alert, String keyword) {
        return Stream.of(alert.skuCode(), alert.productName(), alert.lotNo(), alert.binCode(), alert.warehouseCode(),
                        alert.warehouseName())
                .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains(keyword));
    }
}
