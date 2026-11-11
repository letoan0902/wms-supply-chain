/**
 * REST API Client cho phân hệ Bảng xuất kho thời gian thực & Lộ trình lấy hàng (WMS-12.3 - FR17).
 */

import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { ProductLotInfo } from './pickingOptimizer'
import type { DeductStockPayload } from './pickingTypes'

interface LotApiResponseItem {
  id: number
  productId: number
  lotNo?: string
  mfgDate?: string
  expDate?: string
  binLocationId?: number
  binLocationCode?: string
  zone?: string
  rack?: string
  bin?: string
  availableQuantity?: number
}

/**
 * Tải danh sách lô hàng có tồn kho tại một kho cụ thể để tối ưu vị trí lấy hàng theo FEFO.
 */
export async function fetchLotsForWarehouse(
  warehouseId: number,
  productId?: number
): Promise<ProductLotInfo[]> {
  try {
    const res = await api.get<ApiResponse<PageResponse<LotApiResponseItem>>>('/inventory/lots', {
      params: {
        warehouseId,
        productId,
        hasStock: true,
        size: 100,
      },
    })
    const list = res.data.data?.content || []
    return list.map((it) => ({
      productId: it.productId,
      lotNo: it.lotNo,
      mfgDate: it.mfgDate,
      expDate: it.expDate,
      binLocationId: it.binLocationId,
      binLocationCode: it.binLocationCode,
      zone: it.zone,
      rack: it.rack,
      bin: it.bin,
      availableQuantity: it.availableQuantity,
    }))
  } catch {
    return []
  }
}

/**
 * Xác nhận trừ tồn kho xuất xưởng (Dispatch Stock with Pessimistic Lock - WMS-12.2).
 */
export async function dispatchOrderStock(payload: DeductStockPayload): Promise<void> {
  try {
    await api.post<ApiResponse<unknown>>('/inventory/stock/dispatch', payload)
  } catch {
    // Thử fallback sang /inventory/stock/deduct nếu endpoint /dispatch có quy tắc khác
    await api.post<ApiResponse<unknown>>('/inventory/stock/deduct', payload)
  }
}
