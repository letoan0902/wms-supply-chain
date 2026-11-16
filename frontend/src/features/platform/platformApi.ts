/**
 * API quản trị nền tảng của auth-service (tiền tố `/api/auth/platform`, chỉ vai trò SUPER_ADMIN; vai trò khác nhận 403).
 * Kiểu dữ liệu khớp hợp đồng chung của tính năng (mục 3). Thời điểm là chuỗi ISO-8601 UTC, số tiền là số nguyên VND.
 */
import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { BillingCycle, PaymentStatus, PlanCode, SubscriptionStatus } from '../subscription/types'
import {
  buildCompanyQuery,
  buildPaymentQuery,
  type CompanyFilters,
  type PaymentFilters,
} from './platformQuery'

export type CompanyStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED'

/** Một doanh nghiệp khách hàng trong danh sách (GET /api/auth/platform/companies). */
export interface PlatformCompanySummary {
  id: number
  code: string
  name: string
  taxCode: string | null
  email: string | null
  phone: string | null
  status: CompanyStatus | string
  /** `null` khi doanh nghiệp chưa có thuê bao (PENDING) */
  plan: PlanCode | string | null
  planName: string | null
  subscriptionStatus: SubscriptionStatus | string | null
  /** Hạn đang áp dụng: hạn dùng thử với TRIAL, hạn gói với PRO; `null` nếu không có hạn hoặc chưa có thuê bao */
  expiresAt: string | null
  userCount: number
  createdAt: string
  activatedAt: string | null
}

/** Một giao dịch thanh toán của bất kỳ doanh nghiệp nào (GET /api/auth/platform/payments). */
export interface PlatformPayment {
  /** Mã đơn PayOS, tối đa 16 chữ số nên vẫn nằm trong phạm vi số nguyên an toàn của JavaScript */
  orderCode: number
  companyId: number
  companyCode: string
  companyName: string
  plan: PlanCode | string
  cycle: BillingCycle | string
  amount: number
  status: PaymentStatus | string
  createdAt: string
  paidAt: string | null
}

/** Phần `data` của GET /api/auth/platform/overview. */
export interface PlatformOverview {
  companies: { total: number; active: number; pending: number; suspended: number }
  subscriptions: { free: number; trial: number; pro: number; expired: number; expiringSoon: number }
  users: { total: number; active: number }
  revenue: { total: number; thisMonth: number; paidCount: number }
  /** Đúng 6 tháng gần nhất kể cả tháng hiện tại, tăng dần; `month` dạng `yyyy-MM` theo giờ Việt Nam */
  registrationsByMonth: { month: string; count: number }[]
  revenueByMonth: { month: string; amount: number }[]
  /** 5 doanh nghiệp tạo gần nhất */
  recentCompanies: PlatformCompanySummary[]
  /** 5 thanh toán PAID gần nhất */
  recentPayments: PlatformPayment[]
}

/** Giới hạn của gói; `-1` là không giới hạn (cùng quy ước với GET /api/auth/subscription). */
export interface PlatformCompanyLimits {
  maxUsers: number
  maxWarehouses: number
  maxSkus: number
  maxAiRequests: number
  apiAccess: boolean
}

/** Mức dùng hiện tại; số nào máy chủ không đọc được thì là `null` (không phải 0). */
export interface PlatformCompanyUsage {
  users: number
  warehouses: number | null
  skus: number | null
  aiRequests: number | null
}

/** Tài khoản có vai trò ADMIN của doanh nghiệp. */
export interface PlatformCompanyAdmin {
  id: number
  username: string
  fullName: string | null
  email: string | null
  enabled: boolean
}

/** Phần `data` của GET /api/auth/platform/companies/{id}. */
export interface PlatformCompanyDetail {
  company: PlatformCompanySummary & { address: string | null }
  /** `null` khi doanh nghiệp chưa có thuê bao */
  limits: PlatformCompanyLimits | null
  usage: PlatformCompanyUsage | null
  admins: PlatformCompanyAdmin[]
  /** 10 thanh toán gần nhất của doanh nghiệp */
  payments: PlatformPayment[]
}

