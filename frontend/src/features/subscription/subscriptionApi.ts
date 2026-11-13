import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { PlanInfo, SubscriptionInfo } from './types'

/** GET /api/auth/subscription: gói hiện tại, hạn dùng, giới hạn và mức sử dụng của doanh nghiệp (mọi tài khoản). */
export async function fetchSubscription(): Promise<SubscriptionInfo> {
  const response = await api.get<ApiResponse<SubscriptionInfo>>('/auth/subscription')
  return response.data.data
}

/** GET /api/auth/plans: bảng giá và giới hạn của các gói (công khai). */
export async function fetchPlans(): Promise<PlanInfo[]> {
  const response = await api.get<ApiResponse<PlanInfo[]>>('/auth/plans')
  return response.data.data ?? []
}

/**
 * POST /api/auth/subscription/downgrade (ADMIN): chuyển về gói Khởi Đầu.
 * Chỉ hợp lệ khi đang dùng thử hoặc thuê bao đã hết hạn; trường hợp khác máy chủ trả 409 CONFLICT.
 */
export async function downgradeToFree(): Promise<void> {
  await api.post<ApiResponse<void>>('/auth/subscription/downgrade')
}
