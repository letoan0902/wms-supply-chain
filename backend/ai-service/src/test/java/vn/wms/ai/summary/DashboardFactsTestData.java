package vn.wms.ai.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Dữ liệu KPI và báo cáo nhập xuất mẫu (đúng dạng notification-service, inventory-service trả) cho test. */
public final class DashboardFactsTestData {

    private DashboardFactsTestData() {
    }

    static final ObjectMapper MAPPER = new ObjectMapper();

    /** KPI như notification-service trả cho ADMIN (financial = true) hoặc nhân viên kho (thẻ tài chính bị che). */
    public static JsonNode kpi(boolean financial) throws Exception {
        String receivables = financial
                ? "{\"title\":\"Công nợ phải thu\",\"amount\":12500000,\"currency\":\"VNĐ\",\"overdueCustomersCount\":3,"
                + "\"subText\":\"3 khách hàng còn dư nợ đến hạn\",\"visible\":true}"
                : "{\"title\":\"Công nợ phải thu\",\"amount\":0,\"currency\":\"VNĐ\",\"overdueCustomersCount\":0,"
                + "\"subText\":\"Không có quyền xem\",\"visible\":false}";
        String payables = financial
                ? "{\"title\":\"Công nợ phải trả\",\"amount\":8000000,\"dueSoonSuppliersCount\":2,"
                + "\"subText\":\"2 nhà cung cấp đến hạn trong tuần\",\"visible\":true}"
                : "{\"title\":\"Công nợ phải trả\",\"amount\":0,\"dueSoonSuppliersCount\":0,"
                + "\"subText\":\"Không có quyền xem\",\"visible\":false}";
        String valuation = "{\"title\":\"Giá trị tồn kho\",\"amount\":null,\"valuationMethod\":\"NOT_AVAILABLE\","
                + "\"subText\":\"Chưa tính được: tồn kho chưa có giá vốn\",\"visible\":" + financial + "}";
        String revenue = financial
                ? "{\"title\":\"Doanh số xuất bán tháng\",\"amount\":45000000,\"growthRatePercentage\":null,"
                + "\"isPositiveGrowth\":true,\"subText\":\"Chưa tính được tăng trưởng: tháng trước không có doanh số\","
                + "\"visible\":true}"
                : "{\"title\":\"Doanh số xuất bán tháng\",\"amount\":0,\"growthRatePercentage\":0.0,"
                + "\"subText\":\"Không có quyền xem\",\"visible\":false}";
        return MAPPER.readTree("""
                {"receivables":%s,"payables":%s,"inventoryValuation":%s,"monthlyRevenue":%s,
                 "availableStock":{"title":"Tồn kho khả dụng","totalQuantity":1250.5,"unit":"sản phẩm",
                   "subText":"Sản phẩm lưu tại các vị trí ô/kệ","visible":true},
                 "pendingSalesOrders":{"title":"Đơn bán chờ xuất kho (SO)","totalOrders":7,"urgentOrders":2,
                   "subText":"2 đơn cần điều phối lấy hàng gấp","visible":true},
                 "pendingPurchaseOrders":{"title":"Đơn mua chờ tiếp nhận (PO)","totalOrders":4,"arrivingTodayCount":1,
                   "subText":"1 lô hàng dự kiến đến trong hôm nay","visible":true},
                 "warehouseUtilization":{"title":"Tỷ lệ lấp đầy kho","occupancyRatePercentage":91.5,
                   "usedLocations":183,"totalLocations":200,"subText":"183 / 200 vị trí ô kệ đang sử dụng",
                   "visible":true},
                 "meta":{"companyId":701,"warehouseId":3,"warehouseName":"Kho #3","dateRange":"7_DAYS",
                   "calculatedAt":"2026-11-12T03:00:00Z","fromCache":false}}
                """.formatted(receivables, payables, valuation, revenue));
    }

    public static JsonNode inOut(boolean complete) throws Exception {
        return MAPPER.readTree(complete
                ? "{\"fromDate\":\"2026-11-01\",\"toDate\":\"2026-11-12\",\"totals\":{\"openingQuantity\":10,"
                + "\"quantityIn\":500,\"quantityOut\":320.25,\"closingQuantity\":189.75},\"dataNote\":null}"
                : "{\"fromDate\":\"2026-11-01\",\"toDate\":\"2026-11-12\",\"totals\":{\"openingQuantity\":null,"
                + "\"quantityIn\":null,\"quantityOut\":null,\"closingQuantity\":189.75},"
                + "\"dataNote\":\"Sổ kho bắt đầu ghi từ 10/11/2026.\"}");
    }
}
