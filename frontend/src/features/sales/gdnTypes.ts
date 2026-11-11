/**
 * Kiểu dữ liệu Phiếu xuất kho (Goods Delivery Note - GDN) & Xuất xưởng (WMS-12).
 * Khớp các DTO của order-service: CreateGdnRequest, CreateGdnItemRequest, GdnResponse, GdnItemResponse,
 * GdnSummaryResponse và enum GoodsDeliveryStatus.
 */

export type GoodsDeliveryStatus = 'DRAFT' | 'PICKING' | 'COMPLETED' | 'CANCELLED'

/** Khớp vn.wms.order.dto.CreateGdnItemRequest */
export interface CreateGdnItemRequest {
  soItemId: number
  productId: number
  skuCode?: string
  productName?: string
  uom?: string
  /** Số lượng thực xuất, backend yêu cầu > 0 (DECIMAL(18,3)) */
  deliveredQuantity: number
  unitPrice?: number
  batchNumber?: string
  locationId?: number
  locationCode?: string
  note?: string
}

/** Khớp vn.wms.order.dto.CreateGdnRequest. Kho xuất lấy theo đơn bán, không gửi từ trình duyệt. */
export interface CreateGdnRequest {
  soId: number
  /** yyyy-MM-dd, để trống thì backend lấy ngày hôm nay */
  deliveryDate?: string
  /** Mặc định COMPLETED (xuất ngay); backend từ chối CANCELLED */
  status?: Exclude<GoodsDeliveryStatus, 'CANCELLED'>
  recipientName?: string
  recipientPhone?: string
  deliveryAddress?: string
  note?: string
  items: CreateGdnItemRequest[]
}

/** Khớp vn.wms.order.dto.GdnItemResponse */
export interface GdnItemResponse {
  id: number
  soItemId?: number
  productId: number
  skuCode?: string
  productName?: string
  uom?: string
  orderedQuantity?: number
  deliveredQuantity: number
  unitPrice?: number
  lineTotal?: number
  batchNumber?: string
  locationId?: number
  locationCode?: string
  note?: string
  createdAt?: string
}

/** Khớp vn.wms.order.dto.GdnResponse */
export interface GdnResponse {
  id: number
  gdnNumber: string
  soId?: number
  soNumber?: string
  customerId?: number
  customerName?: string
  warehouseId?: number
  warehouseName?: string
  status: GoodsDeliveryStatus
  deliveryDate?: string
  totalQuantity?: number
  totalAmount?: number
  recipientName?: string
  recipientPhone?: string
  deliveryAddress?: string
  note?: string
  dispatchedBy?: string
  dispatchedAt?: string
  items: GdnItemResponse[]
  createdAt?: string
  updatedAt?: string
}

/** Khớp vn.wms.order.dto.GdnSummaryResponse */
export interface GdnSummaryResponse {
  totalGdns: number
  draftGdns: number
  pickingGdns: number
  completedGdns: number
  cancelledGdns: number
  totalQuantityDispatched: number
  totalAmountDispatched: number
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
