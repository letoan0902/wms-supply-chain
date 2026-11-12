package vn.wms.order.client;

import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

/**
 * Không biết inventory-service đã thực hiện thay đổi tồn hay chưa (hết thời gian chờ, mất kết nối, lỗi 5xx) khi gọi
 * một bước có khóa idempotent (WMS-20.4). Trả 503 cho người dùng; gọi lại cùng khóa là an toàn.
 */
public class InventoryOutcomeUnknownException extends ApiException {

    public InventoryOutcomeUnknownException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}
