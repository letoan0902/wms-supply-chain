package vn.wms.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.wms.inventory.entity.StocktakeScopeType;

import java.time.LocalDate;
import java.util.List;

/**
 * Yêu cầu lập đợt kiểm kê (WMS-14.1). Mã đợt do hệ thống tự sinh theo mẫu ST-yyyyMM-NN.
 *
 * @param warehouseId    kho cần kiểm kê
 * @param scopeType      WAREHOUSE (toàn kho), ZONES (theo khu) hoặc LOCATIONS (theo danh sách vị trí)
 * @param zones          danh sách khu, bắt buộc khi scopeType = ZONES
 * @param binLocationIds danh sách ID vị trí, bắt buộc khi scopeType = LOCATIONS
 * @param productIds     giới hạn sản phẩm (không bắt buộc); để trống nghĩa là mọi sản phẩm
 * @param plannedDate    ngày dự kiến kiểm kê
 * @param notes          ghi chú
 */
public record CreateStocktakeRequest(
        @NotNull(message = "Kho kiểm kê không được để trống")
        Long warehouseId,

        @NotNull(message = "Phạm vi kiểm kê không được để trống")
        StocktakeScopeType scopeType,

        @Size(max = 50, message = "Tối đa 50 khu trong một đợt kiểm kê")
        List<String> zones,

        @Size(max = 1000, message = "Tối đa 1000 vị trí trong một đợt kiểm kê")
        List<Long> binLocationIds,

        @Size(max = 200, message = "Tối đa 200 sản phẩm trong phạm vi một đợt kiểm kê")
        List<Long> productIds,

        @NotNull(message = "Ngày dự kiến kiểm kê không được để trống")
        LocalDate plannedDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String notes
) {
}
