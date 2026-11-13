import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  CreateStockTransferPayload,
  ReceiveStockTransferPayload,
  StockAtpInfo,
  StockTransfer,
  StockTransferFilterParams,
  UpdateStockTransferPayload,
} from './types'

/**
 * REST API Client cho phân hệ Điều chuyển hàng hóa nội bộ giữa các kho.
 */

export async function fetchTransfers(
  params?: StockTransferFilterParams
): Promise<PageResponse<StockTransfer>> {
  const res = await api.get<ApiResponse<PageResponse<StockTransfer>>>('/inventory/transfers', {
    params,
  })
  return res.data.data
}

export async function fetchTransferById(id: number): Promise<StockTransfer> {
  const res = await api.get<ApiResponse<StockTransfer>>(`/inventory/transfers/${id}`)
  return res.data.data
}

export async function createTransfer(payload: CreateStockTransferPayload): Promise<StockTransfer> {
  const res = await api.post<ApiResponse<StockTransfer>>('/inventory/transfers', payload)
  return res.data.data
}

export async function updateTransfer(
  id: number,
  payload: UpdateStockTransferPayload
): Promise<StockTransfer> {
  const res = await api.put<ApiResponse<StockTransfer>>(`/inventory/transfers/${id}`, payload)
  return res.data.data
}

export async function cancelTransfer(id: number, reason?: string): Promise<StockTransfer> {
  const res = await api.post<ApiResponse<StockTransfer>>(`/inventory/transfers/${id}/cancel`, {
    reason,
  })
  return res.data.data
}

export async function dispatchTransfer(id: number): Promise<StockTransfer> {
  const res = await api.post<ApiResponse<StockTransfer>>(`/inventory/transfers/${id}/dispatch`)
  return res.data.data
}

export async function receiveTransfer(
  id: number,
  payload: ReceiveStockTransferPayload
): Promise<StockTransfer> {
  const res = await api.post<ApiResponse<StockTransfer>>(
    `/inventory/transfers/${id}/receive`,
    payload
  )
  return res.data.data
}

export async function checkStockAtp(
  productId: number,
  warehouseId?: number
): Promise<StockAtpInfo> {
  const res = await api.get<ApiResponse<StockAtpInfo>>('/inventory/stock/atp', {
    params: { productId, warehouseId },
  })
  return res.data.data
}
