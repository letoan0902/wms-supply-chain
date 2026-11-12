import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  CountLinesQuery,
  CountPayload,
  CreateStocktakePayload,
  StockAdjustment,
  StocktakeCountLine,
  StocktakeListQuery,
  StocktakeSession,
  VarianceLine,
  VarianceQuery,
  VarianceReport,
} from './types'

/**
 * REST API kiểm kê của inventory-service qua api-gateway (`/api/inventory/stocktakes`, docs/api/KIEM_KE.md).
 * Mọi thao tác ghi trả lại đợt kiểm kê sau khi đổi trạng thái để màn hình cập nhật ngay.
 */

const BASE = '/inventory/stocktakes'

/** Bỏ các tham số rỗng để không gửi `keyword=` hay `status=undefined` lên máy chủ. */
function cleanParams<T extends object>(params: T): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    result[key] = value
  })
  return result
}

export async function fetchStocktakes(
  query: StocktakeListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<StocktakeSession>> {
  const res = await api.get<ApiResponse<PageResponse<StocktakeSession>>>(BASE, {
    params: cleanParams(query),
    signal,
  })
  return res.data.data
}

export async function fetchStocktake(id: number, signal?: AbortSignal): Promise<StocktakeSession> {
  const res = await api.get<ApiResponse<StocktakeSession>>(`${BASE}/${id}`, { signal })
  return res.data.data
}

export async function createStocktake(payload: CreateStocktakePayload): Promise<StocktakeSession> {
  const res = await api.post<ApiResponse<StocktakeSession>>(BASE, payload)
  return res.data.data
}

/** DRAFT sang COUNTING: chốt vị trí, đóng băng, chụp tồn. */
export async function startStocktake(id: number): Promise<StocktakeSession> {
  const res = await api.post<ApiResponse<StocktakeSession>>(`${BASE}/${id}/start`)
  return res.data.data
}

/** Hủy đợt ở DRAFT, COUNTING hoặc COUNTED; lý do không bắt buộc. */
export async function cancelStocktake(id: number, reason?: string): Promise<StocktakeSession> {
  const res = await api.post<ApiResponse<StocktakeSession>>(`${BASE}/${id}/cancel`, {
    reason: reason?.trim() || null,
  })
  return res.data.data
}

/**
 * Kết thúc đếm (COUNTING sang COUNTED). Còn dòng chưa đếm mà chưa xác nhận thì máy chủ trả 409 kèm
 * `data.uncountedLines`; gọi lại với `confirmUncountedAsZero = true` sau khi người dùng đồng ý.
 */
export async function finishStocktake(id: number, confirmUncountedAsZero: boolean): Promise<StocktakeSession> {
  const res = await api.post<ApiResponse<StocktakeSession>>(`${BASE}/${id}/finish`, { confirmUncountedAsZero })
  return res.data.data
}

/** Ghi số đếm mù (ADD cộng dồn, SET ghi đè); phản hồi là dòng đếm, không có tồn hệ thống. */
export async function postCount(id: number, payload: CountPayload): Promise<StocktakeCountLine> {
  const res = await api.post<ApiResponse<StocktakeCountLine>>(`${BASE}/${id}/counts`, payload)
  return res.data.data
}

export async function fetchCountLines(
  id: number,
  query: CountLinesQuery,
  signal?: AbortSignal,
): Promise<PageResponse<StocktakeCountLine>> {
  const res = await api.get<ApiResponse<PageResponse<StocktakeCountLine>>>(`${BASE}/${id}/count-lines`, {
    params: cleanParams(query),
    signal,
  })
  return res.data.data
}

// ----------------------------- Chênh lệch, đếm lại, duyệt hai cấp -----------------------------

/** Báo cáo chênh lệch (chỉ ADMIN, ACCOUNTANT, WAREHOUSE_MANAGER của kho; chỉ sau khi kết thúc đếm). */
export async function fetchVariances(id: number, query: VarianceQuery, signal?: AbortSignal): Promise<VarianceReport> {
  const res = await api.get<ApiResponse<VarianceReport>>(`${BASE}/${id}/variances`, {
    params: cleanParams(query),
    signal,
  })
  return res.data.data
}

/** Yêu cầu đếm lại các dòng chọn: xóa số đếm, đợt quay lại COUNTING. */
export async function requestRecount(id: number, lineIds: number[]): Promise<StocktakeSession> {
  const res = await api.post<ApiResponse<StocktakeSession>>(`${BASE}/${id}/recount`, { lineIds })
  return res.data.data
}

/** Ghi lý do chênh lệch cho một dòng (ở COUNTED); chuỗi rỗng xóa lý do. */
export async function saveLineReason(id: number, lineId: number, reason: string): Promise<VarianceLine> {
  const res = await api.put<ApiResponse<VarianceLine>>(`${BASE}/${id}/lines/${lineId}/reason`, {
    reason: reason.trim() || null,
  })
  return res.data.data
}

/** Quản lý kho duyệt: không lệch thì COMPLETED, có lệch thì sinh phiếu điều chỉnh (PENDING_ACCOUNTING). */
export async function approveByManager(id: number, notes?: string): Promise<StocktakeSession> {
  const res = await api.post<ApiResponse<StocktakeSession>>(`${BASE}/${id}/approve`, { notes: notes?.trim() || null })
  return res.data.data
}

/** Kế toán duyệt phiếu điều chỉnh: cập nhật tồn, mở đóng băng, đợt COMPLETED. Phải khác người duyệt cấp quản lý. */
export async function approveAdjustment(id: number): Promise<StocktakeSession> {
  const res = await api.post<ApiResponse<StocktakeSession>>(`${BASE}/${id}/adjustment/approve`)
  return res.data.data
}

/** Từ chối kết quả kiểm kê hoặc phiếu điều chỉnh (bắt buộc lý do): mở đóng băng, tồn giữ nguyên. */
export async function rejectStocktake(id: number, reason: string): Promise<StocktakeSession> {
  const res = await api.post<ApiResponse<StocktakeSession>>(`${BASE}/${id}/reject`, { reason: reason.trim() })
  return res.data.data
}

/** Chi tiết phiếu điều chỉnh kèm các dòng (GET /api/inventory/stock-adjustments/{id}). */
export async function fetchAdjustment(adjustmentId: number, signal?: AbortSignal): Promise<StockAdjustment> {
  const res = await api.get<ApiResponse<StockAdjustment>>(`/inventory/stock-adjustments/${adjustmentId}`, { signal })
  return res.data.data
}