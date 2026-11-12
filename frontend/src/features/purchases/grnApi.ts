import { api } from '../../services/api'
import { IDEMPOTENCY_KEY_HEADER, isReplayedReceipt } from '../../services/stockDocuments'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  CreateGrnRequest,
  GrnResponse,
  GrnSearchParams,
  GrnSummaryResponse,
  PoReceiptProgressResponse,
} from './grnTypes'

export interface CreateGrnResult {
  grn: GrnResponse
  /** true: phiếu đã được lập từ lần bấm trước với cùng khóa, lần này máy chủ không tạo phiếu mới */
  replayed: boolean
}

/**
 * Gọi API lập Phiếu nhập kho (GRN) từ đơn mua hàng PO (FR11 - WMS-10.1, WMS-10.4).
 * `idempotencyKey` là khóa chống trùng của form (một khóa mỗi lần mở form): gửi lại cùng khóa thì máy chủ trả
 * 200 kèm phiếu đã lập thay vì 201 và phiếu mới (docs/api/CHUNG_TU_TON_KHO.md mục 1).
 */
export async function createGoodsReceipt(
  payload: CreateGrnRequest,
  idempotencyKey?: string
): Promise<CreateGrnResult> {
  const res = await api.post<ApiResponse<GrnResponse>>('/orders/goods-receipt-notes', payload, {
    headers: idempotencyKey ? { [IDEMPOTENCY_KEY_HEADER]: idempotencyKey } : undefined,
  })
  return {
    grn: res.data.data,
    replayed: isReplayedReceipt(res.status, res.headers as Record<string, unknown>),
  }
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
