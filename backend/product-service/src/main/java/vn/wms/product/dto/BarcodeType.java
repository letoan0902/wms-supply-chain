package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Loại mã vạch hỗ trợ sinh bởi hệ thống")
public enum BarcodeType {
    @Schema(description = "Mã vạch 1D Code 128 (chuẩn công nghiệp cho SKU, vị trí, chứng từ)")
    CODE_128,

    @Schema(description = "Mã vạch 1D EAN-13 (chuẩn bán lẻ quốc tế cho hàng tiêu dùng - 13 chữ số)")
    EAN_13,

    @Schema(description = "Mã phản hồi nhanh 2D QR Code (chứa dữ liệu lớn: SKU, Lô hàng, Hạn dùng)")
    QR_CODE
}
