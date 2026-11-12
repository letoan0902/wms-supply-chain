/**
 * Kiểu dữ liệu của màn hình Tồn kho, khớp đúng các DTO của inventory-service:
 * - `LotResponse` (GET /api/inventory/lots),
 * - `InOutBalanceReportResponse`, `InOutBalanceRow` (GET /api/inventory/reports/in-out-balance),
 * - `BatchAtpResponse`, `StockAtpResponse` (POST /api/inventory/stock/atp/batch),
 * - `StockAlertResponse`, `StockAlertSummaryResponse`, `StockAlertScanResponse` (/api/inventory/alerts).
 * Số lượng là BigDecimal ở backend, Jackson trả về dạng số JSON.
 */
import type { PageResponse } from '../../types/api'

/** Loại cảnh báo tồn kho (`StockAlertKind`). */
export type StockAlertKind = 'NEAR_EXPIRY' | 'EXPIRED' | 'DEAD_STOCK' | 'LOW_STOCK'

/** Mức độ cảnh báo (`StockAlertSeverity`). */
export type StockAlertSeverity = 'CRITICAL' | 'WARNING' | 'INFO'

/** Trạng thái hạn dùng mà GET /api/inventory/lots trả về trong `expiryStatus`. */
export type LotExpiryStatus = 'NO_EXPIRY' | 'EXPIRED' | 'EXPIRING_SOON' | 'GOOD'

/** Giá trị lọc `expiryStatus` mà GET /api/inventory/lots chấp nhận. */
export type LotExpiryFilter = 'EXPIRED' | 'EXPIRING_SOON' | 'GOOD'

/** Một dòng tồn theo lô và ô kệ (`LotResponse`). */
export interface LotRow {
  /** ID dòng tồn; là ID của bảng lô nếu kho dùng bảng lô riêng, nên không dùng để khớp cảnh báo */
  stockItemId: number
  warehouseId: number | null
  warehouseName: string | null
  warehouseCode: string | null
  binLocationId: number | null
  binLocationCode: string | null
  zone: string | null
  rack: string | null
  bin: string | null
  productId: number
  lotNo: string | null
  mfgDate: string | null
  expDate: string | null
  quantity: number | null
  reservedQuantity: number | null
  availableQuantity: number | null
  locked: boolean
  lockedReason: string | null
  daysUntilExpiry: number | null
  expiryStatus: LotExpiryStatus | null
}

/** Tham số của GET /api/inventory/lots. */
export interface LotQuery {
  warehouseId?: number
  productId?: number
  lotNo?: string
  locked?: boolean
  hasStock?: boolean
  expiryStatus?: LotExpiryFilter
  expiringDays?: number
  page: number
  size: number
  /** Dạng Spring `field,asc|desc`; chỉ dùng expDate, mfgDate, quantity (có ở cả hai bảng tồn) */
  sort?: string
}

/** Một dòng báo cáo Xuất - Nhập - Tồn (`InOutBalanceRow`), dùng tồn cuối kỳ của hôm nay làm tồn thực tế. */
export interface BalanceRow {
  productId: number
  skuCode: string | null
  productName: string | null
  uom: string | null
  warehouseId: number | null
  warehouseCode: string | null
  warehouseName: string | null
  openingQuantity: number | null
  quantityIn: number | null
  quantityOut: number | null
  closingQuantity: number | null
}

/** Phần dùng tới của `InOutBalanceReportResponse`. */
export interface BalanceReport {
  fromDate: string
  toDate: string
  warehouseId: number | null
  groupBy: string
  rows: PageResponse<BalanceRow>
  dataNote: string | null
  generatedAt: string | null
}

/** Trường sắp xếp mà báo cáo Xuất - Nhập - Tồn chấp nhận và màn hình dùng. */
export type BalanceSortField = 'productId' | 'warehouseId' | 'closingQuantity'

/** Tham số của GET /api/inventory/reports/in-out-balance cho bảng tồn theo hàng hóa. */
export interface BalanceQuery {
  warehouseId?: number
  categoryId?: number
  keyword?: string
  sort: BalanceSortField
  direction: 'asc' | 'desc'
  page: number
  size: number
}

/** Tồn khả dụng của một sản phẩm tại một kho (`StockAtpResponse`): chỉ tính lô không khóa, chưa hết hạn. */
export interface AtpItem {
  productId: number
  warehouseId: number | null
  onHandQuantity: number
  reservedQuantity: number
  atpQuantity: number
}

/** Một cảnh báo tồn kho (`StockAlertResponse`). */
export interface StockAlert {
  /** Khóa ổn định, ví dụ `NEAR_EXPIRY:15` hoặc `LOW_STOCK:2:100` */
  id: string
  kind: StockAlertKind
  severity: StockAlertSeverity
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  productId: number
  skuCode: string | null
  productName: string | null
  /** ID dòng `stock_items`, chỉ có với EXPIRED, NEAR_EXPIRY */
  stockItemId: number | null
  lotNo: string | null
  binLocationId: number | null
  binCode: string | null
  quantity: number | null
  availableQuantity: number | null
  expDate: string | null
  daysLeft: number | null
  lastMovementAt: string | null
  daysWithoutMovement: number | null
  safetyStock: number | null
  reorderPoint: number | null
  message: string
}

/** Tham số của GET /api/inventory/alerts (tham số `sort` bị backend bỏ qua). */
export interface AlertQuery {
  kind?: StockAlertKind
  severity?: StockAlertSeverity
  warehouseId?: number
  productId?: number
  keyword?: string
  page: number
  size: number
}

/** `data` của GET /api/inventory/alerts/summary. */
export interface StockAlertSummary {
  alertDate: string
  nearExpiryDays: number
  deadStockDays: number
  total: number
  countsByKind: Record<StockAlertKind, number>
  countsBySeverity: Record<StockAlertSeverity, number>
  lowStockAvailable: boolean
  note: string | null
  topItems: StockAlert[]
}

export type ScanStatus = 'PUBLISHED' | 'ALREADY_SENT' | 'NOTHING_FOUND' | 'UNAVAILABLE' | 'FAILED'

/** `data` của POST /api/inventory/alerts/scan. */
export interface StockAlertScanResult {
  alertDate: string
  triggeredBy: string
  results: {
    kind: StockAlertKind
    status: ScanStatus
    warehouseCount: number
    itemCount: number
    emailCount: number
    message: string
  }[]
}

/** Thông tin sản phẩm cần cho bảng tồn kho, lấy từ GET /api/products/{id}. */
export interface ProductBrief {
  id: number
  sku: string
  name: string
  uomCode: string | null
  uomName: string | null
  categoryName: string | null
  safetyStock: number | null
  reorderPoint: number | null
}
