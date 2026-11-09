/**
 * Kiểu dữ liệu Phiếu nhập kho (Goods Receipt Note - GRN) & Tiến độ nhập kho (FR11, FR12 - WMS-10).
 */

import type { OrderStatus } from './types'

export type GoodsReceiptStatus = 'DRAFT' | 'COMPLETED' | 'CANCELLED'

export interface CreateGrnItemRequest {
  poItemId: number
  acceptedQuantity: number
  rejectedQuantity?: number
  rejectedReason?: string
  batchNumber?: string
  mfgDate?: string
  expDate?: string
  locationId?: number
  locationCode?: string
  note?: string
}

export interface CreateGrnRequest {
  poId: number
  receiptDate?: string
  note?: string
  items: CreateGrnItemRequest[]
}

export interface GrnItemResponse {
  id: number
  poItemId: number
  productId: number
  skuCode: string
  productName: string
  uom: string
  orderedQuantity: number
  acceptedQuantity: number
  rejectedQuantity: number
  rejectedReason?: string
  batchNumber?: string
  mfgDate?: string
  expDate?: string
  locationId?: number
  locationCode?: string
  note?: string
}

export interface GrnResponse {
  id: number
  grnNumber: string
  poId: number
  poNumber: string
  warehouseId: number
  warehouseName?: string
  supplierId: number
  supplierName?: string
  receiptDate: string
  receivedBy?: string
  status: GoodsReceiptStatus
  totalAcceptedQuantity: number
  totalRejectedQuantity: number
  note?: string
  items: GrnItemResponse[]
  createdAt: string
  updatedAt?: string
}

export interface GrnSummaryResponse {
  totalGrn: number
  completedGrn: number
  draftGrn: number
  cancelledGrn: number
  totalAcceptedQuantity: number
  totalRejectedQuantity: number
}

export interface GrnSearchParams {
  keyword?: string
  status?: GoodsReceiptStatus
  warehouseId?: number
  supplierId?: number
  poId?: number
  fromReceiptDate?: string
  toReceiptDate?: string
  page?: number
  size?: number
}

export interface PoItemReceiptProgress {
  poItemId: number
  productId: number
  skuCode: string
  productName: string
  uom: string
  orderedQuantity: number
  receivedQuantity: number
  remainingQuantity: number
  unitPrice: number
  isCompleted: boolean
  barcode?: string
}

export interface PoReceiptProgressResponse {
  poId: number
  poNumber: string
  supplierId: number
  supplierName: string
  warehouseId: number
  warehouseName: string
  status: OrderStatus
  orderDate: string
  expectedDate?: string
  totalOrderedQuantity: number
  totalReceivedQuantity: number
  totalRemainingQuantity: number
  isFullyReceived: boolean
  items: PoItemReceiptProgress[]
}

/**
 * Cấu hình trạng thái hiển thị cho Phiếu nhập kho (GRN)
 */
export const GRN_STATUS_CONFIG: Record<
  GoodsReceiptStatus,
  { label: string; color: string; badgeStatus: 'default' | 'success' | 'error' | 'warning' | 'processing' }
> = {
  COMPLETED: {
    label: 'Đã hoàn tất',
    color: 'green',
    badgeStatus: 'success',
  },
  DRAFT: {
    label: 'Bản nháp',
    color: 'orange',
    badgeStatus: 'warning',
  },
  CANCELLED: {
    label: 'Đã hủy',
    color: 'red',
    badgeStatus: 'error',
  },
}
