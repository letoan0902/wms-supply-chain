/**
 * REST API Client cho Phiếu xuất kho (Goods Delivery Note - GDN) (WMS-12.1, WMS-12.4 - FR17).
 */

import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  CreateGdnRequest,
  GdnResponse,
  GdnSearchParams,
  GdnSummaryResponse,
} from './gdnTypes'

/**
 * Lập mới Phiếu xuất kho GDN từ Đơn bán hàng SO (WMS-12.1, WMS-12.4).
 */
export async function createGoodsDelivery(payload: CreateGdnRequest): Promise<GdnResponse> {
  const res = await api.post<ApiResponse<GdnResponse>>('/orders/goods-delivery-notes', payload)
  return res.data.data
}

/**
 * Lấy danh sách phiếu xuất kho GDN có phân trang & lọc đa tiêu chí.
 */
export async function fetchGoodsDeliveries(
  params: GdnSearchParams = {}
): Promise<PageResponse<GdnResponse>> {
  const res = await api.get<ApiResponse<PageResponse<GdnResponse>>>('/orders/goods-delivery-notes', {
    params: {
      keyword: params.keyword,
      status: params.status,
      customerId: params.customerId,
      warehouseId: params.warehouseId,
      soId: params.soId,
      fromDeliveryDate: params.fromDeliveryDate,
      toDeliveryDate: params.toDeliveryDate,
      page: params.page ?? 0,
      size: params.size ?? 10,
    },
  })
  return res.data.data
}

/**
 * Báo cáo tóm tắt tổng quan phiếu xuất kho GDN (GDN Metrics Summary).
 */
export async function fetchGoodsDeliverySummary(): Promise<GdnSummaryResponse> {
  const res = await api.get<ApiResponse<GdnSummaryResponse>>('/orders/goods-delivery-notes/summary')
  return res.data.data
}

/**
 * Lấy chi tiết phiếu xuất kho theo ID.
 */
export async function fetchGoodsDeliveryById(id: number): Promise<GdnResponse> {
  const res = await api.get<ApiResponse<GdnResponse>>(`/orders/goods-delivery-notes/${id}`)
  return res.data.data
}

/**
 * Lấy chi tiết phiếu xuất kho theo Mã GDN.
 */
export async function fetchGoodsDeliveryByNumber(gdnNumber: string): Promise<GdnResponse> {
  const res = await api.get<ApiResponse<GdnResponse>>(`/orders/goods-delivery-notes/number/${gdnNumber}`)
  return res.data.data
}

/**
 * Lấy lịch sử các phiếu xuất kho đã lập theo một đơn bán SO.
 */
export async function fetchGoodsDeliveriesBySoId(soId: number): Promise<GdnResponse[]> {
  const res = await api.get<ApiResponse<GdnResponse[]>>(`/orders/goods-delivery-notes/so/${soId}`)
  return res.data.data
}

/**
 * Xác nhận xuất xưởng (Dispatch) phiếu GDN.
 */
export async function dispatchGoodsDelivery(id: number): Promise<GdnResponse> {
  const res = await api.post<ApiResponse<GdnResponse>>(`/orders/goods-delivery-notes/${id}/dispatch`)
  return res.data.data
}
