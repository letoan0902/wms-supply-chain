/**
 * Hỗ trợ gọi các API chứng từ làm thay đổi tồn kho: phiếu nhập, xác nhận đơn bán, phiếu xuất
 * (hợp đồng ở docs/api/CHUNG_TU_TON_KHO.md). Gồm khóa chống lập trùng phiếu nhập và cách đọc lỗi
 * (thiếu hàng, vị trí đóng băng kiểm kê, không rõ kết quả).
 *
 * Tệp không phụ thuộc axios hay React để kiểm tra được bằng Node: lỗi được đọc theo hình dạng lỗi của axios
 * (`isAxiosError`, `response.status`, `response.data`).
 */

/** Một vị trí đang đóng băng kiểm kê: phần tử của `data` trong lỗi 409 khi nhập hoặc xuất vào ô đang kiểm kê. */
export interface FrozenLocation {
  sessionId: number | null
  sessionCode: string
  binLocationId: number | null
  binLocationCode: string
  productId: number | null
}

/** Một sản phẩm không giữ đủ chỗ: phần tử của `data` trong lỗi 400 khi xác nhận đơn bán thiếu hàng. */
export interface StockShortage {
  productId: number | null
  skuCode: string
  requestedQuantity: number
  availableQuantity: number
  shortageQuantity: number
}

interface ErrorBody {
  code?: string
  message?: string
  data?: unknown
}

interface ErrorLike {
  isAxiosError?: boolean
  response?: { status?: number; data?: ErrorBody }
}

function asErrorLike(error: unknown): ErrorLike | null {
  return typeof error === 'object' && error !== null ? (error as ErrorLike) : null
}

/** Mã HTTP của lỗi; undefined khi không có phản hồi từ máy chủ. */
export function errorStatusOf(error: unknown): number | undefined {
  return asErrorLike(error)?.response?.status
}

/** Thông báo tiếng Việt của máy chủ trong thân lỗi (rỗng nếu không có). */
export function errorMessageOf(error: unknown): string {
  const message = asErrorLike(error)?.response?.data?.message
  return typeof message === 'string' ? message : ''
}

function errorDataOf(error: unknown): unknown {
  return asErrorLike(error)?.response?.data?.data
}

/**
 * Không biết máy chủ đã ghi nhận yêu cầu hay chưa: lỗi mạng, hết thời gian chờ (không có phản hồi), hoặc gateway
 * trả 502/504 khi service phía sau không trả lời kịp.
 */
export function isOutcomeUnknown(error: unknown): boolean {
  const e = asErrorLike(error)
  if (!e || e.isAxiosError !== true) return false
  const status = e.response?.status
  return status === undefined || status === 502 || status === 504
}

function toNumber(value: unknown): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

function toId(value: unknown): number | null {
  const n = Number(value)
  return value !== null && value !== undefined && Number.isFinite(n) ? n : null
}

function asRecords(data: unknown): Record<string, unknown>[] {
  const list: unknown[] = Array.isArray(data) ? data : data !== undefined && data !== null ? [data] : []
  return list.filter((x): x is Record<string, unknown> => typeof x === 'object' && x !== null && !Array.isArray(x))
}

/**
 * Danh sách vị trí đóng băng trong `data` của lỗi (rỗng nếu lỗi không phải loại này). Lập phiếu nhập trả một mảng;
 * xuất kho trả lại nguyên lỗi của inventory-service với `data` là một đối tượng.
 */
export function frozenLocationsOf(error: unknown): FrozenLocation[] {
  return asRecords(errorDataOf(error))
    .filter((r) => typeof r.binLocationCode === 'string' || typeof r.sessionCode === 'string')
    .map((r) => ({
      sessionId: toId(r.sessionId),
      sessionCode: typeof r.sessionCode === 'string' ? r.sessionCode : '',
      binLocationId: toId(r.binLocationId),
      binLocationCode: typeof r.binLocationCode === 'string' ? r.binLocationCode : '',
      productId: toId(r.productId),
    }))
}

/** Danh sách thiếu hàng trong `data` của lỗi xác nhận đơn bán (rỗng nếu lỗi không kèm danh sách). */
export function stockShortagesOf(error: unknown): StockShortage[] {
  return asRecords(errorDataOf(error))
    .filter((r) => r.requestedQuantity !== undefined && r.availableQuantity !== undefined)
    .map((r) => {
      const requested = toNumber(r.requestedQuantity)
      const available = toNumber(r.availableQuantity)
      return {
        productId: toId(r.productId),
        skuCode: typeof r.skuCode === 'string' ? r.skuCode : '',
        requestedQuantity: requested,
        availableQuantity: available,
        shortageQuantity:
          r.shortageQuantity !== undefined && r.shortageQuantity !== null
            ? toNumber(r.shortageQuantity)
            : Math.max(0, requested - available),
      }
    })
}

// ---------------------------------------------------------------------------------------------------------------
// Phiếu nhập kho: khóa chống lập trùng (header Idempotency-Key)
// ---------------------------------------------------------------------------------------------------------------

