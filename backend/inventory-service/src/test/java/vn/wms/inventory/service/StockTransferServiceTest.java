package vn.wms.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.DeductStockResponse;
import vn.wms.inventory.dto.ReceiveStockTransferItemRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.dto.StockTransferResponse;
import vn.wms.inventory.dto.UpdateStockTransferRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockTransfer;
import vn.wms.inventory.entity.StockTransferItem;
import vn.wms.inventory.entity.StockTransferStatus;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private StockItemRepository stockItemRepository;

    @Mock
    private StockService stockService;

    @Mock
    private StockDeductionService stockDeductionService;

    @Mock
    private WmsEventPublisher wmsEventPublisher;

    @Mock
    private StockMovementRecorder stockMovementRecorder;

    @InjectMocks
    private StockTransferService stockTransferService;

    private Warehouse warehouseSource;
    private Warehouse warehouseDest;
    private BinLocation binSource;
    private BinLocation binDest;

    @BeforeEach
    void setUp() {
        // Mockito tạo service qua constructor nên không tiêm trường @Autowired; gắn sổ kho giả lập bằng tay (WMS-17.1)
        ReflectionTestUtils.setField(stockTransferService, "stockMovementRecorder", stockMovementRecorder);
        warehouseSource = new Warehouse("WH-HN", "Kho Hà Nội", "HN", "Manager A", "0901", 1000);
        warehouseSource.setId(1L);

        warehouseDest = new Warehouse("WH-SG", "Kho TP.HCM", "SG", "Manager B", "0902", 1000);
        warehouseDest.setId(2L);

        binSource = new BinLocation(warehouseSource, "ZA", "R01", "B01", new BigDecimal("1000.00"));
        binSource.setId(10L);

        binDest = new BinLocation(warehouseDest, "ZB", "R02", "B02", new BigDecimal("1000.00"));
        binDest.setId(20L);
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

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));
        when(stockService.getAtp(501L, 1L))
                .thenReturn(StockAtpResponse.of(501L, 1L, new BigDecimal("100.000"), BigDecimal.ZERO));
        when(stockDeductionService.deductStock(any(), anyString()))
                .thenReturn(new DeductStockResponse(100L, "TRF-20261110-0001", 1L, "COMPLETED", new BigDecimal("25.000"), List.of(), Instant.now()));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferResponse response = stockTransferService.dispatchTransfer(100L);

        assertThat(response.status()).isEqualTo(StockTransferStatus.IN_TRANSIT);
        assertThat(response.transferDate()).isNotNull();
        assertThat(response.totalTransferredQuantity()).isEqualByComparingTo(new BigDecimal("25.000"));
        verify(stockDeductionService).deductStock(any(), anyString());
        // Không truyền ID phiếu điều chuyển làm orderId (orderId là ID đơn bán), mã phiếu vẫn là số chứng từ tham chiếu
        verify(stockDeductionService).deductStock(
                argThat(req -> req.orderId() == null
                        && "TRF-20261110-0001".equals(req.orderNumber())
                        && "TRANSFER_OUT".equals(req.reason())),
                anyString());
    }

    @Test
    @DisplayName("Từ chối duyệt xuất khi lệnh không ở trạng thái DRAFT")
    void dispatchTransfer_ThrowsWhenNotDraft() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .status(StockTransferStatus.IN_TRANSIT)
                .sourceWarehouse(warehouseSource)
                .build();

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> stockTransferService.dispatchTransfer(100L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chỉ có thể duyệt xuất chuyển đối với lệnh ở trạng thái DRAFT");
    }

    @Test
    @DisplayName("Nhận hàng tại kho đích thành công đủ 100%, không hư hỏng -> chuyển sang trạng thái COMPLETED (WMS-13.2)")
    void receiveTransfer_Success_Completed() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .transferNumber("TRF-20261110-0001")
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.IN_TRANSIT)
                .items(new ArrayList<>())
                .build();

        StockTransferItem item = StockTransferItem.builder()
                .id(1001L)
                .transfer(transfer)
                .productId(501L)
                .skuCode("SKU-01")
                .quantity(new BigDecimal("20.000"))
                .transferredQuantity(new BigDecimal("20.000"))
                .receivedQuantity(BigDecimal.ZERO)
                .damagedQuantity(BigDecimal.ZERO)
                .build();
        transfer.addItem(item);

        ReceiveStockTransferItemRequest itemReq = new ReceiveStockTransferItemRequest(
                1001L, new BigDecimal("20.000"), BigDecimal.ZERO, binDest.getId(), "Nhận đủ nguyên vẹn"
        );
        ReceiveStockTransferRequest request = new ReceiveStockTransferRequest(
                List.of(itemReq), "Biên bản giao nhận nguyên đai nguyên kiện"
        );

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));
        when(binLocationRepository.findById(binDest.getId())).thenReturn(Optional.of(binDest));
        when(stockItemRepository.findAllByBinLocationIdAndProductIdWithPessimisticLock(binDest.getId(), 501L))
                .thenReturn(List.of());
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferResponse response = stockTransferService.receiveTransfer(100L, request);

        assertThat(response.status()).isEqualTo(StockTransferStatus.COMPLETED);
        assertThat(response.totalReceivedQuantity()).isEqualByComparingTo(new BigDecimal("20.000"));
        assertThat(response.receivedBy()).isNotNull();
        assertThat(response.receivedAt()).isNotNull();
        assertThat(response.notes()).contains("Biên bản nhận hàng: Biên bản giao nhận nguyên đai nguyên kiện");
        assertThat(response.items().get(0).receivedQuantity()).isEqualByComparingTo(new BigDecimal("20.000"));
        assertThat(response.items().get(0).damagedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.items().get(0).destinationBinLocationCode()).isEqualTo(binDest.getCode());
        verify(stockItemRepository).save(any(StockItem.class));
    }

    @Test
    @DisplayName("Nhận hàng tại kho đích có hư hỏng/thiếu -> chuyển sang trạng thái PARTIAL_RECEIVED kèm biên bản (WMS-13.2)")
    void receiveTransfer_Success_PartialReceivedWithDamagedItems() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .transferNumber("TRF-20261110-0001")
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.IN_TRANSIT)
                .items(new ArrayList<>())
                .build();

        StockTransferItem item = StockTransferItem.builder()
                .id(1001L)
                .transfer(transfer)
                .productId(501L)
                .skuCode("SKU-01")
                .quantity(new BigDecimal("20.000"))
                .transferredQuantity(new BigDecimal("20.000"))
                .receivedQuantity(BigDecimal.ZERO)
                .damagedQuantity(BigDecimal.ZERO)
                .build();
        transfer.addItem(item);

        // Thực nhận 15, hư hỏng 5 do va đập
        ReceiveStockTransferItemRequest itemReq = new ReceiveStockTransferItemRequest(
                1001L, new BigDecimal("15.000"), new BigDecimal("5.000"), binDest.getId(), "5 hộp bẹp vỡ nát"
        );
        ReceiveStockTransferRequest request = new ReceiveStockTransferRequest(
                List.of(itemReq), "Có lập biên bản bất thường tổn thất hàng hóa"
        );

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));
        when(binLocationRepository.findById(binDest.getId())).thenReturn(Optional.of(binDest));
        when(stockItemRepository.findAllByBinLocationIdAndProductIdWithPessimisticLock(binDest.getId(), 501L))
                .thenReturn(List.of());
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferResponse response = stockTransferService.receiveTransfer(100L, request);

        assertThat(response.status()).isEqualTo(StockTransferStatus.PARTIAL_RECEIVED);
        assertThat(response.totalReceivedQuantity()).isEqualByComparingTo(new BigDecimal("15.000"));
        assertThat(response.notes()).contains("Biên bản nhận hàng: Có lập biên bản bất thường");
        assertThat(response.items().get(0).receivedQuantity()).isEqualByComparingTo(new BigDecimal("15.000"));
        assertThat(response.items().get(0).damagedQuantity()).isEqualByComparingTo(new BigDecimal("5.000"));
        assertThat(response.items().get(0).notes()).isEqualTo("5 hộp bẹp vỡ nát");
    }

    @Test
    @DisplayName("Từ chối nhận hàng khi lệnh điều chuyển chưa ở trạng thái IN_TRANSIT (WMS-13.2)")
    void receiveTransfer_ThrowsWhenNotInTransit() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .status(StockTransferStatus.DRAFT)
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .build();

        ReceiveStockTransferRequest request = new ReceiveStockTransferRequest(
                List.of(new ReceiveStockTransferItemRequest(1L, BigDecimal.TEN, BigDecimal.ZERO, 20L, null)), null
        );

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> stockTransferService.receiveTransfer(100L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chỉ có thể nhận hàng đối với lệnh điều chuyển ở trạng thái IN_TRANSIT");
    }

    @Test
    @DisplayName("Từ chối nhận hàng khi vị trí ô/kệ không thuộc kho đích (WMS-13.2)")
    void receiveTransfer_ThrowsWhenDestinationBinMismatch() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.IN_TRANSIT)
                .items(new ArrayList<>())
                .build();

        StockTransferItem item = StockTransferItem.builder()
                .id(1001L)
                .transfer(transfer)
                .productId(501L)
                .skuCode("SKU-01")
                .transferredQuantity(new BigDecimal("10.000"))
                .build();
        transfer.addItem(item);

        // Vị trí binSource thuộc warehouseSource chứ không thuộc warehouseDest
        ReceiveStockTransferItemRequest itemReq = new ReceiveStockTransferItemRequest(
                1001L, new BigDecimal("10.000"), BigDecimal.ZERO, binSource.getId(), null
        );
        ReceiveStockTransferRequest request = new ReceiveStockTransferRequest(List.of(itemReq), null);

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));
        when(binLocationRepository.findById(binSource.getId())).thenReturn(Optional.of(binSource));

        assertThatThrownBy(() -> stockTransferService.receiveTransfer(100L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không thuộc kho đích");
    }

    @Test
    @DisplayName("Từ chối nhận hàng khi tổng số lượng thực nhận và thiếu hỏng vượt quá số lượng đã xuất (WMS-13.2)")
    void receiveTransfer_ThrowsWhenTotalAccountedExceedsTransferred() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.IN_TRANSIT)
                .items(new ArrayList<>())
                .build();

        StockTransferItem item = StockTransferItem.builder()
                .id(1001L)
                .transfer(transfer)
                .productId(501L)
                .skuCode("SKU-01")
                .transferredQuantity(new BigDecimal("10.000"))
                .build();
        transfer.addItem(item);

        // Xuất 10 nhưng nhận 12
        ReceiveStockTransferItemRequest itemReq = new ReceiveStockTransferItemRequest(
                1001L, new BigDecimal("12.000"), BigDecimal.ZERO, binDest.getId(), null
        );
        ReceiveStockTransferRequest request = new ReceiveStockTransferRequest(List.of(itemReq), null);

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> stockTransferService.receiveTransfer(100L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("vượt quá số lượng đã xuất chuyển");
    }

    @Test
    @DisplayName("Từ chối nhận hàng khi chưa kiểm đếm đủ toàn bộ các mặt hàng trong lệnh (WMS-13.2)")
    void receiveTransfer_ThrowsWhenMissingItemsInRequest() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.IN_TRANSIT)
                .items(new ArrayList<>())
                .build();

        StockTransferItem item1 = StockTransferItem.builder().id(1001L).transfer(transfer).productId(501L).skuCode("SKU-01").transferredQuantity(BigDecimal.TEN).build();
        StockTransferItem item2 = StockTransferItem.builder().id(1002L).transfer(transfer).productId(502L).skuCode("SKU-02").transferredQuantity(BigDecimal.TEN).build();
        transfer.addItem(item1);
        transfer.addItem(item2);

        // Request chỉ có item1, thiếu item2
        ReceiveStockTransferItemRequest itemReq = new ReceiveStockTransferItemRequest(
                1001L, BigDecimal.TEN, BigDecimal.ZERO, binDest.getId(), null
        );
        ReceiveStockTransferRequest request = new ReceiveStockTransferRequest(List.of(itemReq), null);

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> stockTransferService.receiveTransfer(100L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cần kiểm đếm và xác nhận toàn bộ danh sách mặt hàng");
    }

    @Test
    @DisplayName("Từ chối duyệt xuất khi ATP lúc duyệt không còn đủ (tồn đã bị giữ chỗ cho đơn bán), không trừ tồn")
    void dispatchTransfer_ThrowsWhenAtpNoLongerSufficient() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .transferNumber("TRF-20261110-0001")
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.DRAFT)
                .items(new ArrayList<>())
                .build();
        // Hai dòng cùng sản phẩm: tổng 30 phải được so với ATP, không so từng dòng riêng lẻ
        transfer.addItem(StockTransferItem.builder().productId(501L).skuCode("SKU-01")
                .quantity(new BigDecimal("15.000")).build());
        transfer.addItem(StockTransferItem.builder().productId(501L).skuCode("SKU-01")
                .quantity(new BigDecimal("15.000")).build());

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));
        // OnHand 40 nhưng 20 đã giữ chỗ cho đơn bán: ATP còn 20 < 30
        when(stockService.getAtp(501L, 1L))
                .thenReturn(StockAtpResponse.of(501L, 1L, new BigDecimal("40.000"), new BigDecimal("20.000")));

        assertThatThrownBy(() -> stockTransferService.dispatchTransfer(100L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không đủ tồn khả dụng để duyệt xuất");
        verify(stockDeductionService, never()).deductStock(any(), anyString());
        assertThat(transfer.getStatus()).isEqualTo(StockTransferStatus.DRAFT);
    }

    @Test
    @DisplayName("Từ chối cập nhật lệnh khi vị trí nguồn không thuộc kho nguồn")
    void updateTransfer_ThrowsWhenSourceBinNotInSourceWarehouse() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .transferNumber("TRF-20261110-0001")
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.DRAFT)
                .items(new ArrayList<>())
                .build();

        // binDest thuộc kho đích, không phải kho nguồn
        CreateStockTransferItemRequest newItem = new CreateStockTransferItemRequest(
                501L, "SKU-01", "Sản phẩm A", "Hộp", binDest.getId(), new BigDecimal("15.000"), null
        );
        UpdateStockTransferRequest request = new UpdateStockTransferRequest(Instant.now(), null, List.of(newItem));

        when(stockTransferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(stockService.getAtp(501L, 1L))
                .thenReturn(StockAtpResponse.of(501L, 1L, new BigDecimal("50.000"), BigDecimal.ZERO));
        when(binLocationRepository.findById(binDest.getId())).thenReturn(Optional.of(binDest));

        assertThatThrownBy(() -> stockTransferService.updateTransfer(100L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không thuộc kho nguồn");
        verify(stockTransferRepository, never()).save(any(StockTransfer.class));
    }

    @Test
    @DisplayName("Nhận hàng không cộng vào dòng tồn của lô khác hoặc dòng bị khóa tại ô đích, mà tạo dòng không theo lô")
    void receiveTransfer_DoesNotMergeIntoLotTrackedOrLockedStockItem() {
        StockTransfer transfer = StockTransfer.builder()
                .id(100L)
                .transferNumber("TRF-20261110-0001")
                .sourceWarehouse(warehouseSource)
                .destinationWarehouse(warehouseDest)
                .status(StockTransferStatus.IN_TRANSIT)
                .items(new ArrayList<>())
                .build();
        StockTransferItem item = StockTransferItem.builder()
                .id(1001L)
                .transfer(transfer)
                .productId(501L)
                .skuCode("SKU-01")
                .quantity(new BigDecimal("20.000"))
                .transferredQuantity(new BigDecimal("20.000"))
                .build();
        transfer.addItem(item);

        StockItem lotRow = new StockItem();
        lotRow.setId(7001L);
        lotRow.setBinLocation(binDest);
        lotRow.setProductId(501L);
        lotRow.setLotNo("LOT-A");
        lotRow.setExpDate(java.time.LocalDate.now().plusDays(10));
        lotRow.setQuantity(new BigDecimal("5.000"));

        StockItem lockedRow = new StockItem();
        lockedRow.setId(7002L);
        lockedRow.setBinLocation(binDest);
        lockedRow.setProductId(501L);
        lockedRow.setLocked(true);
        lockedRow.setQuantity(new BigDecimal("3.000"));

        ReceiveStockTransferRequest request = new ReceiveStockTransferRequest(List.of(
                new ReceiveStockTransferItemRequest(1001L, new BigDecimal("20.000"), BigDecimal.ZERO, binDest.getId(), null)
        ), null);

        when(stockTransferRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(transfer));
        when(binLocationRepository.findById(binDest.getId())).thenReturn(Optional.of(binDest));
        when(stockItemRepository.findAllByBinLocationIdAndProductIdWithPessimisticLock(binDest.getId(), 501L))
                .thenReturn(List.of(lotRow, lockedRow));
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        stockTransferService.receiveTransfer(100L, request);

        assertThat(lotRow.getQuantity()).isEqualByComparingTo(new BigDecimal("5.000"));
        assertThat(lockedRow.getQuantity()).isEqualByComparingTo(new BigDecimal("3.000"));
        verify(stockItemRepository).save(argThat(s -> s.getId() == null
                && s.getLotNo() == null
                && s.getExpDate() == null
                && !s.isLocked()
                && s.getQuantity().compareTo(new BigDecimal("20.000")) == 0));
    }
}

