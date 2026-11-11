import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Col, Descriptions, Row, Space, Tag, Typography } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import PageLoading from '../../components/PageLoading'
import { getApiErrorMessage } from '../../services/api'
import { getStoredUser } from '../../utils/storage'
import PaymentHistoryTable from './components/PaymentHistoryTable'
import PlanActions from './components/PlanActions'
import PlanComparisonTable from './components/PlanComparisonTable'
import UsageCard from './components/UsageCard'
import { fetchPlans } from './subscriptionApi'
import { effectiveExpiry, formatDate, PLAN_LABELS, STATUS_LABELS } from './subscriptionFormat'
import { useSubscription } from './subscriptionStore'
import type { PlanInfo } from './types'

/**
 * Trang Gói dịch vụ: gói hiện tại, mức dùng, bảng so sánh gói; ADMIN thanh toán nâng cấp, gia hạn, chuyển gói và
 * xem lịch sử giao dịch.
 */
export default function SubscriptionPage() {
  const { subscription, readOnly, loading, error, refresh } = useSubscription()
  const [plans, setPlans] = useState<PlanInfo[]>([])
  const [plansLoading, setPlansLoading] = useState(true)
  const [plansError, setPlansError] = useState<string | null>(null)
  const isAdmin = Boolean(getStoredUser()?.roles?.includes('ADMIN'))

  const [plansReloadKey, setPlansReloadKey] = useState(0)

  useEffect(() => {
    // Mở trang thì tải lại để mức dùng là số mới nhất
    void refresh()
  }, [refresh])

  useEffect(() => {
    let ignore = false
    fetchPlans()
      .then((list) => {
        if (ignore) return
        setPlans(list)
        setPlansError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setPlansError(getApiErrorMessage(err, 'Không tải được bảng giá các gói'))
      })
      .finally(() => {
        if (!ignore) setPlansLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [plansReloadKey])

  const reloadAll = useCallback(() => {
    void refresh()
    setPlansLoading(true)
    setPlansReloadKey((key) => key + 1)
  }, [refresh])

  if (!subscription && loading) {
    return <PageLoading tip="Đang tải thông tin gói dịch vụ..." />
  }

  const expiry = subscription ? effectiveExpiry(subscription) : null
  const planLabel = subscription ? subscription.planName || PLAN_LABELS[subscription.plan] || subscription.plan : ''

  return (
    <div className="flex flex-col gap-4" data-read-only-allowed>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Typography.Title level={4} className="!m-0">
          Gói dịch vụ của doanh nghiệp
        </Typography.Title>
        <Button
          icon={<ReloadOutlined />}
          onClick={reloadAll}
          loading={loading}
        >
          Tải lại
        </Button>
      </div>

      {error && <Alert type="error" showIcon message={error} />}

      {subscription && (
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card title="Gói hiện tại" className="h-full">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="Gói">
                  <Space>
                    <Typography.Text strong>{planLabel}</Typography.Text>
                    <Tag>{subscription.plan}</Tag>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="Trạng thái">
                  <Tag color={readOnly ? 'error' : 'success'}>
                    {STATUS_LABELS[subscription.status] ?? subscription.status}
                  </Tag>
                  {readOnly && <Typography.Text type="danger">Chế độ chỉ đọc</Typography.Text>}
                </Descriptions.Item>
                <Descriptions.Item label={subscription.plan === 'TRIAL' ? 'Hết dùng thử' : 'Ngày hết hạn'}>
                  {subscription.plan === 'FREE' ? 'Không thời hạn' : formatDate(expiry)}
                </Descriptions.Item>
                <Descriptions.Item label="Số ngày còn lại">
                  {subscription.daysRemaining === null ? 'Không giới hạn' : `${subscription.daysRemaining} ngày`}
                </Descriptions.Item>
              </Descriptions>
              {readOnly && (
                <Alert
                  className="mt-3"
                  type="error"
                  showIcon
                  message="Gói đã hết hạn, hệ thống đang ở chế độ chỉ đọc"
                  description="Vẫn xem được dữ liệu; các thao tác tạo, sửa, xóa mở lại ngay sau khi gia hạn hoặc chuyển gói."
                />
              )}
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <UsageCard limits={subscription.limits} usage={subscription.usage} />
          </Col>
          <Col xs={24}>
            <PlanActions
              subscription={subscription}
              plans={plans}
              isAdmin={isAdmin}
              onChanged={() => void refresh()}
            />
          </Col>
        </Row>
      )}

      {plansError ? (
        <Alert type="error" showIcon message={plansError} />
      ) : (
        <PlanComparisonTable plans={plans} currentPlan={subscription?.plan ?? null} loading={plansLoading} />
      )}

      {/* Lịch sử giao dịch chỉ ADMIN xem được (GET /api/auth/payments yêu cầu vai trò ADMIN) */}
      {isAdmin && <PaymentHistoryTable />}
    </div>
  )
}
