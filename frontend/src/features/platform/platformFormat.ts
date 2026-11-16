/**
 * Hàm thuần (không phụ thuộc React) để hiển thị số liệu quản trị nền tảng: nhãn tiếng Việt và màu thẻ của từng giá trị
 * enum, nhãn tháng, ngày giờ theo giờ Việt Nam. Nhãn gói, chu kỳ, trạng thái thuê bao và thanh toán dùng lại bảng của
 * phân hệ Gói dịch vụ để hai nơi luôn hiển thị giống nhau.
 */
import { VIETNAM_TIME_ZONE } from '../inventory/inventoryUtils'
import { PAYMENT_STATUS_COLORS, PAYMENT_STATUS_LABELS } from '../subscription/paymentLogic'
import { CYCLE_LABELS, PLAN_LABELS, STATUS_LABELS } from '../subscription/subscriptionFormat'

export { CYCLE_LABELS, PAYMENT_STATUS_COLORS, PAYMENT_STATUS_LABELS, PLAN_LABELS }

/** Trạng thái doanh nghiệp (PENDING: chưa kích hoạt qua email). */
export const COMPANY_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  PENDING: 'Chờ kích hoạt',
  SUSPENDED: 'Tạm khóa',
}

/** Màu thẻ: cùng quy ước với trạng thái tài khoản (xanh lá, đỏ) và thanh toán chờ xử lý (xanh dương). */
export const COMPANY_STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'success',
  PENDING: 'processing',
  SUSPENDED: 'error',
}

export const SUBSCRIPTION_STATUS_LABELS: Record<string, string> = STATUS_LABELS

/** Cùng màu với thẻ trạng thái trên trang Gói dịch vụ. */
export const SUBSCRIPTION_STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'success',
  EXPIRED: 'error',
}

/** Thứ tự và màu các gói trong biểu đồ phân bổ. */
export const PLAN_ORDER = ['FREE', 'TRIAL', 'PRO'] as const

export const PLAN_COLORS: Record<string, string> = {
  FREE: '#94a3b8',
  TRIAL: '#1890ff',
  PRO: '#52c41a',
}

/** Nhãn hiển thị của một giá trị enum; giá trị lạ hiện "Không xác định" thay vì mã thô. */
export function enumLabel(labels: Record<string, string>, value: string | null | undefined): string {
  if (!value) return '—'
  return labels[value] ?? 'Không xác định'
}

/** Màu thẻ của một giá trị enum, giá trị lạ dùng màu mặc định. */
export function enumColor(colors: Record<string, string>, value: string | null | undefined): string {
  return (value && colors[value]) || 'default'
}

/** Tên gói: ưu tiên bảng nhãn của dự án, rồi tên máy chủ trả; chưa có thuê bao thì nói rõ. */
export function planLabel(plan: string | null | undefined, planName?: string | null): string {
  if (!plan) return 'Chưa có thuê bao'
  return PLAN_LABELS[plan] ?? planName ?? 'Không xác định'
}

const MONTH_PATTERN = /^(\d{4})-(\d{2})$/

/** Tháng `yyyy-MM` thành nhãn ngắn cho trục biểu đồ, ví dụ `2026-06` thành `T6/2026`. */
export function formatMonthShort(month: string): string {
  const match = MONTH_PATTERN.exec(month)
  return match ? `T${Number(match[2])}/${match[1]}` : month
}

/** Tháng `yyyy-MM` thành nhãn đầy đủ, ví dụ `2026-06` thành `Tháng 6/2026`. */
export function formatMonthLong(month: string): string {
  const match = MONTH_PATTERN.exec(month)
  return match ? `Tháng ${Number(match[2])}/${match[1]}` : month
}

const vietnamDateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
  timeZone: VIETNAM_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/**
 * Thời điểm ISO-8601 (UTC) thành `dd/MM/yyyy HH:mm` theo giờ Việt Nam, bất kể múi giờ của máy đang xem;
 * thiếu hoặc sai định dạng thì hiện gạch ngang.
 */
export function formatVietnamDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const parts = Object.fromEntries(vietnamDateTimeFormatter.formatToParts(date).map((part) => [part.type, part.value]))
  return `${parts.day}/${parts.month}/${parts.year} ${parts.hour}:${parts.minute}`
}
