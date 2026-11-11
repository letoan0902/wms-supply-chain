package vn.wms.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lớp bọc {@link InventoryClient}: quy lỗi Feign về {@link ApiException} để controller trả về đúng mã lỗi
 * (404 kho không tồn tại, 503 inventory-service chưa sẵn sàng) thay vì 500 chung chung.
 */
@Component
public class InventoryGateway {

    private static final Logger log = LoggerFactory.getLogger(InventoryGateway.class);
    /** Chỉ dùng để đọc khung lỗi ApiResponse trong thân phản hồi lỗi của inventory-service. */
    private static final ObjectMapper JSON = new ObjectMapper();

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
     * Trả về AtpResponse với ATP = 0 nếu không tìm thấy (404); ném ApiException SERVICE_UNAVAILABLE nếu lỗi kết nối.
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
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Không kết nối được dịch vụ kho để kiểm tra tồn kho ATP, vui lòng thử lại sau", ex);
        }
    }

    /**
     * Giải phóng số lượng giữ hàng ảo (soft reservation) khi đơn bán bị hủy (WMS-11.2).
     */
    public void releaseReservations(Long orderId) {
        if (orderId == null) {
            return;
        }
        try {
            inventoryClient.releaseReservations(orderId);
            log.info("Đã yêu cầu inventory-service giải phóng giữ chỗ tồn kho cho đơn bán ID {}", orderId);
        } catch (FeignException.NotFound ex) {
            log.info("Không có bản ghi giữ chỗ nào cần giải phóng cho đơn bán ID {}", orderId);
        } catch (FeignException ex) {
            log.warn("Gọi inventory-service giải phóng giữ chỗ cho đơn {} thất bại (status {}): {}",
                    orderId, ex.status(), ex.getMessage());
        }
    }

    /**
     * Kiểm tra trước khi lưu phiếu nhập kho (WMS-20.4): nếu có dòng sẽ cộng tồn vào vị trí đang đóng băng kiểm kê thì
     * ném 409 CONFLICT nêu mã vị trí và mã đợt kiểm kê, kèm danh sách {@link FrozenLocationConflict} trong
     * {@code data}. Lỗi nghiệp vụ khác của inventory-service (kho không có vị trí, kho không tồn tại) được trả lại
     * nguyên mã và thông báo; không gọi được inventory-service thì 503 và phiếu không được lưu.
     */
    public void assertReceiptLocationsNotFrozen(ReceiptLocationCheckRequest request) {
        if (request == null || request.warehouseId() == null || request.lines().isEmpty()) {
            return;
        }
        List<FrozenLocationConflict> conflicts;
        try {
            ApiResponse<List<FrozenLocationConflict>> response = inventoryClient.checkReceiptLocations(request);
            conflicts = response != null && response.data() != null ? response.data() : List.of();
        } catch (FeignException ex) {
            throw translate(ex, "Không kết nối được dịch vụ kho để kiểm tra vị trí nhận hàng, vui lòng thử lại sau");
        }
        if (conflicts.isEmpty()) {
            return;
        }
        String detail = conflicts.stream()
                .map(c -> c.binLocationCode() + " (đợt " + c.sessionCode() + ")")
                .distinct()
                .collect(Collectors.joining(", "));
        throw ApiException.withData(ErrorCode.CONFLICT,
                "Vị trí " + detail + " đang đóng băng để kiểm kê, không thể lập phiếu nhập kho vào vị trí này. "
                        + "Hãy chọn vị trí khác hoặc chờ đợt kiểm kê kết thúc!",
                conflicts);
    }

    /**
     * Giữ chỗ toàn bộ đơn bán ngay khi xác nhận (WMS-20.4). Thành công thì inventory-service đã commit giữ chỗ (hoặc
     * đơn đã được giữ từ lần gọi trước). Thiếu hàng thì ném {@link StockShortageException} (inventory-service không
     * giữ dòng nào); lỗi nghiệp vụ khác giữ nguyên mã; không gọi được hoặc hết thời gian chờ thì 503, khi đó không
     * biết inventory-service đã giữ hay chưa, nhưng gọi lại cùng đơn là an toàn (idempotent theo đơn).
     */
    public ReserveOrderResponse reserveForOrder(ReserveOrderRequest request) {
        try {
            ApiResponse<ReserveOrderResponse> response = inventoryClient.reserveOrder(request);
            log.info("inventory-service đã giữ chỗ cho đơn bán {} (ID {})", request.orderNumber(), request.orderId());
            return response != null ? response.data() : null;
        } catch (FeignException ex) {
            ApiException translated = translate(ex, "Không kết nối được dịch vụ kho để giữ chỗ tồn kho cho đơn, "
                    + "đơn chưa được xác nhận, vui lòng thử lại sau");
            if (translated.getErrorCode() == ErrorCode.CONFLICT) {
                throw new StockShortageException(translated.getMessage(), translated.getData());
            }
            throw translated;
        }
    }

    /**
     * Quy lỗi Feign về {@link ApiException}: inventory-service trả lỗi 4xx theo khung ApiResponse thì giữ nguyên mã,
     * thông báo tiếng Việt và {@code data}; lỗi kết nối, hết thời gian chờ hoặc 5xx thì trả SERVICE_UNAVAILABLE.
     */
    ApiException translate(FeignException ex, String unavailableMessage) {
        int status = ex.status();
        if (status >= 400 && status < 500) {
            ApiException fromBody = readApiError(ex);
            if (fromBody != null) {
                return fromBody;
            }
            return new ApiException(ErrorCode.fromHttpStatus(status), ErrorCode.fromHttpStatus(status).getDefaultMessage(), ex);
        }
        log.warn("Gọi inventory-service thất bại (status {}): {}", status, ex.getMessage());
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE, unavailableMessage, ex);
    }

    private ApiException readApiError(FeignException ex) {
        String body = ex.contentUTF8();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(body);
            String message = node.path("message").asText(null);
            if (message == null || message.isBlank()) {
                return null;
            }
            ErrorCode code = ErrorCode.fromHttpStatus(ex.status());
            String codeText = node.path("code").asText(null);
            if (codeText != null) {
                for (ErrorCode candidate : ErrorCode.values()) {
                    if (candidate.name().equals(codeText)) {
                        code = candidate;
                        break;
                    }
                }
            }
            JsonNode data = node.get("data");
            if (data != null && !data.isNull()) {
                return ApiException.withData(code, message, JSON.convertValue(data, Object.class));
            }
            return new ApiException(code, message);
        } catch (Exception parseError) {
            log.debug("Không đọc được nội dung lỗi của inventory-service: {}", parseError.getMessage());
            return null;
        }
    }

    /**
     * Tự động sinh danh sách lấy hàng (Picking List) gợi ý vị trí ô kệ và lô theo FEFO/FIFO (WMS-12.1).
     */
    public vn.wms.order.dto.PickingListResponse generatePickingList(vn.wms.order.dto.GeneratePickingListRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Yêu cầu sinh danh sách lấy hàng không được để trống");
        }
        try {
            ApiResponse<vn.wms.order.dto.PickingListResponse> response = inventoryClient.generatePickingList(request);
            if (response != null && response.success() && response.data() != null) {
                return response.data();
            }
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể sinh danh sách lấy hàng từ inventory-service");
        } catch (FeignException ex) {
            log.warn("Gọi inventory-service sinh Picking List thất bại (status {}): {}", ex.status(), ex.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Không kết nối được dịch vụ kho để sinh lộ trình lấy hàng, vui lòng thử lại sau", ex);
        }
    }
}
