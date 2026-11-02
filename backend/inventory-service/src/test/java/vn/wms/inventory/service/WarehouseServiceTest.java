package vn.wms.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.CreateWarehouseRequest;
import vn.wms.inventory.dto.UpdateWarehouseRequest;
import vn.wms.inventory.dto.WarehouseResponse;
import vn.wms.inventory.dto.WarehouseSummaryResponse;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private BinLocationRepository binLocationRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse sampleWarehouse;

    @BeforeEach
    void setUp() {
        sampleWarehouse = new Warehouse();
        sampleWarehouse.setId(1L);
        sampleWarehouse.setCode("WH-HCM-01");
        sampleWarehouse.setName("Kho Tổng TP.HCM");
        sampleWarehouse.setAddress("KCN Tân Bình, TP.HCM");
        sampleWarehouse.setManagerName("Nguyễn Văn Quản Lý");
        sampleWarehouse.setContactPhone("0901234567");
        sampleWarehouse.setCapacity(500);
        sampleWarehouse.setActive(true);
    }

    @Test
    @DisplayName("Tạo mới kho hàng thành công khi mã kho chưa tồn tại")
    void createWarehouse_whenCodeIsUnique_shouldSucceed() {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH-HN-01", "Kho Hà Nội", "Long Biên, HN", "Trần Văn A", "0988776655", 300, true);

        when(warehouseRepository.existsByCode("WH-HN-01")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse w = invocation.getArgument(0);
            w.setId(2L);
            return w;
        });

        WarehouseResponse response = warehouseService.createWarehouse(request);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("WH-HN-01");
        assertThat(response.name()).isEqualTo("Kho Hà Nội");
        assertThat(response.capacity()).isEqualTo(300);
        verify(warehouseRepository).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("Từ chối tạo kho khi mã kho đã tồn tại (ném ApiException conflict)")
    void createWarehouse_whenCodeAlreadyExists_shouldThrowConflict() {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH-HCM-01", "Kho Trùng Mã", "Địa chỉ", "Trưởng kho", "0900000000", 100, true);

        when(warehouseRepository.existsByCode("WH-HCM-01")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.createWarehouse(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Mã kho đã tồn tại");

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("Cập nhật thông tin kho hàng thành công")
    void updateWarehouse_shouldUpdateFieldsAndSave() {
        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "Kho Tổng Miền Nam", "Địa chỉ mới", "Trưởng kho mới", "0999999999", 600, true);

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(sampleWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.updateWarehouse(1L, request);

        assertThat(response.name()).isEqualTo("Kho Tổng Miền Nam");
        assertThat(response.capacity()).isEqualTo(600);
        assertThat(response.managerName()).isEqualTo("Trưởng kho mới");
    }

    @Test
    @DisplayName("Không cho phép xóa kho nếu kho đang có hàng tồn")
    void deleteWarehouse_whenWarehouseHasStock_shouldThrowConflict() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(sampleWarehouse));
        when(stockItemRepository.existsActiveStockByWarehouseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.deleteWarehouse(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("còn hàng hóa đang tồn tại");

        verify(warehouseRepository, never()).delete(any(Warehouse.class));
    }

    @Test
    @DisplayName("Không cho phép xóa kho nếu kho đang có vị trí ô/kệ lưu trữ")
    void deleteWarehouse_whenWarehouseHasLocations_shouldThrowConflict() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(sampleWarehouse));
        when(stockItemRepository.existsActiveStockByWarehouseId(1L)).thenReturn(false);
        when(binLocationRepository.existsByWarehouseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.deleteWarehouse(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("còn vị trí ô/kệ lưu trữ liên kết");

        verify(warehouseRepository, never()).delete(any(Warehouse.class));
    }

    @Test
    @DisplayName("Tính toán đúng các chỉ số Thẻ tóm tắt năng lực đa kho (WMS-05.2)")
    void getSummaryMetrics_shouldCalculateCorrectly() {
        when(warehouseRepository.findAll()).thenReturn(List.of(sampleWarehouse));
        when(binLocationRepository.count()).thenReturn(100L);
        when(stockItemRepository.countTotalOccupiedLocations()).thenReturn(40L);
        when(stockItemRepository.countDistinctActiveProducts()).thenReturn(25L);
        when(stockItemRepository.sumTotalQuantity()).thenReturn(new BigDecimal("1500.000"));

        WarehouseSummaryResponse summary = warehouseService.getSummaryMetrics();

        assertThat(summary.totalWarehouses()).isEqualTo(1L);
        assertThat(summary.activeWarehouses()).isEqualTo(1L);
        assertThat(summary.totalCapacity()).isEqualTo(500L);
        assertThat(summary.totalLocations()).isEqualTo(100L);
        assertThat(summary.occupiedLocations()).isEqualTo(40L);
        assertThat(summary.availableLocations()).isEqualTo(60L);
        assertThat(summary.totalProducts()).isEqualTo(25L);
        assertThat(summary.totalStockQuantity()).isEqualTo(new BigDecimal("1500.000"));
    }
}

