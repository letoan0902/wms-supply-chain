package vn.wms.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.wms.ai.dto.BulkImportResult;
import vn.wms.ai.dto.ExtractionResponse;
import vn.wms.ai.dto.ImportRequest;
import vn.wms.ai.service.AiExtractionService;
import vn.wms.common.api.ApiResponse;

/**
 * Trích xuất tài liệu thành dữ liệu danh mục, xem trước rồi mới nhập (WMS-24.2). Mọi API cần doanh nghiệp; vai trò
 * theo loại dữ liệu: hàng hóa và nhà cung cấp cho ADMIN, WAREHOUSE_MANAGER, PURCHASING; khách hàng cho ADMIN, SALES.
 */
@RestController
@RequestMapping("/api/ai/extractions")
@Tag(name = "AI nhập liệu", description = "Trích xuất file Excel, CSV, PDF, ảnh thành dữ liệu danh mục và nhập sau "
        + "khi người dùng xác nhận")
public class AiExtractionController {

    private final AiExtractionService extractionService;

    public AiExtractionController(AiExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Trích xuất một file (xlsx, xls, csv, pdf, png, jpg; tối đa 10 MB, 200 dòng) thành bảng xem "
            + "trước. Tính một lượt AI khi thành công, chưa ghi gì vào danh mục")
    public ApiResponse<ExtractionResponse> extract(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "dataType", required = false) String dataType) {
        return ApiResponse.ok(extractionService.extract(file, dataType));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem lại kết quả trích xuất trong vòng 1 giờ")
    public ApiResponse<ExtractionResponse> get(@PathVariable String id) {
        return ApiResponse.ok(extractionService.get(id));
    }

    @PostMapping("/{id}/import")
    @Operation(summary = "Nhập các dòng đã sửa và chọn qua API nhập hàng loạt, trả kết quả từng dòng. "
            + "Không tính lượt AI")
    public ApiResponse<BulkImportResult> importRows(@PathVariable String id,
                                                    @Valid @RequestBody ImportRequest request) {
        return ApiResponse.ok(extractionService.importRows(id, request));
    }
}