/** Tên header gửi khóa chống trùng và header máy chủ trả khi request là bản gửi lại. */
export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key'
export const IDEMPOTENT_REPLAYED_HEADER = 'idempotent-replayed'

interface CryptoLike {
  randomUUID?: () => string
  getRandomValues?: (array: Uint8Array<ArrayBuffer>) => Uint8Array<ArrayBuffer>
}

/**
 * Sinh khóa chống trùng dạng UUID. `crypto.randomUUID` chỉ có trên HTTPS hoặc localhost; trang mở bằng HTTP theo
 * địa chỉ IP trong mạng nội bộ thì dùng `crypto.getRandomValues`, cuối cùng mới tới thời gian cộng số ngẫu nhiên.
 */
export function newIdempotencyKey(cryptoImpl: CryptoLike | undefined = globalThis.crypto): string {
  if (cryptoImpl && typeof cryptoImpl.randomUUID === 'function') {
    try {
      return cryptoImpl.randomUUID()
    } catch {
      // Ngữ cảnh không an toàn: thử cách dưới
    }
  }
  if (cryptoImpl && typeof cryptoImpl.getRandomValues === 'function') {
    const bytes = cryptoImpl.getRandomValues(new Uint8Array(16))
    bytes[6] = (bytes[6] & 0x0f) | 0x40
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  }
  const random = () => Math.random().toString(36).slice(2, 10).padEnd(8, '0')
  return `grn-${Date.now().toString(36)}-${random()}${random()}`
}

/**
 * Phản hồi thành công của lập phiếu nhập là bản gửi lại (phiếu đã lập từ lần bấm trước): 200 thay vì 201, hoặc header
 * `Idempotent-Replayed: true` (header chỉ đọc được khi cùng nguồn hoặc gateway cho phép đọc, nên xét cả mã 200).
 */
export function isReplayedReceipt(status: number, headers: Record<string, unknown> | undefined): boolean {
  const flag = headers?.[IDEMPOTENT_REPLAYED_HEADER]
  return status === 200 || String(flag ?? '').toLowerCase() === 'true'
}

/** Kết quả lỗi của một lần bấm lập phiếu nhập. */
export type ReceiptSubmitFailure =
  /** Có dòng cộng tồn vào ô đang đóng băng kiểm kê: chọn ô khác rồi lưu lại */
  | { kind: 'frozen'; message: string; locations: FrozenLocation[] }
  /** Khóa đã dùng cho đơn mua khác: sinh khóa mới, người dùng bấm lưu lại */
  | { kind: 'keyConflict'; message: string }
  /** Không rõ kết quả (mất mạng, hết thời gian chờ): giữ khóa, bấm lại an toàn */
  | { kind: 'unknown'; message: string }
  /** Máy chủ báo chưa ghi gì (503): giữ khóa, bấm lại an toàn */
  | { kind: 'unavailable'; message: string }
  /** Lỗi khác (dữ liệu sai, không có quyền...): hiện thông báo của máy chủ */
  | { kind: 'rejected'; message: string }

export function classifyReceiptSubmitError(error: unknown): ReceiptSubmitFailure {
  const message = errorMessageOf(error)
  if (isOutcomeUnknown(error)) return { kind: 'unknown', message }
  const status = errorStatusOf(error)
  if (status === 409) {
    const locations = frozenLocationsOf(error)
    if (locations.length > 0) return { kind: 'frozen', message, locations }
    if (message.includes(IDEMPOTENCY_KEY_HEADER)) return { kind: 'keyConflict', message }
  }
  if (status === 503) return { kind: 'unavailable', message }
  return { kind: 'rejected', message }
}

/**
 * Vòng đời khóa của một form lập phiếu nhập: một khóa mỗi lần mở form, gửi lại cùng khóa cho mọi lần bấm sau lỗi;
 * chỉ đổi khóa sau khi lập thành công (kể cả bản gửi lại) hoặc khi khóa đã dùng cho đơn mua khác.
 */
export function shouldRenewKeyAfter(outcome: 'created' | 'replayed' | ReceiptSubmitFailure['kind']): boolean {
  return outcome === 'created' || outcome === 'replayed' || outcome === 'keyConflict'
}

// ---------------------------------------------------------------------------------------------------------------
// Phiếu xuất kho
// ---------------------------------------------------------------------------------------------------------------

/**
 * Lập phiếu xuất ở trạng thái hoàn tất mà không xác nhận được việc trừ tồn (503): máy chủ đã lưu phiếu ở PICKING và
 * nêu mã phiếu trong thông báo. Trả mã phiếu đó, hoặc null nếu lỗi không phải trường hợp này.
 */
export function pendingDispatchGdnNumberOf(error: unknown): string | null {
  if (errorStatusOf(error) !== 503) return null
  const message = errorMessageOf(error)
  if (!message.includes('PICKING')) return null
  const match = /\b(GDN-[A-Za-z0-9-]*[A-Za-z0-9])/.exec(message)
  return match ? match[1] : null
}
