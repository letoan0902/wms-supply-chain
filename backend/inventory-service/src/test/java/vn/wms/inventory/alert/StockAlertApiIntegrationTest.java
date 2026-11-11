package vn.wms.inventory.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.inventory.alert.StockAlertTestSupport.page;
import static vn.wms.inventory.alert.StockAlertTestSupport.product;

/** API đọc cảnh báo: phạm vi doanh nghiệp và kho, lọc, phân trang, tóm tắt, quyền chạy quét tay. */
class StockAlertApiIntegrationTest extends StockAlertIntegrationTestBase {

    private long warehouse1;
    private long warehouse2;
    private long warehouseOfB;

    @BeforeEach
    void setUpData() {
        long bin1 = data.warehouseWithBin(COMPANY_A, "WH-HN", "Kho Hà Nội");
        long bin2 = data.warehouseWithBin(COMPANY_A, "WH-HCM", "Kho Hồ Chí Minh");
        long binB = data.warehouseWithBin(COMPANY_B, "WH-HN", "Kho của doanh nghiệp B");
        warehouse1 = data.warehouseIdOfBin(COMPANY_A, bin1);
        warehouse2 = data.warehouseIdOfBin(COMPANY_A, bin2);
        warehouseOfB = data.warehouseIdOfBin(COMPANY_B, binB);

        // Kho Hà Nội: 1 lô hết hạn, 2 lô cận hạn, 1 mặt hàng tồn thấp
        data.stock(COMPANY_A, bin1, 101L, "HN-HH", "2", "0", TODAY.minusDays(2), true);
        data.stock(COMPANY_A, bin1, 102L, "HN-C1", "5", "0", TODAY.plusDays(3), false);
        data.stock(COMPANY_A, bin1, 102L, "HN-C2", "5", "0", TODAY.plusDays(20), false);
        data.stock(COMPANY_A, bin1, 103L, null, "4", "0", null, false);
        // Kho Hồ Chí Minh: 1 lô cận hạn
        data.stock(COMPANY_A, bin2, 104L, "HCM-C1", "8", "0", TODAY.plusDays(10), false);
        // Doanh nghiệp B: lô hết hạn của SKU trùng mã
        data.stock(COMPANY_B, binB, 101L, "B-HH", "9", "0", TODAY.minusDays(1), false);

        catalogs.put(COMPANY_A, page(product(101L, "SKU-101", "0", "0"), product(102L, "SKU-102", "0", "0"),
                product(103L, "SKU-103", "5", "10"), product(104L, "SKU-104", "0", "0")));
        catalogs.put(COMPANY_B, page(product(101L, "SKU-B-101", "0", "0")));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Long companyId,
                                                    String role, String warehouseIds) {
        request.header("X-Company-Id", companyId).header("X-User-Name", role.toLowerCase())
                .header("X-User-Roles", role);
        if (warehouseIds != null) {
            request.header("X-User-Warehouse-Ids", warehouseIds);
        }
        return request;
    }

