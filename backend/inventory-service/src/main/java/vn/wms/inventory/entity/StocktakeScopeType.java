package vn.wms.inventory.entity;

/**
 * Phạm vi vị trí của đợt kiểm kê: toàn bộ kho, một số khu (zone) hoặc danh sách vị trí ô/kệ cụ thể.
 * Danh sách vị trí thực tế được chốt lúc bắt đầu kiểm kê (bảng stocktake_locations).
 */
public enum StocktakeScopeType {
    WAREHOUSE,
    ZONES,
    LOCATIONS
}
