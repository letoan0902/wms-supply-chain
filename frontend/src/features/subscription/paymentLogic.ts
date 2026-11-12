/**
 * Hàm thuần cho luồng thanh toán: đọc tham số PayOS trả về trên địa chỉ, kiểm tra liên kết, điều kiện hỏi lại.
 *
 * PayOS chuyển trình duyệt về PAYOS_RETURN_URL hoặc PAYOS_CANCEL_URL (mặc định đều là `/payment/result`) kèm
 * query `code`, `id`, `cancel` (`true` khi người dùng hủy), `status` (`PAID`, `PENDING`, `PROCESSING`,
 * `CANCELLED`) và `orderCode`. Các tham số này chỉ để hiển thị: trạng thái thật luôn hỏi lại máy chủ.
 */

/** Hỏi lại trạng thái giao dịch mỗi 2 giây. */
export const POLL_INTERVAL_MS = 2_000

/** Hỏi lại tối đa 60 giây, quá thời gian vẫn PENDING thì báo đang chờ ngân hàng xác nhận. */
export const POLL_TIMEOUT_MS = 60_000

/** Mã đơn PayOS tối đa 16 chữ số (máy chủ sinh mili giây epoch nhân 1000 cộng ba chữ số). */
const ORDER_CODE_PATTERN = /^[1-9]\d{0,15}$/

export const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
}

export const PAYMENT_STATUS_COLORS: Record<string, string> = {
  PENDING: 'processing',
  PAID: 'success',
  CANCELLED: 'default',
  EXPIRED: 'warning',
}

/** Mã đơn hợp lệ trên địa chỉ, hoặc `null` khi thiếu, sai định dạng hay vượt phạm vi số nguyên an toàn. */
export function parseOrderCode(params: URLSearchParams): number | null {
  const raw = params.get('orderCode')?.trim() ?? ''
  if (!ORDER_CODE_PATTERN.test(raw)) return null
  const value = Number(raw)
  return Number.isSafeInteger(value) ? value : null
}

/** Trình duyệt quay về từ nút hủy của PayOS (hoặc trang giả lập). */
export function isCancelReturn(params: URLSearchParams): boolean {
  return params.get('cancel')?.toLowerCase() === 'true' || params.get('status')?.toUpperCase() === 'CANCELLED'
}

/** Chỉ chuyển trình duyệt tới liên kết http hoặc https do máy chủ trả về. */
export function isSafeCheckoutUrl(url: string | null | undefined): url is string {
  if (!url) return false
  try {
    const parsed = new URL(url, typeof window === 'undefined' ? 'http://localhost' : window.location.origin)
    return parsed.protocol === 'https:' || parsed.protocol === 'http:'
  } catch {
    return false
  }
}

/** Còn hỏi lại tiếp hay không: chỉ khi giao dịch còn PENDING và chưa quá thời gian chờ. */
export function shouldKeepPolling(status: string, elapsedMs: number, timeoutMs: number = POLL_TIMEOUT_MS): boolean {
  return status === 'PENDING' && elapsedMs < timeoutMs
}
