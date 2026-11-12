/**
 * Logic thuần của phân hệ kiểm kê (không phụ thuộc React, axios): nhãn trạng thái, đọc số lượng nhập tay, đọc mã
 * quét, đọc chi tiết lỗi 409 và các thao tác được phép theo trạng thái và vai trò. Kiểm tra được bằng Node.
 */
import type {
  ReservedConflict,
  StockAdjustmentStatus,
  StocktakeLocation,
  StocktakeScopeType,
  StocktakeStatus,
} from './types'

// ------------------------------------------------------------------------------------------------
// Nhãn
// ------------------------------------------------------------------------------------------------

export const STATUS_LABELS: Record<StocktakeStatus, string> = {
  DRAFT: 'Mới lập',
  COUNTING: 'Đang đếm',
  COUNTED: 'Chờ Quản lý kho duyệt',
  PENDING_ACCOUNTING: 'Chờ Kế toán duyệt',
  COMPLETED: 'Hoàn tất',
  REJECTED: 'Bị từ chối',
  CANCELLED: 'Đã hủy',
}

/** Màu thẻ trạng thái theo quy ước màu của SRS: chờ duyệt vàng, hoàn tất xanh lá, từ chối đỏ. */
export const STATUS_COLORS: Record<StocktakeStatus, string> = {
  DRAFT: 'default',
  COUNTING: 'processing',
  COUNTED: 'warning',
  PENDING_ACCOUNTING: 'warning',
  COMPLETED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default',
}

export const SCOPE_LABELS: Record<StocktakeScopeType, string> = {
  WAREHOUSE: 'Toàn kho',
  ZONES: 'Theo khu',
  LOCATIONS: 'Theo vị trí',
}

export const ADJUSTMENT_STATUS_LABELS: Record<StockAdjustmentStatus, string> = {
  PENDING_APPROVAL: 'Chờ Kế toán duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
}

export const ADJUSTMENT_STATUS_COLORS: Record<StockAdjustmentStatus, string> = {
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
}

export const STATUS_ORDER: readonly StocktakeStatus[] = [
  'DRAFT',
  'COUNTING',
  'COUNTED',
  'PENDING_ACCOUNTING',
  'COMPLETED',
  'REJECTED',
  'CANCELLED',
]

/** Trạng thái mà vị trí trong phạm vi đang bị đóng băng. */
export function isFrozenStatus(status: StocktakeStatus): boolean {
  return status === 'COUNTING' || status === 'COUNTED' || status === 'PENDING_ACCOUNTING'
}

// ------------------------------------------------------------------------------------------------
// Số lượng
// ------------------------------------------------------------------------------------------------

/** Phần nguyên tối đa 12 chữ số: cùng 3 chữ số thập phân vẫn biểu diễn chính xác bằng số của JavaScript. */
const MAX_INTEGER_DIGITS = 12
const QUANTITY_PATTERN = /^(\d+)(?:[.,](\d*))?$/

export type QuantityParseResult = { ok: true; value: number } | { ok: false; error: string }

/**
 * Đọc số lượng người dùng gõ: chấp nhận dấu chấm hoặc dấu phẩy làm dấu thập phân, tối đa 3 chữ số sau dấu, không
 * âm, không có dấu phân cách hàng nghìn. `allowZero = false` cho thao tác cộng thêm (máy chủ yêu cầu lớn hơn 0).
 */
export function parseQuantityInput(raw: string, allowZero: boolean): QuantityParseResult {
  const text = raw.trim()
  if (!text) return { ok: false, error: 'Vui lòng nhập số lượng' }
  if (text.startsWith('-')) return { ok: false, error: 'Số lượng không được âm' }
  const match = QUANTITY_PATTERN.exec(text)
  if (!match) return { ok: false, error: 'Số lượng không hợp lệ (chỉ gồm chữ số và một dấu thập phân)' }
  const integerPart = match[1].replace(/^0+(?=\d)/, '')
  const fraction = match[2] ?? ''
  if (fraction.length > 3) return { ok: false, error: 'Số lượng tối đa 3 chữ số sau dấu thập phân' }
  if (integerPart.length > MAX_INTEGER_DIGITS) return { ok: false, error: 'Số lượng quá lớn' }
  const value = Number(fraction ? `${integerPart}.${fraction}` : integerPart)
  if (!Number.isFinite(value)) return { ok: false, error: 'Số lượng không hợp lệ' }
  if (!allowZero && value <= 0) return { ok: false, error: 'Số lượng phải lớn hơn 0' }
  return { ok: true, value }
}

