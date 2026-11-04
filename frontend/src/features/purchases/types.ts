/**
 * Types & DTOs cho Phân hệ Quản lý Đơn Mua Hàng (Purchase Orders - PO - FR10, WMS-09).
 */

export type OrderStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'CONFIRMED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'COMPLETED'

export interface PurchaseOrderItem {
  id?: number
  productId?: number | null
  skuCode: string
  productName: string
  uom?: string | null
  quantity: number
  unitPrice: number
  discountPercent?: number | null
  discountAmount?: number | null
  taxRate?: number | null
  taxAmount?: number | null
  lineTotal?: number
  note?: string | null
}

export interface PurchaseOrder {
  id: number
  poNumber: string
  supplierId: number
  supplierName?: string | null
  warehouseId?: number | null
  warehouseName?: string | null
  status: OrderStatus
  orderDate: string
  expectedDate?: string | null
  subtotalAmount: number
  discountAmount: number
  taxAmount: number
  totalAmount: number
  note?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  rejectedReason?: string | null
  items: PurchaseOrderItem[]
  createdAt?: string
  updatedAt?: string
}

export interface PurchaseOrderItemPayload {
  _key?: string
  productId?: number
  skuCode: string
  productName: string
  uom?: string
  quantity: number
  unitPrice: number
  discountPercent?: number
  discountAmount?: number
  taxRate?: number
  taxAmount?: number
  note?: string
}

export interface CreatePurchaseOrderPayload {
  poNumber?: string
  supplierId: number
  warehouseId?: number
  warehouseName?: string
  orderDate?: string
  expectedDate?: string
  note?: string
  status?: OrderStatus
  items: Omit<PurchaseOrderItemPayload, '_key'>[]
}

export interface UpdatePurchaseOrderPayload {
  supplierId?: number
  warehouseId?: number
  warehouseName?: string
  orderDate?: string
  expectedDate?: string
  note?: string
  items?: Omit<PurchaseOrderItemPayload, '_key'>[]
}

export interface RejectPurchaseOrderPayload {
  reason: string
}

export interface PurchaseOrderSummary {
  totalOrders: number
  draftOrders?: number
  pendingOrders: number
  approvedOrders: number
  rejectedOrders: number
  cancelledOrders: number
  totalApprovedAmount: number
}

export interface PurchaseOrderFilterParams {
  keyword?: string
  supplierId?: number
  warehouseId?: number
  status?: OrderStatus
  fromOrderDate?: string
  toOrderDate?: string
  page?: number
  size?: number
  sort?: string
}
