/**
 * Kiểu dữ liệu Phiếu xuất kho (Goods Delivery Note - GDN) & Xuất xưởng (FR17 - WMS-12).
 */

export type GoodsDeliveryStatus = 'DRAFT' | 'DISPATCHED' | 'COMPLETED' | 'CANCELLED'

export interface CreateGdnItemRequest {
  salesOrderItemId: number
  productId: number
  lotNo?: string
  binLocationId?: number
  quantity: number
}

export interface CreateGdnRequest {
  salesOrderId: number
  warehouseId: number
  note?: string
  items: CreateGdnItemRequest[]
}

export interface GdnItemResponse {
  id: number
  salesOrderItemId: number
  productId: number
  productSku: string
  productName: string
  uom?: string
  lotNo?: string
  binLocationId?: number
  binCode?: string
  quantity: number
}

export interface GdnResponse {
  id: number
  code: string
  salesOrderId: number
  salesOrderCode: string
  warehouseId: number
  warehouseName?: string
  customerName?: string
  deliveryAddress?: string
  status: GoodsDeliveryStatus
  note?: string
  createdAt: string
  items: GdnItemResponse[]
}

export interface GdnSummaryResponse {
  totalGdn: number
  completedGdn: number
  dispatchedGdn: number
  draftGdn: number
  cancelledGdn: number
  totalDispatchedQuantity: number
}

export interface GdnSearchParams {
  keyword?: string
  status?: GoodsDeliveryStatus
  customerId?: number
  warehouseId?: number
  soId?: number
  fromDeliveryDate?: string
  toDeliveryDate?: string
  page?: number
  size?: number
}
