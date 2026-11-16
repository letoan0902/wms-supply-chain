/**
 * Định nghĩa kiểu dữ liệu cho phân hệ Quản lý Doanh nghiệp & Phê duyệt SaaS (Admin nền tảng).
 * Bám sát DTOs từ Backend auth-service (vn.wms.auth.company.dto).
 */

export type CompanyStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED'

export type SubscriptionPlan = 'FREE' | 'TRIAL' | 'PRO'

export type SubscriptionStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED'

export type BillingCycle = 'MONTHLY' | 'YEARLY'

export interface AdminUserSummary {
  id: number
  username: string
  email: string
  fullName?: string | null
  phone?: string | null
  enabled: boolean
}

export interface CompanySubscriptionSummary {
  plan: SubscriptionPlan
  status: SubscriptionStatus
  trialEndsAt?: string | null
  expiresAt?: string | null
  billingCycle?: BillingCycle | null
}

export interface AdminCompanyItem {
  id: number
  code: string
  name: string
  taxCode?: string | null
  status: CompanyStatus
  createdAt: string
  updatedAt: string
  adminUser?: AdminUserSummary | null
  subscription?: CompanySubscriptionSummary | null
}

export interface ApproveCompanyPayload {
  plan?: SubscriptionPlan
  trialDays?: number
  cycle?: BillingCycle
  note?: string
}

export interface RejectCompanyPayload {
  reason: string
}

export interface UpdateCompanyStatusPayload {
  status: 'ACTIVE' | 'SUSPENDED'
}

export interface CompanyFilterParams {
  keyword?: string
  status?: CompanyStatus | 'ALL'
  page?: number
  size?: number
}
