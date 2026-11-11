/**
 * Các trạng thái trong vòng đời lệnh điều chuyển hàng hóa nội bộ giữa các kho:
 * - DRAFT: Bản nháp
 * - IN_TRANSIT: Đang vận chuyển
 * - COMPLETED: Đã nhận đủ
 * - PARTIAL_RECEIVED: Nhận một phần
 * - CANCELLED: Đã hủy
 */
export type StockTransferStatus =
  | 'DRAFT'
  | 'IN_TRANSIT'
  | 'COMPLETED'
  | 'PARTIAL_RECEIVED'
  | 'CANCELLED'

export interface StockTransferItem {
  id: number
  productId: number
  skuCode: string
  productName?: string
  uom?: string
  sourceBinLocationId?: number
  sourceBinLocationCode?: string
  destinationBinLocationId?: number
  destinationBinLocationCode?: string
  quantity: number
  transferredQuantity: number
  receivedQuantity: number
  damagedQuantity: number
  notes?: string
}

export interface StockTransfer {
  id: number
  transferNumber: string
  sourceWarehouseId: number
  sourceWarehouseCode: string
  sourceWarehouseName: string
  destinationWarehouseId: number
  destinationWarehouseCode: string
  destinationWarehouseName: string
  status: StockTransferStatus
  transferDate?: string
  expectedDeliveryDate?: string
  notes?: string
  approvedBy?: string
  approvedAt?: string
  receivedBy?: string
  receivedAt?: string
  totalItems: number
  totalQuantity: number
  totalTransferredQuantity: number
  totalReceivedQuantity: number
  createdAt: string
  updatedAt: string
  createdBy?: string
  items: StockTransferItem[]
}

export interface CreateStockTransferItemPayload {
  productId: number
  skuCode: string
  productName?: string
  uom?: string
  sourceBinLocationId?: number
  quantity: number
  notes?: string
}

export interface CreateStockTransferPayload {
  sourceWarehouseId: number
  destinationWarehouseId: number
  expectedDeliveryDate?: string
  notes?: string
  items: CreateStockTransferItemPayload[]
}

export interface UpdateStockTransferPayload {
  expectedDeliveryDate?: string
  notes?: string
  items: CreateStockTransferItemPayload[]
}

export interface ReceiveStockTransferItemPayload {
  itemId: number
  receivedQuantity: number
  damagedQuantity?: number
  destinationBinLocationId?: number
  notes?: string
}

export interface ReceiveStockTransferPayload {
  items: ReceiveStockTransferItemPayload[]
  notes?: string
}

export interface StockTransferFilterParams {
  status?: StockTransferStatus
  sourceWarehouseId?: number
  destinationWarehouseId?: number
  keyword?: string
  page?: number
  size?: number
  sort?: string
}

export interface StockAtpInfo {
  productId: number
  warehouseId?: number
  onHandQuantity: number
  reservedQuantity: number
  atpQuantity: number
}
