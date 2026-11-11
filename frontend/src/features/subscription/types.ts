/**
 * Kiểu dữ liệu phân hệ Gói dịch vụ (docs/SAAS_SPEC.md mục 2.5, 2.6 và API của auth-service).
 */

export type PlanCode = 'FREE' | 'TRIAL' | 'PRO'

export type SubscriptionStatus = 'ACTIVE' | 'EXPIRED'

export type BillingCycle = 'MONTHLY' | 'YEARLY'

/** Giới hạn của gói; `-1` là không giới hạn (vn.wms.auth.subscription.dto.SubscriptionResponse.LimitsDto). */
export interface PlanLimits {
  maxUsers: number | null
  maxWarehouses: number | null
  maxSkus: number | null
  maxAiRequests: number | null
  apiAccess: boolean | null
}

/**
 * Mức sử dụng hiện tại. `null` nghĩa là chưa có số liệu (service nghiệp vụ chưa ghi hoặc Redis lỗi),
 * không phải bằng 0.
 */
export interface PlanUsage {
  users: number | null
  warehouses: number | null
  skus: number | null
  aiRequests: number | null
}

/** Phần `data` của GET /api/auth/subscription. */
export interface SubscriptionInfo {
  plan: PlanCode | string
  planName: string
  status: SubscriptionStatus | string
  trialEndsAt: string | null
  expiresAt: string | null
  /** Số ngày còn lại, làm tròn lên; `null` với gói không thời hạn (FREE) */
  daysRemaining: number | null
  readOnly: boolean
  limits: PlanLimits | null
  usage: PlanUsage | null
}

/** Một dòng của GET /api/auth/plans (vn.wms.auth.subscription.dto.PlanResponse). */
export interface PlanInfo {
  code: PlanCode | string
  name: string
  priceMonthly: number | null
  priceYearly: number | null
  maxUsers: number | null
  maxWarehouses: number | null
  maxSkus: number | null
  maxAiRequests: number | null
  apiAccess: boolean | null
  trialDays: number | null
}

export type PaymentStatus = 'PENDING' | 'PAID' | 'CANCELLED' | 'EXPIRED'

/** Một giao dịch thanh toán (vn.wms.auth.payment.dto.PaymentResponse). */
export interface PaymentInfo {
  /** Mã đơn PayOS, tối đa 16 chữ số nên vẫn nằm trong phạm vi số nguyên an toàn của JavaScript */
  orderCode: number
  plan: string
  cycle: BillingCycle | string
  /** Số tiền VND do máy chủ tính từ bảng gói */
  amount: number
  status: PaymentStatus | string
  checkoutUrl: string | null
  qrCode: string | null
  reference: string | null
  expiresAt: string | null
  paidAt: string | null
  createdAt: string | null
}

/** Body của POST /api/auth/payments: không có số tiền, máy chủ tự lấy giá từ bảng gói. */
export interface CreatePaymentRequest {
  plan: 'PRO'
  cycle: BillingCycle
}

/** Loại giới hạn trong `data` của lỗi PLAN_LIMIT_EXCEEDED (vn.wms.common.plan.PlanLimit). */
export type PlanLimitKind = 'USERS' | 'WAREHOUSES' | 'SKUS' | 'AI_REQUESTS' | 'API_ACCESS'

/** `data` của lỗi PLAN_LIMIT_EXCEEDED (vn.wms.common.plan.PlanLimitViolation). */
export interface PlanLimitViolation {
  limit: PlanLimitKind | string
  current: number
  max: number
  plan: string
}
