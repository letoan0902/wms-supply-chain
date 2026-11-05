/**
 * Kiểu dữ liệu của thông báo thời gian thực (WMS-02.4).
 * Hợp đồng với notification-service: client nối SockJS/STOMP tới `/ws-wms`, mọi tin trên các topic
 * là một phong bì JSON `{ id, type, severity, title, message, occurredAt, payload }`.
 */

/** Trạng thái kết nối thời gian thực hiển thị trên Header. */
export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected'

/** Mức độ của thông báo, trùng tên với kiểu thông báo của Ant Design. */
export type RealtimeSeverity = 'info' | 'success' | 'warning' | 'error'

export type OrderType = 'PURCHASE' | 'SALES'

/** Dòng hàng trong sự kiện đơn hàng. */
export interface RealtimeOrderItem {
  productId: number | null
  skuCode: string | null
  productName: string | null
  quantity: number | null
  uom: string | null
  unitPrice?: number | null
}

export interface LowStockPayload {
  warehouseId: number | null
  warehouseName: string | null
  productId: number | null
  skuCode: string | null
  productName: string | null
  quantityOnHand: number | null
  minimumQuantity: number | null
  detectedAt: string | null
}

export interface OrderCreatedPayload {
  orderId: number | null
  orderNumber: string | null
  orderType: OrderType | null
  status: string | null
  supplierId: number | null
  customerId: number | null
  warehouseId: number | null
  warehouseName: string | null
  totalAmount: number | null
  createdBy: string | null
  createdAt: string | null
  items: RealtimeOrderItem[] | null
}

export interface OrderDispatchedPayload {
  orderId: number | null
  orderNumber: string | null
  orderType: OrderType | null
  warehouseId: number | null
  dispatchedBy: string | null
  dispatchedAt: string | null
  items: RealtimeOrderItem[] | null
}

export interface InventoryUpdatedPayload {
  warehouseId: number | null
  productId: number | null
  skuCode: string | null
  quantityBefore: number | null
  quantityAfter: number | null
  reason: string | null
  referenceNumber: string | null
  updatedAt: string | null
}

/** Số liệu KPI trong ngày: payload của KPI_UPDATED, cũng là `data` của GET /api/notifications/realtime/kpi. */
export interface KpiSnapshot {
  date: string | null
  purchaseOrders: number | null
  salesOrders: number | null
  dispatchedOrders: number | null
  lowStockAlerts: number | null
  inventoryMovements: number | null
  updatedAt: string | null
}

/** Ánh xạ loại tin sang kiểu payload tương ứng. */
export interface RealtimePayloadMap {
  LOW_STOCK: LowStockPayload
  ORDER_CREATED: OrderCreatedPayload
  ORDER_DISPATCHED: OrderDispatchedPayload
  INVENTORY_UPDATED: InventoryUpdatedPayload
  KPI_UPDATED: KpiSnapshot
}

export type RealtimeMessageType = keyof RealtimePayloadMap

interface RealtimeEnvelope<T extends RealtimeMessageType> {
  id: string
  type: T
  severity: RealtimeSeverity
  /** Tiêu đề tiếng Việt do backend soạn sẵn, hiển thị thẳng */
  title: string
  /** Nội dung tiếng Việt do backend soạn sẵn, hiển thị thẳng */
  message: string
  /** Thời điểm xảy ra sự kiện, ISO-8601 UTC */
  occurredAt: string
  payload: RealtimePayloadMap[T]
}

/** Phong bì tin thời gian thực, phân biệt theo `type` (discriminated union). */
export type RealtimeMessage = {
  [T in RealtimeMessageType]: RealtimeEnvelope<T>
}[RealtimeMessageType]

/** Các loại tin được lưu vào danh sách thông báo (chuông) và hiện toast. */
export type NotifiableMessage = Extract<
  RealtimeMessage,
  { type: 'LOW_STOCK' | 'ORDER_CREATED' | 'ORDER_DISPATCHED' }
>

/** Một mục trong danh sách thông báo gần đây. */
export interface RealtimeNotification {
  id: string
  type: NotifiableMessage['type']
  severity: RealtimeSeverity
  title: string
  message: string
  occurredAt: string
  read: boolean
}
