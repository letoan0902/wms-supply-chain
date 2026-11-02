package vn.wms.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.product.dto.BarcodeResponse;
import vn.wms.product.dto.BarcodeType;
import vn.wms.product.dto.BatchBarcodeRequest;
import vn.wms.product.dto.BatchBarcodeResponse;
import vn.wms.product.service.BarcodeService;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Barcodes", description = "Tích hợp ZXing tự động sinh Barcode (Code 128, EAN-13) & QR Code (WMS-07.3)")
public class BarcodeController {

    private final BarcodeService barcodeService;

    public BarcodeController(BarcodeService barcodeService) {
        this.barcodeService = barcodeService;
    }

    @GetMapping("/barcodes/generate")
    @Operation(summary = "Sinh mã vạch / QR Code tùy ý dạng JSON kèm Base64 Data URI")
    public ApiResponse<BarcodeResponse> generateGenericBarcode(
            @Parameter(description = "Nội dung cần mã hóa (SKU, mã phiếu, mã vị trí...)")
            @RequestParam String text,
            @Parameter(description = "Loại mã (CODE_128, EAN_13, QR_CODE; mặc định CODE_128)")
            @RequestParam(required = false) BarcodeType type,
            @Parameter(description = "Chiều rộng ảnh (pixel)")
            @RequestParam(required = false) Integer width,
            @Parameter(description = "Chiều cao ảnh (pixel)")
            @RequestParam(required = false) Integer height,
            @Parameter(description = "Kèm nhãn chữ bên dưới cho mã 1D (mặc định true)")
            @RequestParam(required = false, defaultValue = "true") Boolean includeLabel) {
        return ApiResponse.ok(barcodeService.generateGenericBarcode(text, type, width, height, includeLabel));
    }

    @GetMapping(value = "/barcodes/generate/image", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Sinh mã vạch / QR Code tùy ý trả về trực tiếp file ảnh PNG")
    public ResponseEntity<byte[]> generateGenericBarcodeImage(
            @Parameter(description = "Nội dung cần mã hóa")
            @RequestParam String text,
            @Parameter(description = "Loại mã (CODE_128, EAN_13, QR_CODE)")
            @RequestParam(required = false) BarcodeType type,
            @Parameter(description = "Chiều rộng ảnh")
            @RequestParam(required = false) Integer width,
            @Parameter(description = "Chiều cao ảnh")
            @RequestParam(required = false) Integer height,
            @Parameter(description = "Kèm nhãn chữ bên dưới")
            @RequestParam(required = false, defaultValue = "true") Boolean includeLabel) {
        BarcodeType resolvedType = (type != null) ? type : BarcodeType.CODE_128;
        int resolvedWidth = (width != null && width > 0) ? width : (resolvedType == BarcodeType.QR_CODE ? 250 : 300);
        int resolvedHeight = (height != null && height > 0) ? height : (resolvedType == BarcodeType.QR_CODE ? 250 : 100);

        byte[] imageBytes = barcodeService.generatePng(text, resolvedType, resolvedWidth, resolvedHeight, includeLabel);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"barcode.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }

    @GetMapping("/{id}/barcode")
    @Operation(summary = "Lấy ảnh mã vạch / QR Code của sản phẩm theo ID dạng JSON Base64")
    public ApiResponse<BarcodeResponse> getProductBarcode(
            @Parameter(description = "ID sản phẩm") @PathVariable Long id,
            @Parameter(description = "Loại mã (CODE_128, EAN_13, QR_CODE; mặc định CODE_128)")
            @RequestParam(required = false) BarcodeType type,
            @Parameter(description = "Chiều rộng")
            @RequestParam(required = false) Integer width,
            @Parameter(description = "Chiều cao")
            @RequestParam(required = false) Integer height,
            @Parameter(description = "Kèm nhãn chữ")
            @RequestParam(required = false, defaultValue = "true") Boolean includeLabel) {
        return ApiResponse.ok(barcodeService.generateProductBarcode(id, type, width, height, includeLabel));
    }

    @GetMapping(value = "/{id}/barcode/image", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Lấy trực tiếp file ảnh PNG mã vạch / QR Code của sản phẩm theo ID")
    public ResponseEntity<byte[]> getProductBarcodeImage(
            @Parameter(description = "ID sản phẩm") @PathVariable Long id,
            @Parameter(description = "Loại mã (CODE_128, EAN_13, QR_CODE)")
            @RequestParam(required = false) BarcodeType type,
            @Parameter(description = "Chiều rộng")
            @RequestParam(required = false) Integer width,
            @Parameter(description = "Chiều cao")
            @RequestParam(required = false) Integer height,
            @Parameter(description = "Kèm nhãn chữ")
            @RequestParam(required = false, defaultValue = "true") Boolean includeLabel) {
        BarcodeResponse response = barcodeService.generateProductBarcode(id, type, width, height, includeLabel);
        byte[] imageBytes = barcodeService.generatePng(response.content(), response.type(), response.width(), response.height(), includeLabel);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + response.content() + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }

    @PostMapping("/{id}/barcode/batch")
    @Operation(summary = "Tạo tem nhãn Barcode / QR Code kết hợp thông tin SKU, Lô hàng & Hạn sử dụng (FR07)")
    public ApiResponse<BatchBarcodeResponse> generateBatchBarcode(
            @Parameter(description = "ID sản phẩm") @PathVariable Long id,
            @Valid @RequestBody BatchBarcodeRequest request) {
        return ApiResponse.ok(barcodeService.generateProductBatchBarcode(id, request));
    }
}
