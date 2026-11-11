package vn.wms.inventory.alert;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.common.messaging.event.StockAlertKind;

import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả một lần quét cảnh báo của một doanh nghiệp (tác vụ 00:00 hoặc quét tay).
 *
 * @param alertDate   ngày quét (giờ Việt Nam)
 * @param triggeredBy SCHEDULER hoặc tên đăng nhập người bấm quét
 * @param results     kết quả theo từng loại cảnh báo
 */
@Schema(description = "Kết quả quét cảnh báo tồn kho")
public record StockAlertScanResponse(LocalDate alertDate, String triggeredBy, List<KindResult> results) {

    /** Trạng thái xử lý một loại cảnh báo. */
    public enum Status {
        /** Đã phát bản tổng hợp. */
        PUBLISHED,
        /** Hôm nay đã phát loại này rồi, không phát lại. */
        ALREADY_SENT,
        /** Không có cảnh báo nào. */
        NOTHING_FOUND,
        /** Không tính được (LOW_STOCK khi không đọc được danh mục sản phẩm). */
        UNAVAILABLE,
        /** Phát sự kiện lỗi, dấu đã được gỡ để lần quét sau thử lại. */
        FAILED
    }

    /**
     * @param kind           loại cảnh báo
     * @param status         trạng thái
     * @param warehouseCount số kho có cảnh báo (số bản tổng hợp đã phát khi PUBLISHED)
     * @param itemCount      số dòng cảnh báo tìm thấy
     * @param message        diễn giải tiếng Việt
     */
    public record KindResult(StockAlertKind kind, Status status, int warehouseCount, int itemCount, String message) {
    }
}
