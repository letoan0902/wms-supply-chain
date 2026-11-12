package vn.wms.inventory.alert;

import java.util.Map;

/**
 * Kết quả đọc danh mục sản phẩm cho một lần tính cảnh báo.
 *
 * @param products   sản phẩm theo id
 * @param available  false khi không đọc được product-service (cảnh báo tồn thấp không tính được)
 * @param note       lý do bằng tiếng Việt khi không đọc được, null khi đọc được
 */
public record ProductLookup(Map<Long, AlertProductInfo> products, boolean available, String note) {

    public ProductLookup {
        products = products == null ? Map.of() : Map.copyOf(products);
    }

    public static ProductLookup of(Map<Long, AlertProductInfo> products) {
        return new ProductLookup(products, true, null);
    }

    public static ProductLookup unavailable(String note) {
        return new ProductLookup(Map.of(), false, note);
    }

    public AlertProductInfo get(Long productId) {
        return productId == null ? null : products.get(productId);
    }
}
