package vn.wms.inventory.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.common.tenant.TenantContext;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Danh mục sản phẩm của doanh nghiệp hiện tại (SKU, tên, mức tồn an toàn, điểm đặt hàng lại) đọc từ product-service.
 * inventory-service không lưu các ngưỡng này: chúng thuộc bảng {@code products} của product-service.
 *
 * <p>product-service không trả lời được thì trả {@link ProductLookup#unavailable(String)}: cảnh báo tồn thấp không
 * tính được (không đoán ngưỡng), các cảnh báo khác vẫn chạy nhưng SKU và tên để trống.
 */
@Component
public class ProductCatalog {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalog.class);

    private final AlertProductClient client;
    private final StockAlertProperties properties;
    private final Map<Long, CachedLookup> cache = new ConcurrentHashMap<>();

    public ProductCatalog(AlertProductClient client, StockAlertProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * @param useCache true với API đọc (giữ trong bộ nhớ {@code app.alerts.product-cache-seconds} giây),
     *                 false với tác vụ quét (luôn đọc mới)
     */
    public ProductLookup load(boolean useCache) {
        Long companyId = TenantContext.requireCompanyId();
        if (useCache && properties.productCacheSeconds() > 0) {
            CachedLookup cached = cache.get(companyId);
            if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
                return cached.lookup();
            }
        }
        ProductLookup lookup = fetch(companyId);
        if (lookup.available() && properties.productCacheSeconds() > 0) {
            cache.put(companyId, new CachedLookup(lookup,
                    Instant.now().plus(Duration.ofSeconds(properties.productCacheSeconds()))));
        }
        return lookup;
    }

    private ProductLookup fetch(Long companyId) {
        Map<Long, AlertProductInfo> products = new HashMap<>();
        try {
            for (int page = 0; page < properties.maxProductPages(); page++) {
                ApiResponse<PageResponse<AlertProductInfo>> response =
                        client.listProducts(page, properties.productPageSize(), "id");
                PageResponse<AlertProductInfo> data = response == null ? null : response.data();
                if (data == null || data.content() == null) {
                    return ProductLookup.unavailable("Dịch vụ sản phẩm trả về dữ liệu rỗng");
                }
                for (AlertProductInfo product : data.content()) {
                    if (product != null && product.id() != null) {
                        products.put(product.id(), product);
                    }
                }
                if (data.last() || data.content().isEmpty()) {
                    return ProductLookup.of(products);
                }
            }
            log.warn("Danh mục sản phẩm của doanh nghiệp {} vượt {} trang, chỉ đọc phần đầu",
                    companyId, properties.maxProductPages());
            return ProductLookup.of(products);
        } catch (RuntimeException ex) {
            log.warn("Không đọc được danh mục sản phẩm của doanh nghiệp {} từ product-service: {}",
                    companyId, ex.getMessage());
            return ProductLookup.unavailable("Không kết nối được dịch vụ sản phẩm để lấy mức tồn an toàn");
        }
    }

    private record CachedLookup(ProductLookup lookup, Instant expiresAt) {
    }
}
