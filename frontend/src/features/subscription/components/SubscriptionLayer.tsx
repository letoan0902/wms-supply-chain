import { Alert } from 'antd'
import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useSubscription, useSubscriptionStore } from '../subscriptionStore'
import { trialBannerTone } from '../subscriptionFormat'
import SubscriptionDialogs from './SubscriptionDialogs'

/** Quay lại tab sau ít nhất chừng này thời gian thì tải lại thuê bao (có thể đã được gia hạn ở nơi khác). */
const REFRESH_ON_FOCUS_AFTER_MS = 30_000

/**
 * Lớp thuê bao gắn một lần trong MainLayout: tải thuê bao sau khi đăng nhập, hiện dải nhắc dùng thử hoặc dải
 * hết hạn ở đầu mọi trang, bật chế độ chỉ đọc và các hộp thoại hết hạn, vượt giới hạn gói.
 */
export default function SubscriptionLayer() {
  const { subscription, plan, status, daysLeft, readOnly } = useSubscription()
  const load = useSubscriptionStore((state) => state.load)

  useEffect(() => {
    void load()
    const handleVisible = () => {
      if (document.visibilityState !== 'visible') return
      const loadedAt = useSubscriptionStore.getState().loadedAt
      if (loadedAt === null || Date.now() - loadedAt > REFRESH_ON_FOCUS_AFTER_MS) {
        void load()
      }
    }
    document.addEventListener('visibilitychange', handleVisible)
    return () => document.removeEventListener('visibilitychange', handleVisible)
  }, [load])

  let banner = null
  if (subscription && readOnly) {
    banner = (
      <Alert
        type="error"
        banner
        showIcon
        className="sticky top-0 z-20"
        message={
          <span>
            Gói đã hết hạn, hệ thống đang ở chế độ chỉ đọc.{' '}
            <Link to="/subscription" className="font-medium underline">
              Xem gói dịch vụ
            </Link>
          </span>
        }
      />
    )
  } else if (subscription && plan === 'TRIAL' && status === 'ACTIVE' && daysLeft !== null) {
    banner = (
      <Alert
        type={trialBannerTone(daysLeft)}
        banner
        showIcon
        message={
          <span>
            Còn {daysLeft} ngày dùng thử.{' '}
            <Link to="/subscription" className="font-medium underline">
              Xem gói dịch vụ
            </Link>
          </span>
        }
      />
    )
  }

  return (
    <>
      {banner}
      <SubscriptionDialogs readOnly={readOnly} />
    </>
  )
}
