package vn.wms.inventory.dto;

import java.math.BigDecimal;

/**
 * Dòng không duyệt được vì tồn sau điều chỉnh sẽ nhỏ hơn số đang giữ chỗ cho đơn (kèm lỗi 409, trường {@code data}).
 *
 * @param lineId           ID dòng kiểm kê
 * @param binLocationCode  mã vị trí
 * @param productId        ID sản phẩm
 * @param lotNo            số lô
 * @param newQuantity      tồn sau điều chỉnh
 * @param reservedQuantity số đang giữ chỗ hiện tại
 */
public record StocktakeReservedConflict(Long lineId, String binLocationCode, Long productId, String lotNo,
                                        BigDecimal newQuantity, BigDecimal reservedQuantity) {
}
