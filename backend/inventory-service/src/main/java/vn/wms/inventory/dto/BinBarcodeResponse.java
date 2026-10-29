package vn.wms.inventory.dto;

/**
 * Dữ liệu tem mã vạch vị trí ô/kệ (WMS-06.2 - Bin Barcode).
 */
public record BinBarcodeResponse(
        Long locationId,
        String code,
        String warehouseCode,
        String warehouseName,
        String zone,
        String rack,
        String bin,
        String barcodeBase64
) {}

