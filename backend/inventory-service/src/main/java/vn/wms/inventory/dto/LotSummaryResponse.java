package vn.wms.inventory.dto;

import java.math.BigDecimal;

/**
 * Thống kê tổng quan tình trạng các lô hàng (WMS-10.2 - FR12).
 */
public record LotSummaryResponse(
        long totalLots,
        long activeLots,
        long expiringSoonLots,
        long expiredLots,
        long lockedLots,
        BigDecimal totalQuantityInLots
) {
}
