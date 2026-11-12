/**
 * Định nghĩa kiểu dữ liệu báo cáo và thẻ KPI trên Executive Dashboard (WMS-16).
 * Khớp với các record trong vn.wms.notification.dto.dashboard (notification-service) và
 * vn.wms.order.dto.report (order-service).
 */

export interface BaseKpiCardDto {
  title: string
  /** Dòng giải thích dưới con số; khi số tiền hoặc tăng trưởng không tính được, đây là lời giải thích */
  subText?: string | null
  /** false: vai trò hiện tại không được xem thẻ này (backend đã che số liệu) */
  visible: boolean
}

export interface FinancialKpiCardDto extends BaseKpiCardDto {
  /** null khi chưa tính được (ví dụ giá trị tồn kho chưa có giá vốn) */
  amount: number | null
  currency: string
}

export interface ReceivablesCardDto extends FinancialKpiCardDto {
  overdueCustomersCount: number
}

export interface PayablesCardDto extends FinancialKpiCardDto {
  dueSoonSuppliersCount: number
}

export interface InventoryValuationCardDto extends FinancialKpiCardDto {
  valuationMethod: string
}

export interface MonthlyRevenueCardDto extends FinancialKpiCardDto {
  /** null khi tháng trước không có doanh số để so sánh */
  growthRatePercentage: number | null
}

export interface AvailableStockCardDto extends BaseKpiCardDto {
  totalQuantity: number | null
  unit: string
}

export interface PendingSalesOrdersCardDto extends BaseKpiCardDto {
  totalOrders: number
  urgentOrders: number
}

export interface PendingPurchaseOrdersCardDto extends BaseKpiCardDto {
  totalOrders: number
  arrivingTodayCount: number
}

export interface WarehouseUtilizationCardDto extends BaseKpiCardDto {
  occupancyRatePercentage: number | null
  usedLocations: number
  totalLocations: number
}

export interface KpiSummaryResponse {
  receivables: ReceivablesCardDto
  payables: PayablesCardDto
  inventoryValuation: InventoryValuationCardDto
  monthlyRevenue: MonthlyRevenueCardDto
  availableStock: AvailableStockCardDto
  pendingSalesOrders: PendingSalesOrdersCardDto
  pendingPurchaseOrders: PendingPurchaseOrdersCardDto
  warehouseUtilization: WarehouseUtilizationCardDto
  meta: {
    companyId: number
    warehouseId: number | null
    warehouseName: string | null
    dateRange: string
    calculatedAt: string
    fromCache: boolean
  }
}

export type KpiCardKey =
  | 'receivables'
  | 'payables'
  | 'inventoryValuation'
  | 'monthlyRevenue'
  | 'availableStock'
  | 'pendingSalesOrders'
  | 'pendingPurchaseOrders'
  | 'warehouseUtilization'

/** Mốc thời gian chọn trên dashboard. */
export type DashboardTimeRange = 'today' | '7d' | 'month' | 'quarter'

/** Một lát cắt của biểu đồ mặt hàng bán chạy (`TrendingItemSliceDto`). */
export interface TrendingItemSlice {
  /** null với nhóm "Mặt hàng khác" */
  productId: number | null
  sku: string
  name: string
  uom: string
  quantity: number
  percentage: number
  color: string
}

/** `data` của GET /api/orders/reports/trending-items (`TrendingItemsReportResponse`). */
export interface TrendingItemsReport {
  title: string
  totalVolume: number
  unit: string
  dateRange: string
  warehouseName: string
  slices: TrendingItemSlice[]
  meta: {
    companyId: number
    warehouseId: number | null
    calculatedAt: string
    fromCache: boolean
  }
}
