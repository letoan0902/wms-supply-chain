import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  CreatePurchaseOrderPayload,
  PurchaseOrder,
  PurchaseOrderFilterParams,
  PurchaseOrderSummary,
  RejectPurchaseOrderPayload,
  UpdatePurchaseOrderPayload,
} from './types'

/**
 * REST API client cho Phân hệ Quản lý Đơn Mua Hàng (Purchase Orders - PO - FR10, WMS-09).
 */

export async function fetchPurchaseOrders(
  params?: PurchaseOrderFilterParams
): Promise<PageResponse<PurchaseOrder>> {
  const res = await api.get<ApiResponse<PageResponse<PurchaseOrder>>>('/orders/purchase', {
    params,
  })
  return res.data.data
}

export async function fetchPurchaseOrderSummary(): Promise<PurchaseOrderSummary> {
  const res = await api.get<ApiResponse<PurchaseOrderSummary>>('/orders/purchase/summary')
  return res.data.data
}

export async function fetchPurchaseOrderById(id: number): Promise<PurchaseOrder> {
  const res = await api.get<ApiResponse<PurchaseOrder>>(`/orders/purchase/${id}`)
  return res.data.data
}

export async function fetchPurchaseOrderByNumber(poNumber: string): Promise<PurchaseOrder> {
  const res = await api.get<ApiResponse<PurchaseOrder>>(`/orders/purchase/number/${poNumber}`)
  return res.data.data
}

export async function createPurchaseOrder(
  payload: CreatePurchaseOrderPayload
): Promise<PurchaseOrder> {
  const res = await api.post<ApiResponse<PurchaseOrder>>('/orders/purchase', payload)
  return res.data.data
}

export async function updatePurchaseOrder(
  id: number,
  payload: UpdatePurchaseOrderPayload
): Promise<PurchaseOrder> {
  const res = await api.put<ApiResponse<PurchaseOrder>>(`/orders/purchase/${id}`, payload)
  return res.data.data
}

export async function deletePurchaseOrder(id: number): Promise<void> {
  await api.delete(`/orders/purchase/${id}`)
}

export async function approvePurchaseOrder(id: number): Promise<PurchaseOrder> {
  const res = await api.post<ApiResponse<PurchaseOrder>>(`/orders/purchase/${id}/approve`)
  return res.data.data
}

export async function rejectPurchaseOrder(
  id: number,
  payload: RejectPurchaseOrderPayload
): Promise<PurchaseOrder> {
  const res = await api.post<ApiResponse<PurchaseOrder>>(`/orders/purchase/${id}/reject`, payload)
  return res.data.data
}

export async function cancelPurchaseOrder(id: number): Promise<PurchaseOrder> {
  const res = await api.post<ApiResponse<PurchaseOrder>>(`/orders/purchase/${id}/cancel`)
  return res.data.data
}
