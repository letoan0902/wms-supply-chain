package vn.wms.inventory.dto;

import java.time.Instant;

/**
 * Kết quả thực hiện quét và tự động khóa các lô hàng đã hết hạn sử dụng (WMS-10.2 - FR12).
 */
public record AutoLockResponse(
        int lockedCount,
        String message,
        Instant executedAt
) {
}
