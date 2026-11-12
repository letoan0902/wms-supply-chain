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

/** Số liệu mua, bán của một kỳ hoặc của cả khoảng (`TrendFigures`). Số tiền null khi vai trò không được xem. */
export interface PurchaseSalesFigures {
  purchaseOrderCount: number
  purchaseQuantity: number
  purchaseAmount: number | null
  salesOrderCount: number
  salesQuantity: number
  salesAmount: number | null
}

/** `data` của GET /api/orders/reports/purchase-sales-trend (`PurchaseSalesTrendResponse`). */
export interface PurchaseSalesTrend {
  dateRange: string
  fromDate: string
  toDate: string
  /** Cách gom kỳ: theo ngày hoặc theo tháng */
  bucket: 'DAY' | 'MONTH'
  amountVisible: boolean
  currency: string
  purchaseDefinition: string
  salesDefinition: string
  quantityNote: string
  /** Lý do số tiền để trống; null khi được xem số tiền */
  amountNote: string | null
  points: {
    periodStart: string
    periodEnd: string
    label: string
    figures: PurchaseSalesFigures
  }[]
  totals: PurchaseSalesFigures
  meta: {
    companyId: number
    warehouseId: number | null
    warehouseIds: number[] | null
    calculatedAt: string
  }
}

/** Một lát cắt của biểu đồ tỷ trọng tồn kho theo nhóm hàng. */
export interface StockCategorySlice {
  key: string
  kind: 'CATEGORY' | 'UNCATEGORIZED' | 'OTHERS'
  categoryId: number | null
  name: string
  quantity: number
  /** Luôn null khi tồn kho chưa có giá vốn */
  value: number | null
  percentage: number
  productCount: number
}

/** `data` của GET /api/inventory/dashboard/stock-by-category (`StockByCategoryResponse`). */
export interface StockByCategory {
  dateRange: string
  asOf: string
  /** Cơ sở tính tỷ trọng; hiện luôn QUANTITY vì tồn kho chưa có giá vốn */
  basis: 'QUANTITY' | 'VALUE'
  valuationMethod: string
  valueVisible: boolean
  basisNote: string
  unit: string
  totalQuantity: number
  totalValue: number | null
  productCount: number
  categoryCount: number
  slices: StockCategorySlice[]
  warehouseId: number | null
  warehouseIds: number[] | null
  dataNote: string | null
}

/** Một chứng từ kho trong bảng giao dịch gần nhất. */
export interface RecentStockTransaction {
  documentType: string
  documentId: number | null
  documentNumber: string | null
  movementType: string
  movementTypeLabel: string
  direction: 'IN' | 'OUT'
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  counterpartWarehouseId: number | null
  counterpartWarehouseName: string | null
  quantity: number
  productCount: number
  lineCount: number
  occurredAt: string
  performedBy: string | null
  /** Phiên kiểm kê của phiếu điều chỉnh, để mở màn hình kiểm kê */
  stocktakeId: number | null
}

/** `data` của GET /api/inventory/dashboard/recent-transactions (`RecentTransactionsResponse`). */
export interface RecentStockTransactions {
  dateRange: string
  fromTime: string | null
  limit: number
  rows: RecentStockTransaction[]
  warehouseId: number | null
  warehouseIds: number[] | null
  generatedAt: string
}
