/**
 * Kiểu dữ liệu cho phân hệ Hóa đơn mua hàng (Purchase Bill) & Công nợ NCC (FR11, FR13 - WMS-10.3, WMS-10.5).
 */

export type PurchaseBillStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED'

export interface PurchaseBillItem {
  id: number
  poItemId?: number
  productId: number
  skuCode: string
  productName: string
  uom: string
  acceptedQuantity: number
  unitPrice: number
  lineTotal: number
}

export interface PurchaseBill {
  id: number
  billNumber: string
  grnId?: number
  grnNumber?: string
  poId?: number
  poNumber?: string
  supplierId?: number
  supplierName?: string
  billDate: string
  dueDate?: string
  totalAmount: number
  paidAmount: number
  remainingAmount: number
  status: PurchaseBillStatus
  note?: string
  createdAt: string
  updatedAt?: string
  items?: PurchaseBillItem[]
}

export interface PurchaseBillSummary {
  totalBills: number
  unpaidBills: number
  partiallyPaidBills: number
  paidBills: number
  totalPayablesAmount: number
  totalPaidAmount: number
  totalRemainingAmount: number
}

export interface RecordBillPaymentPayload {
  amount: number
  paymentMethod?: string
  referenceCode?: string
  note?: string
  paymentDate?: string
}

export interface PurchaseBillSearchParams {
  keyword?: string
  status?: PurchaseBillStatus
  supplierId?: number
  poId?: number
  fromDate?: string
  toDate?: string
  page?: number
  size?: number
  sort?: string
}

/**
 * Cấu hình hiển thị trạng thái hóa đơn mua hàng
 */
export const BILL_STATUS_CONFIG: Record<
  PurchaseBillStatus,
  { label: string; color: string; badgeStatus: 'default' | 'success' | 'error' | 'warning' | 'processing' }
> = {
  UNPAID: {
    label: 'Chưa thanh toán',
    color: 'orange',
    badgeStatus: 'warning',
  },
  PARTIALLY_PAID: {
    label: 'Thanh toán 1 phần',
    color: 'blue',
    badgeStatus: 'processing',
  },
  PAID: {
    label: 'Đã thanh toán',
    color: 'green',
    badgeStatus: 'success',
  },
  CANCELLED: {
    label: 'Đã hủy',
    color: 'default',
    badgeStatus: 'default',
  },
}
