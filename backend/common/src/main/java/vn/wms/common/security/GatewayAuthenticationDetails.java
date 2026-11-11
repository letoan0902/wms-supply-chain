package vn.wms.common.security;

import java.util.Collections;
import java.util.Set;

/**
 * Phần {@code details} của {@code Authentication} do {@link GatewayIdentityFilter} tạo: các kho được gán, doanh nghiệp
 * và gói dịch vụ lấy từ header của api-gateway (X-User-Warehouse-Ids, X-Company-Id, X-Plan).
 *
 * @param warehouseIds kho được gán cho người dùng (rỗng với ADMIN, ACCOUNTANT, INTEGRATION và lời gọi nội bộ)
 * @param companyId    doanh nghiệp của request, {@code null} nếu chưa xác định
 * @param plan         gói dịch vụ hiện tại (FREE, TRIAL, PRO), {@code null} nếu gateway không gắn
 */
public record GatewayAuthenticationDetails(Set<Long> warehouseIds, Long companyId, String plan) {

    public GatewayAuthenticationDetails {
        warehouseIds = (warehouseIds != null) ? Set.copyOf(warehouseIds) : Collections.emptySet();
    }
}
