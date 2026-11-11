/**
 * Hàm thuần của màn hình Tồn kho: nhãn và màu cảnh báo, ghép cảnh báo vào dòng tồn, định dạng số và ngày.
 * Không phụ thuộc React hay Ant Design để kiểm tra độc lập được.
 */
import type { StockAlert, StockAlertKind, StockAlertSeverity } from './types'

/** Múi giờ tính "hôm nay", cùng múi giờ backend dùng cho cảnh báo và báo cáo kho. */
export const VIETNAM_TIME_ZONE = 'Asia/Ho_Chi_Minh'

/** Thứ tự loại cảnh báo, giống thứ tự backend sắp xếp. */
export const KIND_ORDER: readonly StockAlertKind[] = ['EXPIRED', 'LOW_STOCK', 'NEAR_EXPIRY', 'DEAD_STOCK']

export const SEVERITY_ORDER: readonly StockAlertSeverity[] = ['CRITICAL', 'WARNING', 'INFO']

/** Tên loại cảnh báo dùng cho bộ lọc và thanh tóm tắt. */
export const KIND_LABELS: Record<StockAlertKind, string> = {
  EXPIRED: 'Hết hạn',
  NEAR_EXPIRY: 'Cận hạn',
  LOW_STOCK: 'Tồn thấp',
  DEAD_STOCK: 'Tồn lâu',
}

export const SEVERITY_LABELS: Record<StockAlertSeverity, string> = {
  CRITICAL: 'Khẩn cấp',
  WARNING: 'Cảnh báo',
  INFO: 'Thông tin',
}

/** Màu thẻ theo mức độ (màu có sẵn của Ant Design Tag): đỏ, vàng viền cam, xanh dương. */
export const SEVERITY_COLORS: Record<StockAlertSeverity, string> = {
  CRITICAL: 'red',
  WARNING: 'gold',
  INFO: 'blue',
}

/** Loại cảnh báo gắn với một dòng tồn theo lô; các loại còn lại gắn với cặp (kho, sản phẩm). */
export function isLotAlertKind(kind: StockAlertKind): boolean {
  return kind === 'EXPIRED' || kind === 'NEAR_EXPIRY'
}

/** Nhãn ngắn in trên thẻ màu. Tồn thấp có hai mức: dưới mức an toàn và dưới điểm đặt hàng lại. */
export function alertTagLabel(kind: StockAlertKind, severity: StockAlertSeverity): string {
  switch (kind) {
    case 'EXPIRED':
      return 'HẾT HẠN'
    case 'NEAR_EXPIRY':
      return 'CẬN DATE'
    case 'DEAD_STOCK':
      return 'TỒN LÂU'
    case 'LOW_STOCK':
      return severity === 'CRITICAL' ? 'CHÁY HÀNG' : 'SẮP HẾT'
  }
}

function severityRank(severity: StockAlertSeverity): number {
  const index = SEVERITY_ORDER.indexOf(severity)
  return index < 0 ? SEVERITY_ORDER.length : index
}

function kindRank(kind: StockAlertKind): number {
  const index = KIND_ORDER.indexOf(kind)
  return index < 0 ? KIND_ORDER.length : index
}

/** Mức nghiêm trọng hơn trong hai mức. */
export function maxSeverity(a: StockAlertSeverity, b: StockAlertSeverity): StockAlertSeverity {
  return severityRank(a) <= severityRank(b) ? a : b
}

// ===================== Ghép cảnh báo vào dòng tồn =====================

/** Khóa của cặp (kho, sản phẩm). */
export function productKey(warehouseId: number | null | undefined, productId: number | null | undefined): string {
  return `${warehouseId ?? ''}:${productId ?? ''}`
}

/**
 * Khóa của một dòng tồn theo lô: kho, sản phẩm, ô kệ, số lô.
 * Không khớp bằng `stockItemId` vì GET /api/inventory/lots có thể trả ID của bảng lô riêng (inventory_lots),
 * trong khi cảnh báo luôn mang ID của bảng stock_items; bộ bốn trường này có ở cả hai phía.
 */
export function lotKey(
  warehouseId: number | null | undefined,
  productId: number | null | undefined,
  binLocationId: number | null | undefined,
  lotNo: string | null | undefined,
): string {
  return `${warehouseId ?? ''}:${productId ?? ''}:${binLocationId ?? ''}:${(lotNo ?? '').trim().toUpperCase()}`
}

