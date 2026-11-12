import { create } from 'zustand'
import { getApiErrorMessage } from '../../services/api'
import { fetchSubscription } from './subscriptionApi'
import type { PlanLimits, PlanUsage, SubscriptionInfo } from './types'

interface SubscriptionState {
  subscription: SubscriptionInfo | null
  loading: boolean
  error: string | null
  /** Thời điểm tải thành công gần nhất (ms), để làm mới khi quay lại tab */
  loadedAt: number | null
  /** Tải lại thông tin thuê bao từ máy chủ; nhiều lời gọi cùng lúc dùng chung một request. */
  load: () => Promise<SubscriptionInfo | null>
  /** Máy chủ vừa trả SUBSCRIPTION_EXPIRED: bật chế độ chỉ đọc ngay, trước khi tải lại xong. */
  markExpired: () => void
  /** Xóa dữ liệu khi đăng xuất. */
  reset: () => void
}

let inflight: Promise<SubscriptionInfo | null> | null = null

export const useSubscriptionStore = create<SubscriptionState>()((set) => ({
  subscription: null,
  loading: false,
  error: null,
  loadedAt: null,

  load: () => {
    if (inflight) return inflight
    set({ loading: true })
    inflight = fetchSubscription()
      .then((subscription) => {
        set({ subscription, loading: false, error: null, loadedAt: Date.now() })
        return subscription
      })
      .catch((err: unknown) => {
        set({ loading: false, error: getApiErrorMessage(err, 'Không tải được thông tin gói dịch vụ') })
        return null
      })
      .finally(() => {
        inflight = null
      })
    return inflight
  },

  markExpired: () =>
    set((state) =>
      state.subscription
        ? { subscription: { ...state.subscription, status: 'EXPIRED', readOnly: true } }
        : state,
    ),

  reset: () => {
    set({ subscription: null, loading: false, error: null, loadedAt: null })
  },
}))

export interface UseSubscriptionResult {
  subscription: SubscriptionInfo | null
  plan: string | null
  planName: string | null
  status: string | null
  /** Số ngày còn lại do máy chủ tính (làm tròn lên); `null` với gói không thời hạn hoặc khi chưa tải */
  daysLeft: number | null
  /** Thuê bao đã hết hạn: mọi thao tác tạo, sửa, xóa bị khóa */
  readOnly: boolean
  limits: PlanLimits | null
  usage: PlanUsage | null
  loading: boolean
  error: string | null
  refresh: () => Promise<SubscriptionInfo | null>
}

/** Trạng thái thuê bao dùng chung toàn ứng dụng (tải sau khi đăng nhập, làm mới sau khi thanh toán). */
export function useSubscription(): UseSubscriptionResult {
  const subscription = useSubscriptionStore((state) => state.subscription)
  const loading = useSubscriptionStore((state) => state.loading)
  const error = useSubscriptionStore((state) => state.error)
  const refresh = useSubscriptionStore((state) => state.load)
  return {
    subscription,
    plan: subscription?.plan ?? null,
    planName: subscription?.planName ?? null,
    status: subscription?.status ?? null,
    daysLeft: subscription?.daysRemaining ?? null,
    readOnly: Boolean(subscription?.readOnly || subscription?.status === 'EXPIRED'),
    limits: subscription?.limits ?? null,
    usage: subscription?.usage ?? null,
    loading,
    error,
    refresh,
  }
}
