package vn.wms.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.DeductStockResponse;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.dto.StockTransferResponse;
import vn.wms.inventory.dto.UpdateStockTransferRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockTransfer;
import vn.wms.inventory.entity.StockTransferItem;
import vn.wms.inventory.entity.StockTransferStatus;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockTransferItemRepository;
import vn.wms.inventory.repository.StockTransferRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceTest {

    @Mock
    private StockTransferRepository stockTransferRepository;

    @Mock
    private StockTransferItemRepository stockTransferItemRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private BinLocationRepository binLocationRepository;

    @Mock
    private StockService stockService;

    @Mock
    private StockDeductionService stockDeductionService;

    @InjectMocks
    private StockTransferService stockTransferService;

    private Warehouse warehouseSource;
    private Warehouse warehouseDest;
    private BinLocation binSource;

    @BeforeEach
    void setUp() {
        warehouseSource = new Warehouse("WH-HN", "Kho Hà Nội", "HN", "Manager A", "0901", 1000);
        warehouseSource.setId(1L);

        warehouseDest = new Warehouse("WH-SG", "Kho TP.HCM", "SG", "Manager B", "0902", 1000);
        warehouseDest.setId(2L);

        binSource = new BinLocation(warehouseSource, "ZA", "R01", "B01", new BigDecimal("1000.00"));
        binSource.setId(10L);
    }

    @Test
    @DisplayName("Tạo lệnh điều chuyển kho thành công ở trạng thái DRAFT")
    void createTransfer_Success() {
        CreateStockTransferItemRequest itemReq = new CreateStockTransferItemRequest(
                501L, "SKU-01", "Sản phẩm A", "Hộp", binSource.getId(), new BigDecimal("20.000"), "Chuyển gấp"
        );
        CreateStockTransferRequest request = new CreateStockTransferRequest(
                warehouseSource.getId(), warehouseDest.getId(), Instant.now(), "Điều chuyển định kỳ", List.of(itemReq)
        );

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouseSource));
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(warehouseDest));
        when(binLocationRepository.findById(10L)).thenReturn(Optional.of(binSource));
        when(stockService.getAtp(501L, 1L))
                .thenReturn(StockAtpResponse.of(501L, 1L, new BigDecimal("100.000"), BigDecimal.ZERO));
        when(stockTransferRepository.countByTransferNumberPrefix(anyString())).thenReturn(0L);
        when(stockTransferRepository.existsByTransferNumber(anyString())).thenReturn(false);
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> {
            StockTransfer st = inv.getArgument(0);
            st.setId(100L);
            return st;
        });

        StockTransferResponse response = stockTransferService.createTransfer(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(StockTransferStatus.DRAFT);
        assertThat(response.sourceWarehouseCode()).isEqualTo("WH-HN");
        assertThat(response.destinationWarehouseCode()).isEqualTo("WH-SG");
        assertThat(response.totalItems()).isEqualTo(1);
        assertThat(response.totalQuantity()).isEqualByComparingTo(new BigDecimal("20.000"));
        verify(stockTransferRepository).save(any(StockTransfer.class));
    }

    @Test
    @DisplayName("Từ chối tạo lệnh khi kho nguồn và kho đích trùng nhau")
    void createTransfer_ThrowsWhenSourceEqualsDest() {
        CreateStockTransferRequest request = new CreateStockTransferRequest(
                1L, 1L, Instant.now(), "Trùng kho", List.of()
        );

        assertThatThrownBy(() -> stockTransferService.createTransfer(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Kho xuất nguồn và kho nhập đích không được trùng nhau");
    }

    @Test
    @DisplayName("Từ chối tạo lệnh khi tồn khả dụng ATP tại kho nguồn không đủ")
    void createTransfer_ThrowsWhenInsufficientAtp() {
        CreateStockTransferItemRequest itemReq = new CreateStockTransferItemRequest(
                501L, "SKU-01", "Sản phẩm A", "Hộp", null, new BigDecimal("50.000"), null
        );
        CreateStockTransferRequest request = new CreateStockTransferRequest(
                1L, 2L, Instant.now(), null, List.of(itemReq)
        );

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouseSource));
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(warehouseDest));
        // Chỉ còn 10 cái khả dụng ATP, trong khi yêu cầu 50
        when(stockService.getAtp(501L, 1L))
                .thenReturn(StockAtpResponse.of(501L, 1L, new BigDecimal("10.000"), BigDecimal.ZERO));

        assertThatThrownBy(() -> stockTransferService.createTransfer(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không đủ tồn khả dụng");
    }

    @Test
    @DisplayName("Cập nhật thành công khi lệnh còn ở trạng thái DRAFT")
    void updateTransfer_SuccessWhenDraft() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .transferNumber("TRF-20261110-0001")
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.DRAFT)
                .items(new ArrayList<>())
                .build();

        CreateStockTransferItemRequest newItem = new CreateStockTransferItemRequest(
                501L, "SKU-01", "Sản phẩm A", "Hộp", null, new BigDecimal("15.000"), null
        );
        UpdateStockTransferRequest request = new UpdateStockTransferRequest(
                Instant.now(), "Đã sửa ghi chú", List.of(newItem)
        );

        when(stockTransferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(stockService.getAtp(501L, 1L))
                .thenReturn(StockAtpResponse.of(501L, 1L, new BigDecimal("50.000"), BigDecimal.ZERO));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferResponse response = stockTransferService.updateTransfer(100L, request);

        assertThat(response.notes()).isEqualTo("Đã sửa ghi chú");
        assertThat(response.totalQuantity()).isEqualByComparingTo(new BigDecimal("15.000"));
    }

    @Test
    @DisplayName("Từ chối cập nhật khi lệnh không còn ở trạng thái DRAFT")
    void updateTransfer_ThrowsWhenNotDraft() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .status(StockTransferStatus.IN_TRANSIT)
                .sourceWarehouse(warehouseSource)
                .build();

        UpdateStockTransferRequest request = new UpdateStockTransferRequest(
                Instant.now(), "Đã sửa ghi chú", List.of()
        );

        when(stockTransferRepository.findById(100L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> stockTransferService.updateTransfer(100L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chỉ có thể chỉnh sửa lệnh điều chuyển khi còn ở trạng thái DRAFT");
    }

    @Test
    @DisplayName("Hủy lệnh điều chuyển thành công khi còn ở trạng thái DRAFT")
    void cancelTransfer_SuccessWhenDraft() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .status(StockTransferStatus.DRAFT)
                .sourceWarehouse(warehouseSource)
                .build();

        when(stockTransferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferResponse response = stockTransferService.cancelTransfer(100L, "Không cần chuyển nữa");

        assertThat(response.status()).isEqualTo(StockTransferStatus.CANCELLED);
        assertThat(response.notes()).contains("Lý do hủy: Không cần chuyển nữa");
    }

    @Test
    @DisplayName("Duyệt xuất kho chuyển đi đường (dispatch): Trừ tồn kho nguồn và chuyển trạng thái sang IN_TRANSIT")
    void dispatchTransfer_Success() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .transferNumber("TRF-20261110-0001")
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.DRAFT)
                .items(new ArrayList<>())
                .build();

        StockTransferItem item = StockTransferItem.builder()
                .transfer(transfer)
                .productId(501L)
                .skuCode("SKU-01")
                .sourceBinLocation(binSource)
                .quantity(new BigDecimal("25.000"))
                .transferredQuantity(BigDecimal.ZERO)
                .build();
        transfer.addItem(item);

        when(stockTransferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(stockDeductionService.deductStock(any(), anyString()))
                .thenReturn(new DeductStockResponse(100L, "TRF-20261110-0001", 1L, "COMPLETED", new BigDecimal("25.000"), List.of(), Instant.now()));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferResponse response = stockTransferService.dispatchTransfer(100L);

        assertThat(response.status()).isEqualTo(StockTransferStatus.IN_TRANSIT);
        assertThat(response.transferDate()).isNotNull();
        assertThat(response.totalTransferredQuantity()).isEqualByComparingTo(new BigDecimal("25.000"));
        verify(stockDeductionService).deductStock(any(), anyString());
    }

    @Test
    @DisplayName("Từ chối duyệt xuất khi lệnh không ở trạng thái DRAFT")
    void dispatchTransfer_ThrowsWhenNotDraft() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .status(StockTransferStatus.IN_TRANSIT)
                .sourceWarehouse(warehouseSource)
                .build();

        when(stockTransferRepository.findById(100L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> stockTransferService.dispatchTransfer(100L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chỉ có thể duyệt xuất chuyển đối với lệnh ở trạng thái DRAFT");
    }
}