/** Chỉ mục cảnh báo để ghép vào dòng tồn mà không phải duyệt lại toàn bộ danh sách cho từng dòng. */
export interface AlertIndex {
  /** Cảnh báo theo lô, khóa {@link lotKey} */
  byLot: Map<string, StockAlert[]>
  /** Cảnh báo theo mặt hàng (tồn thấp, tồn lâu), khóa {@link productKey} */
  byProduct: Map<string, StockAlert[]>
  /** Cảnh báo theo lô gom theo mặt hàng, khóa {@link productKey}, để hiện trên dòng tồn theo hàng hóa */
  lotAlertsByProduct: Map<string, StockAlert[]>
}

function push(map: Map<string, StockAlert[]>, key: string, alert: StockAlert): void {
  const list = map.get(key)
  if (list) {
    list.push(alert)
  } else {
    map.set(key, [alert])
  }
}

/** Lập chỉ mục cho danh sách cảnh báo; cảnh báo trùng `id` chỉ tính một lần. */
export function indexAlerts(alerts: readonly StockAlert[]): AlertIndex {
  const index: AlertIndex = { byLot: new Map(), byProduct: new Map(), lotAlertsByProduct: new Map() }
  const seen = new Set<string>()
  for (const alert of alerts) {
    if (seen.has(alert.id)) continue
    seen.add(alert.id)
    const pKey = productKey(alert.warehouseId, alert.productId)
    if (isLotAlertKind(alert.kind)) {
      push(index.byLot, lotKey(alert.warehouseId, alert.productId, alert.binLocationId, alert.lotNo), alert)
      push(index.lotAlertsByProduct, pKey, alert)
    } else {
      push(index.byProduct, pKey, alert)
    }
  }
  return index
}

/** Một thẻ màu trên dòng tồn; có thể gộp nhiều cảnh báo cùng loại (ví dụ ba lô cận hạn của một mặt hàng). */
export interface RowTag {
  key: string
  kind: StockAlertKind
  severity: StockAlertSeverity
  label: string
  color: string
  /** Số cảnh báo được gộp vào thẻ */
  count: number
  /** Các cảnh báo gốc, để hiện lý do trong tooltip */
  alerts: StockAlert[]
}

function toTag(key: string, alerts: StockAlert[]): RowTag {
  const severity = alerts.map((a) => a.severity).reduce(maxSeverity)
  const kind = alerts[0].kind
  return {
    key,
    kind,
    severity,
    label: alertTagLabel(kind, severity),
    color: SEVERITY_COLORS[severity],
    count: alerts.length,
    alerts,
  }
}

/** Sắp xếp thẻ: nghiêm trọng trước, rồi theo thứ tự loại. */
export function sortTags(tags: RowTag[]): RowTag[] {
  return [...tags].sort(
    (a, b) => severityRank(a.severity) - severityRank(b.severity) || kindRank(a.kind) - kindRank(b.kind),
  )
}

/** Thẻ của một dòng tồn theo lô và ô kệ: cảnh báo của chính lô đó và cảnh báo của mặt hàng tại kho đó. */
export function tagsForLotRow(
  index: AlertIndex,
  row: { warehouseId: number | null; productId: number; binLocationId: number | null; lotNo: string | null },
): RowTag[] {
  const lotAlerts = index.byLot.get(lotKey(row.warehouseId, row.productId, row.binLocationId, row.lotNo)) ?? []
  const productAlerts = index.byProduct.get(productKey(row.warehouseId, row.productId)) ?? []
  const tags = [...lotAlerts, ...productAlerts].map((alert) => toTag(alert.id, [alert]))
  return sortTags(tags)
}

/**
 * Thẻ của một dòng tồn theo hàng hóa và kho: cảnh báo của mặt hàng (mỗi cảnh báo một thẻ) và cảnh báo của các lô
 * thuộc mặt hàng đó (gộp theo loại, kèm số lô).
 */
export function tagsForProductRow(
  index: AlertIndex,
  row: { warehouseId: number | null; productId: number },
): RowTag[] {
  const key = productKey(row.warehouseId, row.productId)
  const tags = (index.byProduct.get(key) ?? []).map((alert) => toTag(alert.id, [alert]))
  const lotGroups = new Map<StockAlertKind, StockAlert[]>()
  for (const alert of index.lotAlertsByProduct.get(key) ?? []) {
    const list = lotGroups.get(alert.kind)
    if (list) list.push(alert)
    else lotGroups.set(alert.kind, [alert])
  }
  lotGroups.forEach((alerts, kind) => tags.push(toTag(`${kind}:${key}`, alerts)))
  return sortTags(tags)
}

