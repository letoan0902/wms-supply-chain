package vn.wms.order.client;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.Optional;

/**
 * Lớp bọc {@link InventoryClient}: quy lỗi Feign về {@link ApiException} để controller trả về đúng mã lỗi
 * (404 kho không tồn tại, 503 inventory-service chưa sẵn sàng) thay vì 500 chung chung.
 */
@Component
public class InventoryGateway {

    private static final Logger log = LoggerFactory.getLogger(InventoryGateway.class);

    private final InventoryClient inventoryClient;

    public InventoryGateway(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    /** Tìm kho theo id; rỗng nếu không có. Ném ApiException SERVICE_UNAVAILABLE khi không gọi được inventory-service. */
    public Optional<WarehouseSummary> findWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return Optional.empty();
        }
        try {
            ApiResponse<WarehouseSummary> response = inventoryClient.getWarehouse(warehouseId);
            if (response == null || !response.success()) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.data());
        } catch (FeignException.NotFound ex) {
            return Optional.empty();
        } catch (FeignException ex) {
            log.warn("Gọi inventory-service lấy kho {} thất bại (status {}): {}", warehouseId, ex.status(), ex.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Không kết nối được dịch vụ kho để kiểm tra thông tin kho, vui lòng thử lại sau", ex);
        }
    }

    /** Kho phải tồn tại và đang hoạt động, dùng khi lập đơn mua hoặc đơn bán. */
    public WarehouseSummary requireActiveWarehouse(Long warehouseId) {
        WarehouseSummary warehouse = findWarehouse(warehouseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy kho có id " + warehouseId));
        if (!warehouse.active()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Kho " + warehouse.code() + " đang ngừng hoạt động, không thể lập đơn cho kho này");
        }
        return warehouse;
    }

    /**
     * Lấy thông tin tồn khả dụng ATP của một sản phẩm từ inventory-service (FR15 - WMS-11.1).
     * Trả về AtpResponse với ATP = 0 nếu không tìm thấy hoặc lỗi kết nối.
     */
    public AtpResponse getAtp(Long productId, Long warehouseId) {
        if (productId == null) {
            return AtpResponse.zero(null, warehouseId);
        }
        try {
            ApiResponse<AtpResponse> response = inventoryClient.getAtp(productId, warehouseId);
            if (response != null && response.success() && response.data() != null) {
                return response.data();
            }
            return AtpResponse.zero(productId, warehouseId);
        } catch (FeignException.NotFound ex) {
            return AtpResponse.zero(productId, warehouseId);
        } catch (FeignException ex) {
            log.warn("Gọi inventory-service lấy ATP sản phẩm {} thất bại (status {}): {}", productId, ex.status(), ex.getMessage());
            return AtpResponse.zero(productId, warehouseId);
        }
    }
}