/** Cộng hai số lượng có tối đa 3 chữ số thập phân mà không bị sai số dấu phẩy động (0.1 + 0.2). */
export function addQuantities(a: number, b: number): number {
  return Math.round(a * 1000 + b * 1000) / 1000
}

const quantityFormatter = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

/** Hiển thị số lượng kiểu Việt Nam, tối đa 3 chữ số thập phân; null hiện gạch ngang. */
export function formatQty(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return quantityFormatter.format(Number(value))
}

/** Hiển thị chênh lệch có dấu: +2, -0,005, 0. */
export function formatSignedQty(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  const n = Number(value)
  return n > 0 ? `+${quantityFormatter.format(n)}` : quantityFormatter.format(n)
}

// ------------------------------------------------------------------------------------------------
// Mã quét
// ------------------------------------------------------------------------------------------------

export interface ScannedCode {
  /** Mã để tra hàng hóa (mã vạch hoặc SKU) */
  code: string
  /** Số lô đọc được từ tem lô */
  lotNo: string | null
}

const LOT_LABEL_PATTERN = /^SKU:([^|]+)\|LOT:([^|]*)(?:\|.*)?$/i

/**
 * Đọc chuỗi máy quét gửi: tem lô do hệ thống in (`SKU:<sku>|LOT:<số lô>|EXP:<hạn dùng>`, product-service
 * BarcodeService) cho cả SKU và số lô; chuỗi khác là mã vạch hoặc SKU.
 */
export function parseScannedCode(raw: string): ScannedCode {
  const text = raw.trim()
  const match = LOT_LABEL_PATTERN.exec(text)
  if (match) {
    const lot = match[2].trim()
    return { code: match[1].trim(), lotNo: lot || null }
  }
  return { code: text, lotNo: null }
}

/** Vị trí của đợt có mã trùng (không phân biệt hoa thường) với chuỗi quét, hoặc null. */
export function findLocationByCode(
  locations: readonly StocktakeLocation[],
  code: string,
): StocktakeLocation | null {
  const wanted = code.trim().toUpperCase()
  if (!wanted) return null
  return locations.find((l) => l.binLocationCode.toUpperCase() === wanted) ?? null
}

export interface ProductLike {
  id: number
  sku: string
  barcode?: string | null
}

/**
 * Hàng hóa khớp đúng mã quét trong kết quả tìm kiếm (tìm kiếm của product-service là gần đúng): ưu tiên mã vạch
 * trùng khớp, sau đó tới SKU, không phân biệt hoa thường. Không khớp đúng thì null (không đoán).
 */
export function matchProductByCode<P extends ProductLike>(products: readonly P[], code: string): P | null {
  const wanted = code.trim().toLowerCase()
  if (!wanted) return null
  return (
    products.find((p) => (p.barcode ?? '').trim().toLowerCase() === wanted) ??
    products.find((p) => p.sku.trim().toLowerCase() === wanted) ??
    null
  )
}

/** Khóa của một dòng theo (vị trí, sản phẩm, số lô) giống máy chủ; số lô rỗng coi như không có lô. */
export function lineKeyOf(binLocationId: number, productId: number, lotNo: string | null | undefined): string {
  const lot = (lotNo ?? '').trim()
  return `${binLocationId}|${productId}|${lot}`
}

/** Phần trăm tiến độ đếm (0 nếu chưa có dòng nào). */
export function progressPercent(counted: number, total: number): number {
  if (!total || total <= 0) return 0
  return Math.min(100, Math.round((counted / total) * 1000) / 10)
}

// ------------------------------------------------------------------------------------------------
// Lỗi 409 kèm chi tiết
// ------------------------------------------------------------------------------------------------

interface ErrorLike {
  response?: { status?: number; data?: { code?: string; message?: string; data?: unknown } }
}

function errorDataOf(error: unknown): unknown {
  return typeof error === 'object' && error !== null ? (error as ErrorLike).response?.data?.data : undefined
}

function errorStatus(error: unknown): number | undefined {
  return typeof error === 'object' && error !== null ? (error as ErrorLike).response?.status : undefined
}

/**
 * Số dòng chưa đếm trong lỗi 409 của kết thúc đếm (`data.uncountedLines`), hoặc null nếu lỗi không phải loại này.
 */
