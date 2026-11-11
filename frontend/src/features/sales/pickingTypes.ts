/**
 * Kiểu dữ liệu cho phân hệ Bảng xuất kho thời gian thực & Lộ trình lấy hàng thông minh (WMS-12.3 - FR17).
 */

export interface PickingItem {
  id: string // Khóa duy nhất: `pick-${soItemId}-${stockItemId}` hoặc `pick-${soItemId}-thieu` cho phần thiếu hàng
  soItemId: number
  productId: number
  skuCode: string
  productName: string
  uom: string
  /** Số lượng cần lấy tại điểm dừng này (một dòng SO có thể chia ra nhiều ô kệ / lô) */
  orderedQuantity: number
  pickedQuantity: number
  isPicked: boolean
  /** Phần số lượng không còn tồn khả dụng trong kho: không có vị trí để lấy */
  shortage?: boolean
  // Vị trí ô lưu cất lấy từ tồn kho thực tế (Zone -> Rack -> Bin)
  stockItemId?: number
  locationId?: number
  locationCode: string
  zone: string
  rack: string
  bin: string
  // Thông tin Lô & HSD (nếu có)
  lotNo?: string
  mfgDate?: string
  expDate?: string
  barcode?: string
  // Thứ tự chặng trong lộ trình lấy hàng tối ưu (1, 2, 3...)
  step: number
  note?: string
}
