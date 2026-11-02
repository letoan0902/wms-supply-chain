package vn.wms.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.BinBarcodeResponse;
import vn.wms.inventory.dto.BinLocationResponse;
import vn.wms.inventory.dto.BulkCreateBinLocationsRequest;
import vn.wms.inventory.dto.CreateBinLocationRequest;
import vn.wms.inventory.entity.BinLocation;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinLocationServiceTest {

    @Mock
    private BinLocationRepository binLocationRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @InjectMocks
    private BinLocationService binLocationService;

    private Warehouse sampleWarehouse;
    private BinLocation sampleLocation;

    @BeforeEach
    void setUp() {
        sampleWarehouse = new Warehouse();
        sampleWarehouse.setId(1L);
        sampleWarehouse.setCode("WH01");
        sampleWarehouse.setName("Kho Tổng");

        sampleLocation = new BinLocation();
        sampleLocation.setId(10L);
        sampleLocation.setWarehouse(sampleWarehouse);
        sampleLocation.setZone("ZA");
        sampleLocation.setRack("R01");
        sampleLocation.setBin("B01");
        sampleLocation.setCode("WH01-ZA-R01-B01");
        sampleLocation.setMaxWeight(new BigDecimal("1000.00"));
        sampleLocation.setStatus("EMPTY");
        sampleLocation.setActive(true);
    }

    @Test
    @DisplayName("Tạo vị trí ô/kệ đơn lẻ chuẩn hóa 4 cấp (Warehouse-Zone-Rack-Bin)")
    void createLocation_shouldBuildStandardCodeAndSave() {
        CreateBinLocationRequest request = new CreateBinLocationRequest(
                1L, "zb", "r02", "b05", new BigDecimal("800.00"), true);

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(sampleWarehouse));
        when(binLocationRepository.existsByCode("WH01-ZB-R02-B05")).thenReturn(false);
        when(binLocationRepository.save(any(BinLocation.class))).thenAnswer(invocation -> {
            BinLocation b = invocation.getArgument(0);
            b.setId(20L);
            return b;
        });

        BinLocationResponse response = binLocationService.createLocation(request);

        assertThat(response.code()).isEqualTo("WH01-ZB-R02-B05");
        assertThat(response.zone()).isEqualTo("ZB");
        assertThat(response.rack()).isEqualTo("R02");
        assertThat(response.bin()).isEqualTo("B05");
        assertThat(response.maxWeight()).isEqualTo(new BigDecimal("800.00"));
    }

    @Test
    @DisplayName("Từ chối tạo vị trí khi mã vị trí đã tồn tại")
    void createLocation_whenCodeExists_shouldThrowConflict() {
        CreateBinLocationRequest request = new CreateBinLocationRequest(
                1L, "ZA", "R01", "B01", new BigDecimal("1000.00"), true);

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(sampleWarehouse));
        when(binLocationRepository.existsByCode("WH01-ZA-R01-B01")).thenReturn(true);

        assertThatThrownBy(() -> binLocationService.createLocation(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đã tồn tại trong hệ thống");

        verify(binLocationRepository, never()).save(any(BinLocation.class));
    }

    @Test
    @DisplayName("Sinh hàng loạt vị trí tự động (Bulk Generate) theo dải kệ và ô")
    void bulkCreateLocations_shouldGenerateCartesianProduct() {
        // Sinh Kệ R01..R02 và Ô B01..B03 -> 2 x 3 = 6 vị trí
        BulkCreateBinLocationsRequest request = new BulkCreateBinLocationsRequest(
                1L, "ZA", "R", 1, 2, "B", 1, 3, new BigDecimal("500.00"));

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(sampleWarehouse));
        when(binLocationRepository.existsByCode(any())).thenReturn(false);
        when(binLocationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<BinLocationResponse> responses = binLocationService.bulkCreateLocations(request);

        assertThat(responses).hasSize(6);
        assertThat(responses.get(0).code()).isEqualTo("WH01-ZA-R01-B01");
        assertThat(responses.get(5).code()).isEqualTo("WH01-ZA-R02-B03");
    }

    @Test
    @DisplayName("Không cho phép xóa vị trí nếu đang có hàng tồn kho bên trong")
    void deleteLocation_whenHasStock_shouldThrowConflict() {
        when(binLocationRepository.findById(10L)).thenReturn(Optional.of(sampleLocation));
        when(stockItemRepository.existsActiveStockByBinLocationId(10L)).thenReturn(true);

        assertThatThrownBy(() -> binLocationService.deleteLocation(10L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đang có hàng hóa lưu trữ bên trong");

        verify(binLocationRepository, never()).delete(any(BinLocation.class));
    }

    @Test
    @DisplayName("Sinh mã vạch Code 128 dạng Base64 hợp lệ (WMS-06.2)")
    void generateBarcode_shouldReturnBase64Png() {
        when(binLocationRepository.findById(10L)).thenReturn(Optional.of(sampleLocation));

        BinBarcodeResponse barcode = binLocationService.generateBarcode(10L);

        assertThat(barcode).isNotNull();
        assertThat(barcode.code()).isEqualTo("WH01-ZA-R01-B01");
        assertThat(barcode.barcodeBase64()).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("Sinh mảng byte PNG mã vạch Code 128 hợp lệ (WMS-06.2)")
    void generateBarcodePng_shouldReturnValidPngBytes() {
        when(binLocationRepository.findById(10L)).thenReturn(Optional.of(sampleLocation));

        byte[] pngBytes = binLocationService.generateBarcodePng(10L);

        assertThat(pngBytes).isNotEmpty();
        // Kiểm tra magic number của PNG file (0x89 0x50 0x4E 0x47)
        assertThat(pngBytes[0]).isEqualTo((byte) 0x89);
        assertThat(pngBytes[1]).isEqualTo((byte) 'P');
        assertThat(pngBytes[2]).isEqualTo((byte) 'N');
        assertThat(pngBytes[3]).isEqualTo((byte) 'G');
    }
}