export function uncountedLinesOf(error: unknown): number | null {
  if (errorStatus(error) !== 409) return null
  const data = errorDataOf(error)
  if (typeof data !== 'object' || data === null || Array.isArray(data)) return null
  const value = Number((data as Record<string, unknown>).uncountedLines)
  return Number.isFinite(value) && value > 0 ? value : null
}

// ------------------------------------------------------------------------------------------------
// Thao tác được phép
// ------------------------------------------------------------------------------------------------

/** Quyền của người dùng trên kho của đợt (tính bằng useCan từ bảng quyền dùng chung). */
export interface StocktakeAbilities {
  /** Lập, bắt đầu, kết thúc đếm, đếm lại, ghi lý do, hủy, duyệt cấp quản lý */
  manage: boolean
  /** Kiểm đếm */
  count: boolean
  /** Xem tồn hệ thống và chênh lệch */
  variance: boolean
  /** Duyệt phiếu điều chỉnh cấp kế toán */
  approveAdjustment: boolean
}

export type StocktakeAction =
  | 'start'
  | 'cancel'
  | 'count'
  | 'finish'
  | 'approve'
  | 'approveAdjustment'
  | 'reject'
  | 'recount'

/**
 * Các thao tác hiển thị cho đợt theo trạng thái và quyền (máy chủ vẫn kiểm tra lại). Từ chối ở COUNTED chỉ dành
 * cho người quản lý được kho; ở PENDING_ACCOUNTING thêm Kế toán (StocktakeApprovalService.reject).
 */
export function allowedActions(status: StocktakeStatus, can: StocktakeAbilities): StocktakeAction[] {
  const actions: StocktakeAction[] = []
  if (status === 'DRAFT' && can.manage) actions.push('start')
  if (status === 'COUNTING' && can.count) actions.push('count')
  if (status === 'COUNTING' && can.manage) actions.push('finish')
  if (status === 'COUNTED' && can.manage) actions.push('approve')
  if (status === 'PENDING_ACCOUNTING' && can.approveAdjustment) actions.push('approveAdjustment')
  if ((status === 'COUNTED' && can.manage) || (status === 'PENDING_ACCOUNTING' && (can.manage || can.approveAdjustment))) {
    actions.push('reject')
  }
  if ((status === 'DRAFT' || status === 'COUNTING' || status === 'COUNTED') && can.manage) actions.push('cancel')
  if ((status === 'COUNTING' || status === 'COUNTED') && can.manage) actions.push('recount')
  return actions
}

/** Kế toán duyệt phải khác người đã duyệt cấp Quản lý kho (máy chủ trả 403 nếu trùng). */
export function isSameApprover(managerApprovedBy: string | null, username: string | null | undefined): boolean {
  return Boolean(managerApprovedBy && username && managerApprovedBy === username)
}

/** Màu chữ của chênh lệch theo dấu: thừa xanh lá, thiếu đỏ, bằng 0 xám. */
export function varianceTone(value: number | null | undefined): 'increase' | 'decrease' | 'zero' | 'none' {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return 'none'
  const n = Number(value)
  return n > 0 ? 'increase' : n < 0 ? 'decrease' : 'zero'
}

/**
 * Danh sách dòng trong lỗi 409 khi duyệt mà tồn sau điều chỉnh nhỏ hơn số đang giữ chỗ hoặc âm (`data` là mảng
 * `lineId`, `binLocationCode`, `productId`, `lotNo`, `newQuantity`, `reservedQuantity`); rỗng nếu không phải loại này.
 */
export function reservedConflictsOf(error: unknown): ReservedConflict[] {
  if (errorStatus(error) !== 409) return []
  const data = errorDataOf(error)
  if (!Array.isArray(data)) return []
  return data
    .filter((r): r is Record<string, unknown> => typeof r === 'object' && r !== null)
    .filter((r) => r.newQuantity !== undefined && r.reservedQuantity !== undefined)
    .map((r) => ({
      lineId: r.lineId === null || r.lineId === undefined ? null : Number(r.lineId),
      binLocationCode: typeof r.binLocationCode === 'string' ? r.binLocationCode : '',
      productId: r.productId === null || r.productId === undefined ? null : Number(r.productId),
      lotNo: typeof r.lotNo === 'string' && r.lotNo ? r.lotNo : null,
      newQuantity: Number(r.newQuantity),
      reservedQuantity: Number(r.reservedQuantity),
    }))
}
