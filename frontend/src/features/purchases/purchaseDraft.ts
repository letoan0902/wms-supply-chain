/**
 * Mở form lập đơn mua đã điền sẵn từ màn hình khác (ví dụ nút "Tạo đơn mua nháp" trên cảnh báo tồn thấp ở chuông
 * thông báo): truyền qua `state` của điều hướng tới `/purchases`, không lưu gì cho tới khi người dùng bấm lưu.
 */

/** Giá trị điền sẵn cho form lập đơn mua. Số lượng, đơn giá, nhà cung cấp để người dùng tự nhập. */
export interface PurchaseOrderPrefill {
  /** Sản phẩm cần mua; form đọc lại sản phẩm để lấy mã, tên, đơn vị tính, giá mua, thuế suất của danh mục */
  productId: number
  /** Mã SKU và tên lấy từ nguồn (có thể null khi nguồn không đọc được danh mục sản phẩm) */
  skuCode: string | null
  productName: string | null
  /** Kho nhận hàng; bỏ trống nếu nguồn không có */
  warehouseId?: number
  /** Mô tả nguồn hiển thị ở đầu form (không ghi vào đơn) */
  sourceDescription?: string
}

const STATE_KEY = 'purchaseOrderPrefill'

/** `state` truyền cho `navigate('/purchases', { state })`. */
export function purchasePrefillState(prefill: PurchaseOrderPrefill): Record<string, PurchaseOrderPrefill> {
  return { [STATE_KEY]: prefill }
}

function optionalString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

function positiveInteger(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : undefined
}

/** Đọc giá trị điền sẵn từ `location.state`; trả về null khi không có hoặc không hợp lệ. */
export function readPurchasePrefill(state: unknown): PurchaseOrderPrefill | null {
  if (typeof state !== 'object' || state === null) return null
  const raw: unknown = (state as Record<string, unknown>)[STATE_KEY]
  if (typeof raw !== 'object' || raw === null) return null
  const value = raw as Record<string, unknown>
  const productId = positiveInteger(value.productId)
  if (productId === undefined) return null
  return {
    productId,
    skuCode: optionalString(value.skuCode),
    productName: optionalString(value.productName),
    warehouseId: positiveInteger(value.warehouseId),
    sourceDescription: optionalString(value.sourceDescription) ?? undefined,
  }
}
