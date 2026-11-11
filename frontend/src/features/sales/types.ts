/**
 * Kiểu dữ liệu cho phân hệ Đơn bán hàng (SO) & Tồn khả dụng (ATP) (FR14, FR15, FR16 - WMS-11).
 */

export type OrderStatus = 'DRAFT' | 'CONFIRMED' | 'BACKORDER' | 'COMPLETED' | 'CANCELLED'

export interface SalesOrderItem {
  id: number
  productId: number
  skuCode: string
  productName: string
  barcode?: string
  uom?: string
  quantity: number
  unitPrice: number
  discountPercent: number
  discountAmount: number
  taxRate: number
  taxAmount: number
  lineTotal: number
  onHandQuantity: number
  reservedQuantity: number
  atpQuantity: number
  isBackorder: boolean
  note?: string
}

export interface SalesOrderItemPayload {
  _key: string // Khóa định danh duy nhất cho từng dòng trong bảng UI React
  id?: number
  productId?: number
  skuCode: string
  productName: string
  uom: string
  quantity: number
  unitPrice: number
  discountPercent?: number
  discountAmount?: number
  taxRate?: number
  taxAmount?: number
  note?: string
  // Tồn kho và ATP cập nhật tức thời
  onHandQuantity?: number
  reservedQuantity?: number
  atpQuantity?: number
  isCheckingAtp?: boolean
}

export interface SalesOrder {
  id: number
  soNumber: string
  customerId: number
  customerCode?: string
  customerName?: string
  warehouseId?: number
  warehouseName?: string
  deliveryAddress?: string
  recipientName?: string
  recipientPhone?: string
  status: OrderStatus
  orderDate: string
  expectedDate?: string
  subtotalAmount: number
  discountAmount: number
  taxAmount: number
  totalAmount: number
  note?: string
  confirmedBy?: string
  confirmedAt?: string
  backorderReason?: string
  items: SalesOrderItem[]
  createdAt?: string
  updatedAt?: string
}

export interface CreateSalesOrderPayload {
  soNumber?: string
  customerId: number
  warehouseId?: number
  warehouseName?: string
  deliveryAddress?: string
  recipientName?: string
  recipientPhone?: string
  orderDate?: string
  expectedDate?: string
  note?: string
  status?: OrderStatus
  allowBackorder?: boolean
  items: Array<{
    productId: number
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
  }>
}

export interface UpdateSalesOrderPayload {
  customerId: number
  warehouseId?: number
  warehouseName?: string
  deliveryAddress?: string
  recipientName?: string
  recipientPhone?: string
  orderDate: string
  expectedDate?: string
  note?: string
  allowBackorder?: boolean
  items: Array<{
    productId: number
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
  }>
}

export interface SalesOrderSummary {
  totalOrders: number
  draftOrders: number
  backorders: number
  confirmedOrders: number
  pendingSaleOrders: number
  completedOrders: number
  cancelledOrders: number
  totalRevenue: number
}

export interface SalesOrderQueryParams {
  keyword?: string
  customerId?: number
  warehouseId?: number
  status?: OrderStatus
  fromOrderDate?: string
  toOrderDate?: string
  page?: number
  size?: number
}

export interface StockAtpResponse {
  productId: number
  warehouseId?: number
  onHandQuantity: number
  reservedQuantity: number
  atpQuantity: number
}

export interface AtpCheckRequest {
  warehouseId?: number
  items: Array<{
    productId: number
    skuCode?: string
    productName?: string
    quantity: number
  }>
}

export interface AtpCheckResponse {
  warehouseId?: number
  canFulfillAll: boolean
  items: Array<{
    productId: number
    skuCode: string
    productName: string
    requestedQuantity: number
    onHandQuantity: number
    reservedQuantity: number
    atpQuantity: number
    isSufficient: boolean
    shortageQuantity: number
  }>
}
