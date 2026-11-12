/**
 * Hàm thuần (không phụ thuộc React) để hiển thị gói dịch vụ: giới hạn, mức dùng, dải nhắc hạn.
 */

/** Giá trị giới hạn mang nghĩa không giới hạn (docs/SAAS_SPEC.md mục 2.5, 2.7). */
export const UNLIMITED = -1

/** Số ngày còn lại từ mức này trở xuống thì dải nhắc dùng thử đổi sang màu cảnh báo. */
export const TRIAL_WARNING_DAYS = 7

/** Tên hiển thị của gói theo bảng ở mục 2.5, dùng khi máy chủ chỉ trả mã gói (ví dụ trong lỗi vượt giới hạn). */
export const PLAN_LABELS: Record<string, string> = {
  FREE: 'Khởi Đầu',
  TRIAL: 'Dùng thử',
  PRO: 'Chuyên Nghiệp',
}

export const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  EXPIRED: 'Đã hết hạn',
}

export const CYCLE_LABELS: Record<string, string> = {
  MONTHLY: 'Theo tháng',
  YEARLY: 'Theo năm',
}

/** Tên loại giới hạn trong lỗi PLAN_LIMIT_EXCEEDED. */
export const LIMIT_LABELS: Record<string, string> = {
  USERS: 'Số người dùng',
  WAREHOUSES: 'Số kho',
  SKUS: 'Số SKU',
  AI_REQUESTS: 'Lượt AI trong tháng',
  API_ACCESS: 'Quyền dùng API key',
}

const numberFormatter = new Intl.NumberFormat('vi-VN')

export function isUnlimited(max: number | null | undefined): boolean {
  return max === UNLIMITED
}

/** Hiển thị một giới hạn: `-1` thành "Không giới hạn", thiếu số liệu thành "—". */
export function formatLimit(max: number | null | undefined): string {
  if (max === null || max === undefined) return '—'
  if (isUnlimited(max)) return 'Không giới hạn'
  return numberFormatter.format(max)
}

/** Hiển thị mức dùng: `null` là chưa có số liệu, không hiển thị thành 0. */
export function formatUsage(used: number | null | undefined): string {
  if (used === null || used === undefined) return 'Chưa có số liệu'
  return numberFormatter.format(used)
}

/**
 * Phần trăm đã dùng so với giới hạn (0 đến 100), `null` khi không tính được:
 * chưa có số liệu, không giới hạn, hoặc thiếu giới hạn.
 */
export function usagePercent(used: number | null | undefined, max: number | null | undefined): number | null {
  if (used === null || used === undefined || max === null || max === undefined) return null
  if (isUnlimited(max)) return null
  if (max <= 0) return used > 0 ? 100 : 0
  return Math.min(100, Math.round((used / max) * 100))
}

/** Mức dùng đã chạm giới hạn: không thể tạo thêm (cùng điều kiện `current >= max` của PlanLimitChecker). */
export function isLimitReached(used: number | null | undefined, max: number | null | undefined): boolean {
  if (used === null || used === undefined || max === null || max === undefined) return false
  if (isUnlimited(max)) return false
  return used >= max
}

export type TrialBannerTone = 'info' | 'warning'

/** Màu dải nhắc dùng thử: cảnh báo khi còn từ {@link TRIAL_WARNING_DAYS} ngày trở xuống. */
export function trialBannerTone(daysLeft: number | null | undefined): TrialBannerTone {
  if (daysLeft === null || daysLeft === undefined) return 'info'
  return daysLeft <= TRIAL_WARNING_DAYS ? 'warning' : 'info'
}

/** Mốc hết hạn đang áp dụng: ngày hết dùng thử với TRIAL, ngày hết hạn với PRO, không có với FREE. */
export function effectiveExpiry(info: {
  plan: string
  trialEndsAt: string | null
  expiresAt: string | null
}): string | null {
  if (info.plan === 'FREE') return null
  if (info.plan === 'TRIAL') return info.trialEndsAt ?? info.expiresAt
  return info.expiresAt ?? info.trialEndsAt
}

/**
 * Được phép chuyển về gói Khởi Đầu hay không, cùng quy tắc với SubscriptionService#downgradeToFree:
 * đang dùng thử, hoặc thuê bao đã hết hạn (và chưa ở gói Khởi Đầu).
 */
export function canDowngradeToFree(info: { plan: string; status: string }): boolean {
  if (info.plan === 'FREE') return false
  return info.plan === 'TRIAL' || info.status === 'EXPIRED'
}

/** Định dạng ngày theo kiểu Việt Nam, ví dụ 09/12/2026. */
export function formatDate(isoString: string | null | undefined): string {
  if (!isoString) return '—'
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date)
}
