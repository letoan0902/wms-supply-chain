import type { PageResponse } from '../../types/api'

/**
 * Kiểu dữ liệu của các API báo cáo kho (inventory-service, tiền tố /api/inventory/reports).
 * Khớp từng trường với các record trong vn.wms.inventory.dto.report và tài liệu docs/api/BAO_CAO_KHO.md.
 *
 * Quy ước: số lượng là số thập phân 3 chữ số; `null` nghĩa là không xác định được (không phải 0).
 * Các trường `...Value` (giá trị tiền) hiện luôn `null` vì tồn kho chưa có giá vốn.
 * Các trường `...At` là chuỗi ISO-8601 UTC; ngày (`fromDate`, `expDate`...) dạng YYYY-MM-DD.
 */

/** Số lượng hoặc tiền từ backend (BigDecimal); null là không xác định. */
export type Decimal = number | null

export type GroupBy = 'PRODUCT_WAREHOUSE' | 'PRODUCT'

export type InOutBalanceSortField =
  | 'productId'
  | 'warehouseId'
  | 'openingQuantity'
  | 'quantityIn'
  | 'quantityOut'
  | 'closingQuantity'

export type SortDirection = 'asc' | 'desc'

/** Tham số của GET /inventory/reports/in-out-balance (và /export). */
export interface InOutBalanceParams {
  fromDate: string
  toDate: string
  warehouseId?: number
  categoryId?: number
  productId?: number
  keyword?: string
  groupBy?: GroupBy
  sort?: InOutBalanceSortField
  direction?: SortDirection
  page?: number
  size?: number
}

/** Một dòng báo cáo Xuất - Nhập - Tồn (InOutBalanceRow). */
export interface InOutBalanceRow {
  productId: number
  skuCode: string | null
  productName: string | null
  uom: string | null
  warehouseId: number | null
  warehouseCode: string | null
  warehouseName: string | null
  openingQuantity: Decimal
  quantityIn: Decimal
  quantityOut: Decimal
  closingQuantity: Decimal
  openingValue: Decimal
  valueIn: Decimal
  valueOut: Decimal
  closingValue: Decimal
}

/** Dòng tổng cộng trên mọi dòng khớp bộ lọc (InOutBalanceTotals). */
export interface InOutBalanceTotals {
  openingQuantity: Decimal
  quantityIn: Decimal
  quantityOut: Decimal
  closingQuantity: Decimal
  openingValue: Decimal
  valueIn: Decimal
  valueOut: Decimal
  closingValue: Decimal
}

/** `data` của GET /inventory/reports/in-out-balance (InOutBalanceReportResponse). */
export interface InOutBalanceReport {
  fromDate: string
  toDate: string
  timezone: string
  warehouseId: number | null
  warehouseIds: number[] | null
  groupBy: GroupBy
  rows: PageResponse<InOutBalanceRow>
  totals: InOutBalanceTotals
  ledgerStartedAt: string | null
  quantitiesComplete: boolean
  dataNote: string | null
  valuationMethod: string | null
  valuationNote: string | null
  generatedAt: string
}

/** Tham số của GET /inventory/reports/stock-card (và /export). */
export interface StockCardParams {
  productId: number
  warehouseId: number
  fromDate: string
  toDate: string
  page?: number
  size?: number
}

/** Một dòng thẻ kho (StockCardLine). */
export interface StockCardLine {
  movementId: number
  occurredAt: string
  movementType: string
  movementTypeLabel: string | null
  documentType: string | null
  documentId: number | null
  documentNumber: string | null
  referenceType: string | null
  referenceId: number | null
  referenceNumber: string | null
  partnerId: number | null
  counterpartWarehouseId: number | null
  lotNo: string | null
  expDate: string | null
  binLocationId: number | null
  binLocationCode: string | null
  quantityIn: Decimal
  quantityOut: Decimal
  balanceAfter: Decimal
  performedBy: string | null
  reason: string | null
}

/** `data` của GET /inventory/reports/stock-card (StockCardResponse). */
export interface StockCardReport {
  productId: number
  skuCode: string | null
  productName: string | null
  uom: string | null
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  fromDate: string
  toDate: string
  timezone: string
  openingQuantity: Decimal
  totalIn: Decimal
  totalOut: Decimal
  closingQuantity: Decimal
  lines: PageResponse<StockCardLine>
  ledgerStartedAt: string | null
  dataNote: string | null
  generatedAt: string
}

/** Sản phẩm đã chọn trong bộ lọc báo cáo (lấy từ danh mục hàng hóa của product-service). */
export interface ProductOption {
  id: number
  sku: string
  name: string
  uomCode?: string
}
