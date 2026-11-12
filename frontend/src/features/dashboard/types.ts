/**
 * Định nghĩa kiểu dữ liệu báo cáo và thẻ KPI trên Executive Dashboard (WMS-16).
 * Khớp chuẩn xác 100% với DTO từ backend notification-service.
 */

export interface BaseKpiCardDto {
  title: string
  subText?: string
  visible: boolean
}

export interface FinancialKpiCardDto extends BaseKpiCardDto {
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
  growthRatePercentage: number
  isPositiveGrowth: boolean
}

export interface AvailableStockCardDto extends BaseKpiCardDto {
  totalQuantity: number
  uom: string
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
  occupancyRatePercentage: number
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
