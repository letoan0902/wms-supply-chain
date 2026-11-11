/**
 * Kiểu dữ liệu cho phân hệ Bảng xuất kho thời gian thực & Lộ trình lấy hàng thông minh (WMS-12.3 - FR17).
 */

import type { OrderStatus, SalesOrder } from './types'

export type PickingItemStatus = 'PENDING' | 'IN_CART' | 'COMPLETED'

export interface PickingItem {
  id: string // Khóa duy nhất (ví dụ: `${soItemId}-${binLocationId}-${lotNo}`)
  soItemId: number
  productId: number
  skuCode: string
  productName: string
  uom: string
  orderedQuantity: number
  pickedQuantity: number
  isPicked: boolean
  // Vị trí ô lưu cất tối ưu (Zone -> Rack -> Bin)
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

export type OutboundPickingStatus = 'WAITING' | 'PICKING' | 'PICKED' | 'DISPATCHED'

export interface OutboundOrderRow {
  soId: number
  soNumber: string
  customerId: number
  customerName: string
  customerPhone?: string
  deliveryAddress?: string
  warehouseId?: number
  warehouseName?: string
  orderDate: string
  expectedDate?: string
  soStatus: OrderStatus
  totalAmount: number
  // Tiến độ nhặt hàng
  itemCount: number
  totalQuantity: number
  pickedQuantity: number
  pickingStatus: OutboundPickingStatus
  progressPercent: number
  items: PickingItem[]
  originalOrder: SalesOrder
}

export interface OutboundBoardSummary {
  waitingOrders: number
  pickingOrders: number
  dispatchedOrdersToday: number
  totalOutboundItems: number
  completionRate: number
}

export interface DeductStockItemPayload {
  productId: number
  skuCode: string
  quantity: number
  binLocationId?: number
  lotNo?: string
}

export interface DeductStockPayload {
  orderId: number
  orderNumber: string
  warehouseId: number
  reason: string
  note?: string
  items: DeductStockItemPayload[]
}
