package vn.wms.inventory.service.report;

import org.springframework.stereotype.Service;
import vn.wms.inventory.dto.report.InOutBalanceQuery;
import vn.wms.inventory.dto.report.InOutBalanceReportResponse;
import vn.wms.inventory.dto.report.StockCardResponse;
import vn.wms.inventory.repository.WarehouseRepository;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lập file Excel của báo cáo kho (WMS-17.3): lấy dữ liệu qua {@link StockReportService} (cùng kiểm tra tham số,
 * vai trò, phạm vi kho và doanh nghiệp như API xem) rồi giao cho {@link StockReportExcelWriter} ghi file.
 * Mọi lỗi nghiệp vụ được ném trước khi ghi byte nào nên vẫn trả JSON {@code ApiResponse} như API thường.
 */
@Service
public class StockReportExportService {

    /** File đã lập: nội dung và tên file chỉ có ký tự ASCII. */
    public record ExportFile(byte[] content, String fileName) {
    }

    private final StockReportService stockReportService;
    private final StockReportExcelWriter writer;
    private final WarehouseRepository warehouseRepository;

    public StockReportExportService(StockReportService stockReportService, StockReportExcelWriter writer,
                                    WarehouseRepository warehouseRepository) {
        this.stockReportService = stockReportService;
        this.writer = writer;
        this.warehouseRepository = warehouseRepository;
    }

    public ExportFile exportInOutBalance(InOutBalanceQuery query) {
        InOutBalanceReportResponse report = stockReportService.getInOutBalanceForExport(query);
        List<String> filters = new ArrayList<>();
        filters.add("Kỳ: " + StockReportExcelWriter.formatDate(report.fromDate()) + " - "
                + StockReportExcelWriter.formatDate(report.toDate()));
        filters.add("Kho: " + warehouseFilterText(query.warehouseId(), report.warehouseIds() == null));
        if (query.categoryId() != null) {
            filters.add("Danh mục: ID " + query.categoryId());
        }
        if (query.productId() != null) {
            filters.add("Sản phẩm: " + productFilterText(query.productId(), report));
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            filters.add("Từ khóa: " + query.keyword().trim());
        }
        boolean byWarehouse = !"PRODUCT".equals(report.groupBy());
        filters.add("Gom dòng: " + (byWarehouse ? "theo sản phẩm và kho" : "theo sản phẩm (gộp các kho)"));
        String name = "bao-cao-xuat-nhap-ton_" + report.fromDate() + "_" + report.toDate() + ".xlsx";
        return new ExportFile(writer.writeInOutBalance(report, filters), name);
    }

    public ExportFile exportStockCard(Long productId, Long warehouseId, LocalDate fromDate, LocalDate toDate) {
        StockCardResponse card = stockReportService.getStockCardForExport(productId, warehouseId, fromDate, toDate);
        String sku = card.skuCode() != null && !card.skuCode().isBlank() ? card.skuCode() : "SP" + productId;
        String warehouse = card.warehouseCode() != null && !card.warehouseCode().isBlank()
                ? card.warehouseCode() : "kho" + warehouseId;
        String name = "the-kho_" + asciiToken(sku) + "_" + asciiToken(warehouse) + "_" + fromDate + "_" + toDate + ".xlsx";
        return new ExportFile(writer.writeStockCard(card), name);
    }

    private String warehouseFilterText(Long warehouseId, boolean allWarehousesOfCompany) {
        if (warehouseId != null) {
            return warehouseRepository.findById(warehouseId)
                    .map(w -> StockReportExcelWriter.warehouseLabel(w.getCode(), w.getName(), w.getId()))
                    .orElse("Kho ID " + warehouseId);
        }
        return allWarehousesOfCompany ? "Tất cả kho của doanh nghiệp" : "Tất cả kho được phân công";
    }

    private static String productFilterText(Long productId, InOutBalanceReportResponse report) {
        return report.rows().content().stream()
                .filter(r -> productId.equals(r.productId()))
                .findFirst()
                .map(r -> StockReportExcelWriter.productLabel(r.skuCode(), r.productName(), productId))
                .orElse("ID " + productId);
    }

    /** Chỉ giữ chữ, số, gạch ngang, gạch dưới và dấu chấm để tên file an toàn trên mọi hệ điều hành. */
    static String asciiToken(String value) {
        String s = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "x" : s;
    }
}
