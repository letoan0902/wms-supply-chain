import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type {
  DashboardTimeRange,
  KpiSummaryResponse,
  PurchaseSalesTrend,
  RecentStockTransactions,
  StockByCategory,
  TrendingItemsReport,
} from './types'

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

/**
 * Mua hàng và bán hàng theo thời gian: GET /api/orders/reports/purchase-sales-trend. Gom theo ngày (hôm nay, 7 ngày,
 * tháng này) hoặc theo tháng (quý này); số tiền null khi vai trò không được xem.
 */
export async function fetchPurchaseSalesTrend(
  warehouseId: number | 'all' | undefined,
  dateRange: DashboardTimeRange,
): Promise<PurchaseSalesTrend> {
  const response = await api.get<ApiResponse<PurchaseSalesTrend>>('/orders/reports/purchase-sales-trend', {
    params: buildParams(warehouseId, dateRange),
  })
  return response.data.data
}

/** Tỷ trọng tồn kho hiện tại theo nhóm hàng: GET /api/inventory/dashboard/stock-by-category. */
export async function fetchStockByCategory(
  warehouseId: number | 'all' | undefined,
  dateRange: DashboardTimeRange,
): Promise<StockByCategory> {
  const response = await api.get<ApiResponse<StockByCategory>>('/inventory/dashboard/stock-by-category', {
    params: buildParams(warehouseId, dateRange),
  })
  return response.data.data
}

/** Các chứng từ kho gần nhất trong khoảng thời gian: GET /api/inventory/dashboard/recent-transactions. */
export async function fetchRecentTransactions(
  warehouseId: number | 'all' | undefined,
  dateRange: DashboardTimeRange,
  limit = 10,
): Promise<RecentStockTransactions> {
  const response = await api.get<ApiResponse<RecentStockTransactions>>('/inventory/dashboard/recent-transactions', {
    params: { ...buildParams(warehouseId, dateRange), limit },
  })
  return response.data.data
}
