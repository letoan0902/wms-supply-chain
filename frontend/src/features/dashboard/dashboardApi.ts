import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { DashboardTimeRange, KpiSummaryResponse, TrendingItemsReport } from './types'

/** Mốc thời gian của giao diện sang giá trị `dateRange` mà backend chấp nhận. */
const DATE_RANGE_PARAM: Record<DashboardTimeRange, string> = {
  today: 'TODAY',
  '7d': '7_DAYS',
  month: 'THIS_MONTH',
  quarter: 'THIS_QUARTER',
}

function buildParams(warehouseId: number | 'all' | undefined, dateRange: DashboardTimeRange) {
  const params: Record<string, string | number> = { dateRange: DATE_RANGE_PARAM[dateRange] }
  if (warehouseId !== undefined && warehouseId !== 'all') {
    params.warehouseId = warehouseId
  }
  return params
}

/**
 * Gọi API Aggregator lấy dữ liệu 8 thẻ KPI điều hành (WMS-16.1): GET /api/notifications/dashboard/kpi-summary.
 *
 * @param warehouseId ID kho cần lọc, hoặc 'all' (không lọc; vai trò theo kho được backend giới hạn)
 * @param dateRange   Mốc thời gian đang chọn
 */
export async function fetchDashboardKpiSummary(
  warehouseId: number | 'all' | undefined,
  dateRange: DashboardTimeRange,
): Promise<KpiSummaryResponse> {
  const response = await api.get<ApiResponse<KpiSummaryResponse>>('/notifications/dashboard/kpi-summary', {
    params: buildParams(warehouseId, dateRange),
  })
  return response.data.data
}

/**
 * Mặt hàng xuất bán chạy nhất trong kỳ (WMS-16.2): GET /api/orders/reports/trending-items.
 * Chỉ có số lượng; phần ngoài `limit` mặt hàng đầu được gom vào nhóm "Mặt hàng khác".
 */
export async function fetchTrendingItems(
  warehouseId: number | 'all' | undefined,
  dateRange: DashboardTimeRange,
  limit = 5,
): Promise<TrendingItemsReport> {
  const response = await api.get<ApiResponse<TrendingItemsReport>>('/orders/reports/trending-items', {
    params: { ...buildParams(warehouseId, dateRange), limit },
  })
  return response.data.data
}