    @Test
    @DisplayName("ADMIN thấy mọi kho của doanh nghiệp mình, không thấy dữ liệu của doanh nghiệp khác")
    void adminSeesAllWarehousesOfOwnCompanyOnly() throws Exception {
        mockMvc.perform(as(get("/api/inventory/alerts").param("size", "50"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(5)))
                .andExpect(jsonPath("$.data.content[0].kind", is("EXPIRED")))
                .andExpect(jsonPath("$.data.content[0].severity", is("CRITICAL")))
                .andExpect(jsonPath("$.data.content[*].skuCode", not(hasItem("SKU-B-101"))))
                .andExpect(jsonPath("$.data.content[*].lotNo", not(hasItem("B-HH"))));

        mockMvc.perform(as(get("/api/inventory/alerts"), COMPANY_B, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(1)))
                .andExpect(jsonPath("$.data.content[0].lotNo", is("B-HH")))
                .andExpect(jsonPath("$.data.content[0].skuCode", is("SKU-B-101")));

        // Lọc theo kho của doanh nghiệp khác: không trả gì
        mockMvc.perform(as(get("/api/inventory/alerts").param("warehouseId", String.valueOf(warehouseOfB)),
                        COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(0)));
    }

    @Test
    @DisplayName("Quản lý kho chỉ thấy kho được phân công; hỏi kho khác bị 403")
    void managerIsScopedToAssignedWarehouses() throws Exception {
        mockMvc.perform(as(get("/api/inventory/alerts"), COMPANY_A, "WAREHOUSE_MANAGER", String.valueOf(warehouse2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(1)))
                .andExpect(jsonPath("$.data.content[0].warehouseCode", is("WH-HCM")))
                .andExpect(jsonPath("$.data.content[0].lotNo", is("HCM-C1")));

        mockMvc.perform(as(get("/api/inventory/alerts").param("warehouseId", String.valueOf(warehouse1)),
                        COMPANY_A, "WAREHOUSE_MANAGER", String.valueOf(warehouse2)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(get("/api/inventory/alerts/summary"), COMPANY_A, "WAREHOUSE_STAFF", null))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lọc theo loại, mức, từ khóa và phân trang")
    void filtersAndPaging() throws Exception {
        mockMvc.perform(as(get("/api/inventory/alerts").param("kind", "NEAR_EXPIRY").param("size", "2")
                        .param("page", "1"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(3)))
                .andExpect(jsonPath("$.data.totalPages", is(2)))
                .andExpect(jsonPath("$.data.page", is(1)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].lotNo", is("HN-C2")))
                .andExpect(jsonPath("$.data.last", is(true)));

        mockMvc.perform(as(get("/api/inventory/alerts").param("severity", "CRITICAL"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].severity", everyItem(is("CRITICAL"))))
                .andExpect(jsonPath("$.data.totalElements", is(2)));

        mockMvc.perform(as(get("/api/inventory/alerts").param("kind", "LOW_STOCK"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].skuCode", is("SKU-103")))
                .andExpect(jsonPath("$.data.content[0].quantity", is(4.0)))
                .andExpect(jsonPath("$.data.content[0].safetyStock", is(5)))
                .andExpect(jsonPath("$.data.content[0].stockItemId").doesNotExist());

        mockMvc.perform(as(get("/api/inventory/alerts").param("keyword", "hcm-c"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(1)));

        mockMvc.perform(as(get("/api/inventory/alerts").param("kind", "KHONG_CO"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tóm tắt đếm theo loại và mức; product-service lỗi thì báo không tính được tồn thấp")
    void summaryCountsAndProductServiceOutage() throws Exception {
        mockMvc.perform(as(get("/api/inventory/alerts/summary"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(5)))
                .andExpect(jsonPath("$.data.countsByKind.EXPIRED", is(1)))
                .andExpect(jsonPath("$.data.countsByKind.NEAR_EXPIRY", is(3)))
                .andExpect(jsonPath("$.data.countsByKind.LOW_STOCK", is(1)))
                .andExpect(jsonPath("$.data.countsByKind.DEAD_STOCK", is(0)))
                .andExpect(jsonPath("$.data.countsBySeverity.CRITICAL", is(2)))
                .andExpect(jsonPath("$.data.nearExpiryDays", is(30)))
                .andExpect(jsonPath("$.data.deadStockDays", is(90)))
                .andExpect(jsonPath("$.data.lowStockAvailable", is(true)))
                .andExpect(jsonPath("$.data.topItems", hasSize(5)));

        when(productClient.listProducts(anyInt(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("product-service không phản hồi"));

        mockMvc.perform(as(get("/api/inventory/alerts/summary"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockAvailable", is(false)))
                .andExpect(jsonPath("$.data.countsByKind.LOW_STOCK", is(0)))
                .andExpect(jsonPath("$.data.note").isNotEmpty());
        mockMvc.perform(as(get("/api/inventory/alerts").param("kind", "LOW_STOCK"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Quét tay chỉ dành cho ADMIN; thiếu thông tin người dùng là 401")
    void scanRequiresAdmin() throws Exception {
        mockMvc.perform(as(post("/api/inventory/alerts/scan"), COMPANY_A, "WAREHOUSE_MANAGER",
                        String.valueOf(warehouse1)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/inventory/alerts/scan").header("X-Company-Id", COMPANY_A))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(as(post("/api/inventory/alerts/scan"), COMPANY_A, "ADMIN", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.triggeredBy", is("admin")))
                .andExpect(jsonPath("$.data.results[?(@.kind == 'NEAR_EXPIRY')].status", hasItem("PUBLISHED")))
                .andExpect(jsonPath("$.data.results[?(@.kind == 'NEAR_EXPIRY')].warehouseCount", hasItem(2)))
                .andExpect(jsonPath("$.data.results[?(@.kind == 'DEAD_STOCK')].status", hasItem("NOTHING_FOUND")));
    }
}
