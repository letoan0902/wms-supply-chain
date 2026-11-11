import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  PurchaseBill,
  PurchaseBillSearchParams,
  PurchaseBillSummary,
  RecordBillPaymentPayload,
} from './purchaseBillTypes'

/**
 * REST API Client cho Hóa đơn mua hàng & Quản lý trạng thái thanh toán (FR11, FR13 - WMS-10.3, WMS-10.5).
 */

export async function fetchPurchaseBills(
  params?: PurchaseBillSearchParams
): Promise<PageResponse<PurchaseBill>> {
  const res = await api.get<ApiResponse<PageResponse<PurchaseBill>>>(
    '/orders/purchase-bills',
    { params }
  )
  return res.data.data
}

export async function fetchPurchaseBillSummary(): Promise<PurchaseBillSummary> {
  const res = await api.get<ApiResponse<PurchaseBillSummary>>(
    '/orders/purchase-bills/summary'
  )
  return res.data.data
}

export async function fetchPurchaseBillById(id: number): Promise<PurchaseBill> {
  const res = await api.get<ApiResponse<PurchaseBill>>(
    `/orders/purchase-bills/${id}`
  )
  return res.data.data
}

export async function fetchPurchaseBillByNumber(
  billNumber: string
): Promise<PurchaseBill> {
  const res = await api.get<ApiResponse<PurchaseBill>>(
    `/orders/purchase-bills/number/${encodeURIComponent(billNumber)}`
  )
  return res.data.data
}

export async function fetchPurchaseBillByGrnId(
  grnId: number
): Promise<PurchaseBill | null> {
  try {
    const res = await api.get<ApiResponse<PurchaseBill>>(
      `/orders/purchase-bills/grn/${grnId}`
    )
    return res.data.data
  } catch {
    return null
  }
}

export async function fetchPurchaseBillsByPoId(
  poId: number
): Promise<PurchaseBill[]> {
  const res = await api.get<ApiResponse<PurchaseBill[]>>(
    `/orders/purchase-bills/po/${poId}`
  )
  return res.data.data || []
}

export async function recordBillPayment(
  billId: number,
  payload: RecordBillPaymentPayload
): Promise<PurchaseBill> {
  const res = await api.post<ApiResponse<PurchaseBill>>(
    `/orders/purchase-bills/${billId}/payments`,
    payload
  )
  return res.data.data
}
