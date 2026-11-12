/**
 * Kiểu dữ liệu của phân hệ kiểm kê, khớp DTO của inventory-service (hợp đồng ở docs/api/KIEM_KE.md).
 * Số lượng là số thập phân tối đa 3 chữ số sau dấu phẩy; thời gian ISO 8601 UTC, ngày dạng `yyyy-MM-dd`.
 */

/** Trạng thái đợt kiểm kê (vn.wms.inventory.entity.StocktakeStatus). */
export type StocktakeStatus =
  | 'DRAFT'
  | 'COUNTING'
  | 'COUNTED'
  | 'PENDING_ACCOUNTING'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'

/** Phạm vi đợt kiểm kê (StocktakeScopeType). */
export type StocktakeScopeType = 'WAREHOUSE' | 'ZONES' | 'LOCATIONS'

/** Trạng thái phiếu điều chỉnh (StockAdjustmentStatus). */
export type StockAdjustmentStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'

/** Vị trí trong phạm vi đợt (StocktakeLocationResponse). */
export interface StocktakeLocation {
  binLocationId: number
  binLocationCode: string
  zone: string | null
  rack: string | null
  bin: string | null
  /** Vị trí đang bị đợt này đóng băng */
  frozen: boolean
}

/** Đợt kiểm kê (StocktakeSessionResponse). `locations` chỉ có ở API chi tiết, null ở danh sách. */
export interface StocktakeSession {
  id: number
  code: string
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  status: StocktakeStatus
  scopeType: StocktakeScopeType
  zones: string[] | null
  productIds: number[] | null
  plannedDate: string | null
  notes: string | null
  totalLocations: number
  totalLines: number
  countedLines: number
  uncountedLines: number
  startedBy: string | null
  startedAt: string | null
  finishedBy: string | null
  finishedAt: string | null
  managerApprovedBy: string | null
  managerApprovedAt: string | null
  approvedBy: string | null
  approvedAt: string | null
  closedBy: string | null
  closedAt: string | null
  closeReason: string | null
  adjustmentId: number | null
  adjustmentCode: string | null
  adjustmentStatus: StockAdjustmentStatus | null
  createdBy: string | null
  createdAt: string | null
  updatedAt: string | null
  locations: StocktakeLocation[] | null
}

/** Bộ lọc danh sách đợt (GET /api/inventory/stocktakes). Trang bắt đầu từ 0, `size` tối đa 100. */
export interface StocktakeListQuery {
  warehouseId?: number
  status?: StocktakeStatus
  fromDate?: string
  toDate?: string
  keyword?: string
  page: number
  size: number
}

/** Thân POST /api/inventory/stocktakes (CreateStocktakeRequest). */
export interface CreateStocktakePayload {
  warehouseId: number
  scopeType: StocktakeScopeType
  /** Bắt buộc khi ZONES */
  zones?: string[]
  /** Bắt buộc khi LOCATIONS */
  binLocationIds?: number[]
  /** Không bắt buộc: giới hạn sản phẩm */
  productIds?: number[]
  plannedDate: string
  notes?: string
}

/** Dòng đếm cho màn hình đếm mù (StocktakeCountLineResponse): KHÔNG có tồn hệ thống. */
export interface StocktakeCountLine {
  lineId: number
  binLocationId: number
  binLocationCode: string
  zone: string | null
  productId: number
  lotNo: string | null
  counted: boolean
  countedQuantity: number | null
  countTimes: number
  lastCountedBy: string | null
  lastCountedAt: string | null
  unexpected: boolean
  recountRequested: boolean
}

/** Bộ lọc GET /api/inventory/stocktakes/{id}/count-lines. `binCode` lọc gần đúng (chứa chuỗi). */
export interface CountLinesQuery {
  counted?: boolean
  binCode?: string
  productId?: number
  page: number
  size: number
}

export type CountMode = 'ADD' | 'SET'

/**
 * Thân POST /api/inventory/stocktakes/{id}/counts (StocktakeCountRequest). Xác định dòng bằng `lineId`, hoặc bằng
 * vị trí (`binLocationId` hoặc `binLocationCode`) cùng `productId` và `lotNo`; không có dòng khớp thì máy chủ tạo
 * dòng hàng phát sinh.
 */
export interface CountPayload {
  lineId?: number
  binLocationId?: number
  binLocationCode?: string
  productId?: number
  lotNo?: string
  quantity: number
  mode: CountMode
}
