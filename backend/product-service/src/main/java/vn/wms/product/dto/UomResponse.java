package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.product.entity.Uom;

@Schema(description = "Thông tin đơn vị tính (Unit of Measure)")
public record UomResponse(
        @Schema(description = "ID đơn vị tính", example = "1")
        Long id,

        @Schema(description = "Mã đơn vị tính", example = "BOTTLE")
        String code,

        @Schema(description = "Tên đơn vị tính", example = "Chai")
        String name
) {
    public static UomResponse from(Uom uom) {
        return new UomResponse(uom.getId(), uom.getCode(), uom.getName());
    }
}
