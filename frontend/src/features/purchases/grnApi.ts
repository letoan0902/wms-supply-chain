import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  CreateGrnRequest,
  GrnResponse,
  GrnSearchParams,
  GrnSummaryResponse,
  PoReceiptProgressResponse,
} from './grnTypes'

/**
 * Gọi API lập Phiếu nhập kho (GRN) từ đơn mua hàng PO (FR11 - WMS-10.1, WMS-10.4).
 */
export async function createGoodsReceipt(payload: CreateGrnRequest): Promise<GrnResponse> {
  const res = await api.post<ApiResponse<GrnResponse>>('/orders/goods-receipt-notes', payload)
  return res.data.data
}

/**
 * Lấy danh sách phiếu nhập kho có phân trang & tìm kiếm đa tiêu chí.
 */
export async function fetchGoodsReceipts(
  params: GrnSearchParams = {}
): Promise<PageResponse<GrnResponse>> {
  const res = await api.get<ApiResponse<PageResponse<GrnResponse>>>('/orders/goods-receipt-notes', {
    params: {
      keyword: params.keyword,
      status: params.status,
      warehouseId: params.warehouseId,
      supplierId: params.supplierId,
      poId: params.poId,
      fromReceiptDate: params.fromReceiptDate,
      toReceiptDate: params.toReceiptDate,
      page: params.page ?? 0,
      size: params.size ?? 10,
    },
  })
  return res.data.data
}

/**
 * Lấy báo cáo thống kê tình hình nhập kho (GRN Summary).
 */
export async function fetchGoodsReceiptSummary(): Promise<GrnSummaryResponse> {
  const res = await api.get<ApiResponse<GrnSummaryResponse>>('/orders/goods-receipt-notes/summary')
  return res.data.data
}

/**
 * Chi tiết phiếu nhập kho theo ID.
 */
export async function fetchGoodsReceiptById(id: number): Promise<GrnResponse> {
  const res = await api.get<ApiResponse<GrnResponse>>(`/orders/goods-receipt-notes/${id}`)
  return res.data.data
}

/**
 * Chi tiết phiếu nhập kho theo Mã GRN.
 */
export async function fetchGoodsReceiptByNumber(grnNumber: string): Promise<GrnResponse> {
  const res = await api.get<ApiResponse<GrnResponse>>(`/orders/goods-receipt-notes/number/${grnNumber}`)
  return res.data.data
}

/**
 * Lấy lịch sử các đợt nhập kho của một đơn mua PO.
 */
export async function fetchGoodsReceiptsByPoId(poId: number): Promise<GrnResponse[]> {
  const res = await api.get<ApiResponse<GrnResponse[]>>(`/orders/goods-receipt-notes/po/${poId}`)
  return res.data.data
}

/**
 * Lấy tiến độ nhập kho của đơn mua PO (đã nhập bao nhiêu, còn lại bao nhiêu từng SKU - FR11).
 */
export async function fetchPoReceiptProgress(poId: number): Promise<PoReceiptProgressResponse> {
  const res = await api.get<ApiResponse<PoReceiptProgressResponse>>(
    `/orders/goods-receipt-notes/po/${poId}/progress`
  )
  return res.data.data
}
