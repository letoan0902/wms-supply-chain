import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  CreateSupplierPayload,
  RecordSupplierTransactionPayload,
  Supplier,
  SupplierDebtSummary,
  SupplierTransaction,
  UpdateSupplierPayload,
} from './types'

/**
 * REST API client cho Phân hệ Quản lý Nhà cung cấp & Công nợ (WMS-08.3 - FR09).
 */

export async function fetchSuppliers(params?: {
  keyword?: string
  active?: boolean
  page?: number
  size?: number
  sort?: string
}): Promise<PageResponse<Supplier>> {
  const res = await api.get<ApiResponse<PageResponse<Supplier>>>('/orders/suppliers', { params })
  return res.data.data
}

export async function fetchSupplierDebtSummary(): Promise<SupplierDebtSummary> {
  const res = await api.get<ApiResponse<SupplierDebtSummary>>('/orders/suppliers/summary')
  return res.data.data
}

export async function fetchSupplierById(id: number): Promise<Supplier> {
  const res = await api.get<ApiResponse<Supplier>>(`/orders/suppliers/${id}`)
  return res.data.data
}

export async function fetchSupplierByCode(code: string): Promise<Supplier> {
  const res = await api.get<ApiResponse<Supplier>>(`/orders/suppliers/code/${code}`)
  return res.data.data
}

export async function createSupplier(payload: CreateSupplierPayload): Promise<Supplier> {
  const res = await api.post<ApiResponse<Supplier>>('/orders/suppliers', payload)
  return res.data.data
}

export async function updateSupplier(id: number, payload: UpdateSupplierPayload): Promise<Supplier> {
  const res = await api.put<ApiResponse<Supplier>>(`/orders/suppliers/${id}`, payload)
  return res.data.data
}

export async function deleteSupplier(id: number): Promise<void> {
  await api.delete(`/orders/suppliers/${id}`)
}

export async function fetchSupplierTransactions(
  supplierId: number,
  params?: { page?: number; size?: number }
): Promise<PageResponse<SupplierTransaction>> {
  const res = await api.get<ApiResponse<PageResponse<SupplierTransaction>>>(
    `/orders/suppliers/${supplierId}/transactions`,
    { params }
  )
  return res.data.data
}

export async function recordSupplierTransaction(
  supplierId: number,
  payload: RecordSupplierTransactionPayload
): Promise<SupplierTransaction> {
  const res = await api.post<ApiResponse<SupplierTransaction>>(
    `/orders/suppliers/${supplierId}/transactions`,
    payload
  )
  return res.data.data
}
