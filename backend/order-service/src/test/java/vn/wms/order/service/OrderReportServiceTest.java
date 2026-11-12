package vn.wms.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.client.WarehouseSummary;
import vn.wms.order.dto.report.TrendingItemsReportResponse;
import vn.wms.order.repository.SalesOrderItemRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReportServiceTest {

    @Mock
    private SalesOrderItemRepository salesOrderItemRepository;

    @Mock
    private InventoryGateway inventoryGateway;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private OrderReportService orderReportService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        orderReportService = new OrderReportService(salesOrderItemRepository, inventoryGateway, redis, objectMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();
    }

    private void mockUser(String username, String roles, String warehouseIds, Long companyId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Name", username);
        request.addHeader("X-User-Roles", roles);
        if (warehouseIds != null) {
            request.addHeader("X-User-Warehouse-Ids", warehouseIds);
        }
        if (companyId != null) {
            request.addHeader("X-Company-Id", String.valueOf(companyId));
        }
        // Ở môi trường chạy, bộ lọc tenant đặt doanh nghiệp từ header; test gọi thẳng service nên đặt tay
        TenantContext.setCompanyId(companyId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("Admin xem báo cáo: Tính toán chính xác tỷ trọng Top 3 mặt hàng xuất bán chạy")
    void getTrendingItems_asAdmin_calculatesPercentagesAndTopSlicesCorrectly() {
        mockUser("admin_user", Roles.ADMIN, null, 1L);

        List<Object[]> rawRows = new ArrayList<>();
        // row: [productId, skuCode, productName, uom, totalQty]
        rawRows.add(new Object[]{101L, "SKU-OIL-1L", "Dầu ăn Tường An 1L", "Chai", new BigDecimal("500")});
        rawRows.add(new Object[]{102L, "SKU-SOY-500", "Nước tương Maggi 500ml", "Chai", new BigDecimal("300")});
        rawRows.add(new Object[]{103L, "SKU-CHILI-250", "Tương ớt Chinsu 250g", "Chai", new BigDecimal("200")});

        when(salesOrderItemRepository.findTopSoldProducts(any(), any(), any(), anyBoolean(), any())).thenReturn(rawRows);

        TrendingItemsReportResponse response = orderReportService.getTrendingItems(null, "THIS_MONTH", 5);

        assertThat(response).isNotNull();
        assertThat(response.totalVolume()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(response.slices()).hasSize(3);

        // Slice 1: Dầu ăn (500 / 1000 = 50.0%)
        assertThat(response.slices().get(0).sku()).isEqualTo("SKU-OIL-1L");
        assertThat(response.slices().get(0).quantity()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(response.slices().get(0).percentage()).isEqualTo(50.0);
        assertThat(response.slices().get(0).color()).isEqualTo("#1890ff");

        // Slice 2: Nước tương (300 / 1000 = 30.0%)
        assertThat(response.slices().get(1).sku()).isEqualTo("SKU-SOY-500");
        assertThat(response.slices().get(1).quantity()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(response.slices().get(1).percentage()).isEqualTo(30.0);
        assertThat(response.slices().get(1).color()).isEqualTo("#52c41a");

        // Slice 3: Tương ớt (200 / 1000 = 20.0%)
        assertThat(response.slices().get(2).sku()).isEqualTo("SKU-CHILI-250");
        assertThat(response.slices().get(2).quantity()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(response.slices().get(2).percentage()).isEqualTo(20.0);
        assertThat(response.slices().get(2).color()).isEqualTo("#fa8c16");
    }

    @Test
    @DisplayName("Limit = 2: Mặt hàng vượt quá limit được tự động gom vào nhóm 'Mặt hàng khác'")
    void getTrendingItems_withLimitGroupingOthers_groupsOverflowIntoKhac() {
        mockUser("admin_user", Roles.ADMIN, null, 1L);

        List<Object[]> rawRows = new ArrayList<>();
        rawRows.add(new Object[]{101L, "SKU-1", "Sản phẩm 1", "Hộp", new BigDecimal("500")});
        rawRows.add(new Object[]{102L, "SKU-2", "Sản phẩm 2", "Hộp", new BigDecimal("300")});
        rawRows.add(new Object[]{103L, "SKU-3", "Sản phẩm 3", "Hộp", new BigDecimal("200")});

        when(salesOrderItemRepository.findTopSoldProducts(any(), any(), any(), anyBoolean(), any())).thenReturn(rawRows);

        TrendingItemsReportResponse response = orderReportService.getTrendingItems(null, "7_DAYS", 2);

        assertThat(response.slices()).hasSize(3); // 2 top items + 1 'Khác'
        assertThat(response.slices().get(0).sku()).isEqualTo("SKU-1");
        assertThat(response.slices().get(1).sku()).isEqualTo("SKU-2");

        // Lát cắt 'Khác'
        var otherSlice = response.slices().get(2);
        assertThat(otherSlice.sku()).isEqualTo("OTHERS");
        assertThat(otherSlice.name()).isEqualTo("Mặt hàng khác");
        assertThat(otherSlice.quantity()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(otherSlice.percentage()).isEqualTo(20.0);
        assertThat(otherSlice.color()).isEqualTo("#8c8c8c");
    }

    @Test
    @DisplayName("Chưa xác thực: Chặn 401 Unauthorized khi không có User Context")
    void getTrendingItems_unauthenticated_throwsUnauthorized() {
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(() -> orderReportService.getTrendingItems(1L, "7_DAYS", 5))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Yêu cầu thông tin xác thực người dùng");
    }

    @Test
    @DisplayName("Nhân viên kho truy cập kho không được phân quyền: Chặn 403 Forbidden")
    void getTrendingItems_warehouseStaffAccessingUnauthorizedWarehouse_throwsForbidden() {
        // Staff chỉ có quyền kho 1
        mockUser("staff_user", Roles.WAREHOUSE_STAFF, "1", 1L);

        // Cố tình truy cập kho 2
        assertThatThrownBy(() -> orderReportService.getTrendingItems(2L, "7_DAYS", 5))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không có quyền truy cập dữ liệu của kho ID: 2")
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(salesOrderItemRepository);
    }

    @Test
    @DisplayName("Không có doanh nghiệp trong ngữ cảnh: COMPANY_REQUIRED, không rơi về doanh nghiệp 1 và không đọc cache")
    void getTrendingItems_withoutCompany_throwsCompanyRequired() {
        mockUser("admin_user", Roles.ADMIN, null, null);

        assertThatThrownBy(() -> orderReportService.getTrendingItems(null, "7_DAYS", 5))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMPANY_REQUIRED);
        verifyNoInteractions(valueOperations, salesOrderItemRepository);
    }

    @Test
    @DisplayName("Khóa cache tách theo doanh nghiệp: doanh nghiệp 2 không đọc khóa của doanh nghiệp 1")
    void getTrendingItems_cacheKeyContainsCompany() {
        mockUser("admin_b", Roles.ADMIN, null, 2L);
        when(salesOrderItemRepository.findTopSoldProducts(any(), any(), any(), anyBoolean(), any())).thenReturn(List.of());

        orderReportService.getTrendingItems(null, "7_DAYS", 5);

        verify(valueOperations).get("wms:orders:company:2:trending:all:7_DAYS:5");
    }

    @Test
    @DisplayName("Nhân viên kho chưa được gán kho nào: 403, không rơi về số liệu toàn doanh nghiệp")
    void getTrendingItems_staffWithoutWarehouses_throwsForbidden() {
        mockUser("staff_none", Roles.WAREHOUSE_STAFF, null, 1L);

        assertThatThrownBy(() -> orderReportService.getTrendingItems(null, "7_DAYS", 5))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(salesOrderItemRepository);
    }

    @Test
    @DisplayName("Quản lý kho không chọn kho: lọc theo đúng các kho được gán")
    void getTrendingItems_managerWithoutWarehouseParam_filtersAssignedWarehouses() {
        mockUser("manager", Roles.WAREHOUSE_MANAGER, "3,1", 1L);
        when(salesOrderItemRepository.findTopSoldProducts(any(), any(), any(), anyBoolean(), any())).thenReturn(List.of());

        TrendingItemsReportResponse response = orderReportService.getTrendingItems(null, "7_DAYS", 5);

        verify(salesOrderItemRepository).findTopSoldProducts(any(), any(), any(), eq(false), eq(List.of(1L, 3L)));
        assertThat(response.warehouseName()).isEqualTo("2 kho được phân công");
        assertThat(response.slices()).isEmpty();
        assertThat(response.totalVolume()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Admin không chọn kho: không lọc theo kho")
    void getTrendingItems_adminWithoutWarehouse_queriesAllWarehouses() {
        mockUser("admin_user", Roles.ADMIN, null, 1L);
        when(salesOrderItemRepository.findTopSoldProducts(any(), any(), any(), anyBoolean(), any())).thenReturn(List.of());

        orderReportService.getTrendingItems(null, null, null);

        verify(salesOrderItemRepository).findTopSoldProducts(any(), any(), any(), eq(true), any());
    }

    @Test
    @DisplayName("Khoảng thời gian hoặc limit không hợp lệ: 400 thay vì âm thầm đổi sang mặc định")
    void getTrendingItems_invalidParams_throwBadRequest() {
        mockUser("admin_user", Roles.ADMIN, null, 1L);

        assertThatThrownBy(() -> orderReportService.getTrendingItems(null, "LAST_YEAR", 5))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> orderReportService.getTrendingItems(null, "7_DAYS", 0))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> orderReportService.getTrendingItems(null, "7_DAYS", 21))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoInteractions(salesOrderItemRepository);
    }

    @Test
    @DisplayName("Cache Hit: Đọc dữ liệu từ Redis và trả về ngay lập tức, không gọi database")
    void getTrendingItems_cacheHit_returnsCachedResponseImmediately() throws Exception {
        mockUser("admin_user", Roles.ADMIN, null, 1L);

        TrendingItemsReportResponse cached = new TrendingItemsReportResponse(
                "Trending Items - Mặt hàng xuất bán chạy",
                new BigDecimal("1280"),
                "sản phẩm",
                "7_DAYS",
                "Tất cả các kho",
                List.of(),
                new TrendingItemsReportResponse.ReportMeta(1L, null, null, false)
        );
        String cachedJson = objectMapper.writeValueAsString(cached);

        when(valueOperations.get(anyString())).thenReturn(cachedJson);

        TrendingItemsReportResponse response = orderReportService.getTrendingItems(null, "7_DAYS", 5);

        assertThat(response).isNotNull();
        assertThat(response.totalVolume()).isEqualByComparingTo(new BigDecimal("1280"));
        assertThat(response.meta().fromCache()).isTrue();

        verify(salesOrderItemRepository, never()).findTopSoldProducts(any(), any(), any(), anyBoolean(), any());
    }
}
