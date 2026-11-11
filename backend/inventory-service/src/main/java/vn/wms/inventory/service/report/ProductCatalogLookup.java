package vn.wms.inventory.service.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.client.ProductClient;
import vn.wms.inventory.client.ProductInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tra cứu product-service cho báo cáo kho (WMS-17.1): danh mục và từ khóa chỉ product-service biết, nên bộ lọc
 * {@code categoryId}, {@code keyword} được đổi thành danh sách ID sản phẩm trước khi tổng hợp sổ kho.
 */
@Component
public class ProductCatalogLookup {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogLookup.class);

    /** Kích thước trang tối đa product-service cho phép. */
    static final int PAGE_SIZE = 100;
    /** Số sản phẩm tối đa một bộ lọc được khớp; vượt thì yêu cầu thu hẹp để câu tổng hợp không quá lớn. */
    static final int MAX_MATCHED_PRODUCTS = 2000;

    private final ProductClient productClient;

    public ProductCatalogLookup(ProductClient productClient) {
        this.productClient = productClient;
    }

    /**
     * ID các sản phẩm khớp từ khóa và danh mục. product-service không trả lời được thì báo
     * {@code SERVICE_UNAVAILABLE}: không được lặng lẽ bỏ bộ lọc vì báo cáo sẽ ra số của mọi sản phẩm.
     */
    public Set<Long> findProductIds(String keyword, Long categoryId) {
        Set<Long> ids = new LinkedHashSet<>();
        int page = 0;
        while (true) {
            PageResponse<ProductInfo> result;
            try {
                ApiResponse<PageResponse<ProductInfo>> response =
                        productClient.searchProducts(keyword, categoryId, page, PAGE_SIZE);
                result = response != null ? response.data() : null;
            } catch (Exception ex) {
                log.warn("Không tra được danh sách sản phẩm từ product-service cho bộ lọc báo cáo: {}", ex.getMessage());
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                        "Không lọc được theo danh mục hoặc từ khóa vì dịch vụ sản phẩm chưa sẵn sàng, vui lòng thử lại sau");
            }
            if (result == null || result.content() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                        "Dịch vụ sản phẩm trả về dữ liệu không hợp lệ, chưa lọc được theo danh mục hoặc từ khóa");
            }
            for (ProductInfo product : result.content()) {
                if (product != null && product.id() != null) {
                    ids.add(product.id());
                }
            }
            if (ids.size() > MAX_MATCHED_PRODUCTS) {
                throw ApiException.badRequest("Bộ lọc khớp hơn " + MAX_MATCHED_PRODUCTS
                        + " sản phẩm, vui lòng thu hẹp từ khóa hoặc chọn danh mục cụ thể hơn");
            }
            if (result.last() || result.content().isEmpty()) {
                return ids;
            }
            page++;
        }
    }

    /**
     * Thông tin hiển thị của các sản phẩm chưa có ảnh chụp mã, tên trong sổ kho (ví dụ chỉ có dòng số dư mở sổ).
     * Chỉ để bổ sung hiển thị: lỗi gọi product-service thì bỏ qua, các trường mã, tên giữ null.
     */
    public Map<Long, ProductInfo> findDisplayInfo(Collection<Long> productIds) {
        Map<Long, ProductInfo> result = new HashMap<>();
        for (Long id : productIds) {
            try {
                ApiResponse<ProductInfo> response = productClient.getProduct(id);
                if (response != null && response.data() != null) {
                    result.put(id, response.data());
                }
            } catch (feign.FeignException.NotFound ex) {
                log.debug("Sản phẩm {} không còn trong product-service", id);
            } catch (Exception ex) {
                // Dừng ngay ở lỗi đầu tiên: product-service đang lỗi thì gọi tiếp từng sản phẩm chỉ làm báo cáo chậm
                log.debug("Không lấy được thông tin sản phẩm {} từ product-service: {}", id, ex.getMessage());
                break;
            }
        }
        return result;
    }
}
