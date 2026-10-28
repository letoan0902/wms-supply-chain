package vn.wms.inventory.dto;

import java.math.BigDecimal;

/**
 * Request cập nhật vị trí ô/kệ (WMS-06.1).
 */
public record UpdateBinLocationRequest(
        BigDecimal maxWeight,
        String status,
        Boolean active
) {}

