import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { KpiSummaryResponse } from './types'

/**
 * Gọi API Aggregator lấy dữ liệu 8 thẻ KPI điều hành thời gian thực (WMS-16.1).
 *
 * @param warehouseId ID kho cần lọc (hoặc 'all' nếu toàn hệ thống)
 * @param dateRange   Mốc thời gian: 'today', '7d', 'month', 'quarter'
 */
export async function fetchDashboardKpiSummary(
  warehouseId?: number | 'all',
  dateRange?: string
): Promise<KpiSummaryResponse> {
  const params: Record<string, string | number> = {}

  if (warehouseId && warehouseId !== 'all') {
    params.warehouseId = warehouseId
  }

  if (dateRange) {
    const rangeMap: Record<string, string> = {
      today: 'TODAY',
      '7d': '7_DAYS',
      month: 'THIS_MONTH',
      quarter: 'THIS_QUARTER',
    }
    params.dateRange = rangeMap[dateRange] || '7_DAYS'
  }

  const response = await api.get<ApiResponse<KpiSummaryResponse>>(
    '/notifications/dashboard/kpi-summary',
    { params }
  )

  return response.data.data
}