/** Dòng có thẻ nào thuộc loại hoặc mức đang lọc không (dùng để làm nổi bật dòng). */
export function hasMatchingTag(
  tags: readonly RowTag[],
  kind: StockAlertKind | undefined,
  severity: StockAlertSeverity | undefined,
): boolean {
  return tags.some((tag) => (!kind || tag.kind === kind) && (!severity || tag.alerts.some((a) => a.severity === severity)))
}

// ===================== Định dạng =====================

const quantityFormatter = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

/** Số lượng kiểu Việt Nam, tối đa 3 chữ số thập phân; thiếu giá trị thì hiện gạch ngang. */
export function formatQuantity(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return quantityFormatter.format(Number(value))
}

/** Ngày `yyyy-MM-dd` (LocalDate của backend) thành `dd/MM/yyyy`, không qua Date để không lệch múi giờ. */
export function formatLocalDate(value: string | null | undefined): string {
  if (!value) return '—'
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  return match ? `${match[3]}/${match[2]}/${match[1]}` : '—'
}

const vietnamDateParts = new Intl.DateTimeFormat('en-CA', {
  timeZone: VIETNAM_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** Ngày theo giờ Việt Nam dạng `yyyy-MM-dd` của một thời điểm. */
export function vietnamIsoDate(instant: Date): string {
  const parts = vietnamDateParts.formatToParts(instant)
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return `${get('year')}-${get('month')}-${get('day')}`
}

/** Thời điểm ISO 8601 (UTC) thành ngày `dd/MM/yyyy` theo giờ Việt Nam. */
export function formatInstantDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : formatLocalDate(vietnamIsoDate(date))
}

/** Giờ `HH:mm:ss` theo giờ Việt Nam. */
export function formatClock(instant: Date): string {
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: VIETNAM_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(instant)
}

/** Diễn giải số ngày còn tới hạn dùng; 0 hoặc âm là đã hết hạn (cùng quy ước với backend). */
export function daysLeftText(daysLeft: number | null | undefined): string {
  if (daysLeft === null || daysLeft === undefined) return '—'
  if (daysLeft > 0) return `Còn ${daysLeft} ngày`
  if (daysLeft === 0) return 'Hết hạn hôm nay'
  return `Quá hạn ${-daysLeft} ngày`
}

/** Lý do chi tiết của một cảnh báo, dùng trong tooltip của thẻ. */
export function alertReason(alert: StockAlert): string {
  switch (alert.kind) {
    case 'EXPIRED':
    case 'NEAR_EXPIRY': {
      const lot = alert.lotNo ? `Lô ${alert.lotNo}` : 'Lô không số lô'
      const bin = alert.binCode ? ` tại ô ${alert.binCode}` : ''
      return `${lot}${bin}: hạn dùng ${formatLocalDate(alert.expDate)}, ${daysLeftText(alert.daysLeft).toLowerCase()}, `
        + `còn ${formatQuantity(alert.quantity)}`
    }
    case 'LOW_STOCK': {
      const threshold = alert.severity === 'CRITICAL'
        ? `mức an toàn ${formatQuantity(alert.safetyStock)}`
        : `điểm đặt hàng lại ${formatQuantity(alert.reorderPoint)}`
      return `Tồn dùng được ${formatQuantity(alert.quantity)} so với ${threshold}`
    }
    case 'DEAD_STOCK':
      return `${alert.daysWithoutMovement ?? '—'} ngày không nhập xuất (lần cuối ${formatInstantDate(alert.lastMovementAt)}), `
        + `còn ${formatQuantity(alert.quantity)}`
  }
}

// ===================== Thời gian thực =====================

/**
 * Tin thời gian thực của kho `warehouseId` có liên quan tới bảng đang xem không.
 * `visibleWarehouseIds = null` nghĩa là bảng đang xem mọi kho trong phạm vi; tin không ghi kho thì luôn coi là liên quan.
 */
export function isRelevantWarehouse(
  warehouseId: number | null | undefined,
  visibleWarehouseIds: readonly number[] | null,
): boolean {
  if (warehouseId === null || warehouseId === undefined) return true
  if (visibleWarehouseIds === null) return true
  return visibleWarehouseIds.includes(Number(warehouseId))
}

/** Số mili giây phải chờ trước lần làm mới kế tiếp để không làm mới quá một lần mỗi `minIntervalMs`. */
export function throttleDelay(now: number, lastRunAt: number | null, minIntervalMs: number): number {
  if (lastRunAt === null) return 0
  return Math.max(0, lastRunAt + minIntervalMs - now)
}