/** Thân của PUT /api/auth/platform/companies/{id}/status. */
export interface UpdateCompanyStatusRequest {
  status: 'SUSPENDED' | 'ACTIVE'
  /** Tùy chọn, tối đa 255 ký tự */
  reason?: string
}

/**
 * Thân (tùy chọn) của POST /api/auth/platform/companies/{id}/approve. Không gửi gì thì cấp gói dùng thử 30 ngày.
 * `trialDays` (1 đến 90) chỉ dùng với TRIAL, `cycle` bắt buộc với PRO.
 */
export type ApproveCompanyRequest =
  | { plan: 'TRIAL'; trialDays?: number }
  | { plan: 'PRO'; cycle: BillingCycle }

/** Thân của POST /api/auth/platform/companies/{id}/reject. */
export interface RejectCompanyRequest {
  /** Bắt buộc, tối đa 255 ký tự; máy chủ chỉ ghi vào nhật ký, không gửi trong email */
  reason: string
}

const BASE = '/auth/platform'

/** GET /api/auth/platform/overview: số liệu tổng quan của nền tảng (không tính doanh nghiệp PLATFORM). */
export async function fetchPlatformOverview(): Promise<PlatformOverview> {
  const response = await api.get<ApiResponse<PlatformOverview>>(`${BASE}/overview`)
  return response.data.data
}

/** GET /api/auth/platform/companies: danh sách doanh nghiệp khách hàng, mới tạo trước, `page` tính từ 0. */
export async function fetchPlatformCompanies(filters: CompanyFilters): Promise<PageResponse<PlatformCompanySummary>> {
  const response = await api.get<ApiResponse<PageResponse<PlatformCompanySummary>>>(`${BASE}/companies`, {
    params: buildCompanyQuery(filters),
  })
  return response.data.data
}

/** GET /api/auth/platform/companies/{id}: chi tiết một doanh nghiệp; không có hoặc là doanh nghiệp PLATFORM trả 404. */
export async function fetchPlatformCompany(id: number): Promise<PlatformCompanyDetail> {
  const response = await api.get<ApiResponse<PlatformCompanyDetail>>(`${BASE}/companies/${id}`)
  return response.data.data
}

/**
 * PUT /api/auth/platform/companies/{id}/status: tạm khóa (chỉ từ ACTIVE) hoặc mở khóa (chỉ từ SUSPENDED).
 * Chuyển trạng thái không hợp lệ trả 400 kèm thông báo của máy chủ.
 */
export async function updatePlatformCompanyStatus(
  id: number,
  payload: UpdateCompanyStatusRequest,
): Promise<PlatformCompanySummary> {
  const response = await api.put<ApiResponse<PlatformCompanySummary>>(`${BASE}/companies/${id}/status`, payload)
  return response.data.data
}

/**
 * POST /api/auth/platform/companies/{id}/approve: duyệt bản đăng ký đang chờ (PENDING). Doanh nghiệp hoạt động ngay với
 * gói đã chọn; doanh nghiệp không còn PENDING trả 400.
 */
export async function approvePlatformCompany(
  id: number,
  payload: ApproveCompanyRequest,
): Promise<PlatformCompanySummary> {
  const response = await api.post<ApiResponse<PlatformCompanySummary>>(`${BASE}/companies/${id}/approve`, payload)
  return response.data.data
}

/**
 * POST /api/auth/platform/companies/{id}/reject: từ chối bản đăng ký đang chờ (PENDING). Doanh nghiệp chuyển SUSPENDED,
 * người đăng ký không đăng nhập được; doanh nghiệp không còn PENDING trả 400.
 */
export async function rejectPlatformCompany(
  id: number,
  payload: RejectCompanyRequest,
): Promise<PlatformCompanySummary> {
  const response = await api.post<ApiResponse<PlatformCompanySummary>>(`${BASE}/companies/${id}/reject`, payload)
  return response.data.data
}

/** GET /api/auth/platform/payments: giao dịch của mọi doanh nghiệp, mới tạo trước, `page` tính từ 0. */
export async function fetchPlatformPayments(filters: PaymentFilters): Promise<PageResponse<PlatformPayment>> {
  const response = await api.get<ApiResponse<PageResponse<PlatformPayment>>>(`${BASE}/payments`, {
    params: buildPaymentQuery(filters),
  })
  return response.data.data
}
