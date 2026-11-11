import { Button, Descriptions, Modal, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { onSubscriptionApiError, PLAN_LIMIT_EXCEEDED, SUBSCRIPTION_EXPIRED } from '../../../services/api'
import type { SubscriptionApiError } from '../../../services/api'
import { getStoredUser } from '../../../utils/storage'
import { enableReadOnlyGuard } from '../readOnlyGuard'
import { useSubscriptionStore } from '../subscriptionStore'
import { formatLimit, LIMIT_LABELS, PLAN_LABELS } from '../subscriptionFormat'
import type { PlanLimitViolation } from '../types'

const SUBSCRIPTION_PATH = '/subscription'

interface SubscriptionDialogsProps {
  /** Thuê bao đã hết hạn: bật chế độ chỉ đọc trên toàn trang. */
  readOnly: boolean
}

function toViolation(data: unknown): PlanLimitViolation | null {
  if (!data || typeof data !== 'object') return null
  const value = data as Partial<PlanLimitViolation>
  if (typeof value.limit !== 'string') return null
  return {
    limit: value.limit,
    current: typeof value.current === 'number' ? value.current : Number.NaN,
    max: typeof value.max === 'number' ? value.max : Number.NaN,
    plan: typeof value.plan === 'string' ? value.plan : '',
  }
}

/**
 * Hai hộp thoại dùng chung cho mọi màn hình:
 * - Hết hạn (SUBSCRIPTION_EXPIRED hoặc bấm nút ghi khi đang chỉ đọc): chỉ một hộp thoại dù nhiều request lỗi.
 * - Vượt giới hạn gói (PLAN_LIMIT_EXCEEDED): nêu giới hạn đã chạm, đang dùng trên tối đa; ADMIN có nút nâng cấp.
 */
export default function SubscriptionDialogs({ readOnly }: SubscriptionDialogsProps) {
  const navigate = useNavigate()
  const markExpired = useSubscriptionStore((state) => state.markExpired)
  const reload = useSubscriptionStore((state) => state.load)
  const [expiredOpen, setExpiredOpen] = useState(false)
  const [expiredMessage, setExpiredMessage] = useState<string | null>(null)
  const [limitError, setLimitError] = useState<{ message: string; violation: PlanLimitViolation | null } | null>(
    null,
  )
  const isAdmin = Boolean(getStoredUser()?.roles?.includes('ADMIN'))

  useEffect(() => {
    return onSubscriptionApiError((error: SubscriptionApiError) => {
      if (error.code === SUBSCRIPTION_EXPIRED) {
        markExpired()
        void reload()
        setExpiredMessage(error.message || null)
        setExpiredOpen(true)
      } else if (error.code === PLAN_LIMIT_EXCEEDED) {
        // Nhiều request cùng vượt giới hạn (ví dụ nhập hàng loạt) chỉ mở một hộp thoại
        setLimitError((current) => current ?? { message: error.message, violation: toViolation(error.data) })
      }
    })
  }, [markExpired, reload])

  // Chế độ chỉ đọc: làm mờ và chặn các nút ghi trên toàn trang; bấm vào thì mở hộp thoại hết hạn
  useEffect(() => {
    if (!readOnly) return undefined
    return enableReadOnlyGuard(() => {
      setExpiredMessage(null)
      setExpiredOpen(true)
    })
  }, [readOnly])

  const goToSubscription = () => {
    setExpiredOpen(false)
    setLimitError(null)
    navigate(SUBSCRIPTION_PATH)
  }

  const violation = limitError?.violation ?? null
  const planLabel = violation?.plan ? (PLAN_LABELS[violation.plan] ?? violation.plan) : null

  return (
    <>
      <Modal
        open={expiredOpen}
        title="Gói dịch vụ đã hết hạn"
        onCancel={() => setExpiredOpen(false)}
        footer={[
          <Button key="close" onClick={() => setExpiredOpen(false)}>
            Đóng
          </Button>,
          <Button key="go" type="primary" onClick={goToSubscription}>
            Xem gói dịch vụ
          </Button>,
        ]}
        wrapClassName="wms-read-only-allowed"
        destroyOnHidden
      >
        <Typography.Paragraph>
          Hệ thống đang ở chế độ chỉ đọc: vẫn xem được dữ liệu nhưng không thể tạo, sửa hoặc xóa.
        </Typography.Paragraph>
        {expiredMessage && <Typography.Paragraph type="secondary">{expiredMessage}</Typography.Paragraph>}
        <Typography.Paragraph className="mb-0">
          {isAdmin
            ? 'Gia hạn hoặc chuyển gói trên trang Gói dịch vụ để mở lại các thao tác ghi.'
            : 'Liên hệ quản trị viên của doanh nghiệp để gia hạn gói dịch vụ.'}
        </Typography.Paragraph>
      </Modal>

      <Modal
        open={limitError !== null}
        title="Đã chạm giới hạn của gói dịch vụ"
        onCancel={() => setLimitError(null)}
        footer={[
          <Button key="close" onClick={() => setLimitError(null)}>
            Đóng
          </Button>,
          isAdmin ? (
            <Button key="upgrade" type="primary" onClick={goToSubscription}>
              Nâng cấp gói
            </Button>
          ) : null,
        ]}
        wrapClassName="wms-read-only-allowed"
        destroyOnHidden
      >
        {limitError?.message && <Typography.Paragraph>{limitError.message}</Typography.Paragraph>}
        {violation && (
          <Descriptions column={1} size="small" bordered className="mb-3">
            <Descriptions.Item label="Giới hạn">{LIMIT_LABELS[violation.limit] ?? violation.limit}</Descriptions.Item>
            {violation.limit !== 'API_ACCESS' && (
              <Descriptions.Item label="Đang dùng / tối đa">
                {Number.isFinite(violation.current) ? violation.current : '—'} /{' '}
                {Number.isFinite(violation.max) ? formatLimit(violation.max) : '—'}
              </Descriptions.Item>
            )}
            {planLabel && <Descriptions.Item label="Gói hiện tại">{planLabel}</Descriptions.Item>}
          </Descriptions>
        )}
        {!isAdmin && (
          <Typography.Paragraph type="secondary" className="mb-0">
            Liên hệ quản trị viên của doanh nghiệp để nâng cấp gói dịch vụ.
          </Typography.Paragraph>
        )}
      </Modal>
    </>
  )
}
