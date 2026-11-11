import { Alert, App, Button, Card, Divider, Radio, Typography } from 'antd'
import { useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency } from '../../../utils/format'
import { createPayment } from '../paymentApi'
import { isSafeCheckoutUrl } from '../paymentLogic'
import { downgradeToFree } from '../subscriptionApi'
import { canDowngradeToFree, CYCLE_LABELS, formatLimit } from '../subscriptionFormat'
import type { BillingCycle, PlanInfo, SubscriptionInfo } from '../types'

interface PlanActionsProps {
  subscription: SubscriptionInfo
  plans: PlanInfo[]
  isAdmin: boolean
  /** Tải lại thuê bao sau khi đổi gói */
  onChanged: () => void
}

/** Thao tác đổi gói: nâng cấp hoặc gia hạn gói Chuyên Nghiệp, chuyển về gói Khởi Đầu. Chỉ ADMIN thao tác được. */
export default function PlanActions({ subscription, plans, isAdmin, onChanged }: PlanActionsProps) {
  const { message, modal } = App.useApp()
  const [cycle, setCycle] = useState<BillingCycle>('MONTHLY')
  const [downgrading, setDowngrading] = useState(false)
  const [paying, setPaying] = useState(false)

  if (!isAdmin) {
    return (
      <Card title="Đổi gói dịch vụ" className="h-full">
        <Alert type="info" showIcon message="Liên hệ quản trị viên của doanh nghiệp" />
      </Card>
    )
  }

  const proPlan = plans.find((plan) => plan.code === 'PRO') ?? null
  const freePlan = plans.find((plan) => plan.code === 'FREE') ?? null
  const isRenewal = subscription.plan === 'PRO'
  const amount = proPlan ? (cycle === 'YEARLY' ? proPlan.priceYearly : proPlan.priceMonthly) : null
  const days = cycle === 'YEARLY' ? 365 : 30

  // Tạo giao dịch rồi chuyển trình duyệt sang trang thanh toán; giữ trạng thái đang xử lý cho tới khi rời trang
  const pay = async () => {
    setPaying(true)
    try {
      const payment = await createPayment({ plan: 'PRO', cycle })
      if (!isSafeCheckoutUrl(payment.checkoutUrl)) {
        message.error('Máy chủ không trả về liên kết thanh toán hợp lệ')
        setPaying(false)
        return
      }
      window.location.assign(payment.checkoutUrl)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không tạo được giao dịch thanh toán, vui lòng thử lại sau'))
      setPaying(false)
    }
  }

  const confirmDowngrade = () => {
    modal.confirm({
      title: 'Chuyển về gói Khởi Đầu?',
      content: (
        <div className="flex flex-col gap-2">
          <span>
            Gói Khởi Đầu miễn phí, không thời hạn, với giới hạn{' '}
            {freePlan
              ? `${formatLimit(freePlan.maxUsers)} người dùng, ${formatLimit(freePlan.maxWarehouses)} kho, ${formatLimit(freePlan.maxSkus)} SKU, ${formatLimit(freePlan.maxAiRequests)} lượt AI mỗi tháng`
              : 'thấp hơn gói hiện tại'}
            .
          </span>
          <span>Dữ liệu đã có được giữ nguyên, chỉ việc tạo thêm bị giới hạn theo gói mới.</span>
        </div>
      ),
      okText: 'Chuyển về gói Khởi Đầu',
      cancelText: 'Hủy',
      onOk: async () => {
        setDowngrading(true)
        try {
          await downgradeToFree()
          message.success('Đã chuyển về gói Khởi Đầu')
          onChanged()
        } catch (err) {
          message.error(getApiErrorMessage(err, 'Không chuyển được về gói Khởi Đầu'))
        } finally {
          setDowngrading(false)
        }
      },
    })
  }

  return (
    <Card title="Đổi gói dịch vụ" className="h-full">
      <Typography.Title level={5} className="!mt-0">
        {isRenewal ? 'Gia hạn gói Chuyên Nghiệp' : 'Nâng cấp lên gói Chuyên Nghiệp'}
      </Typography.Title>
      {proPlan ? (
        <>
          <Radio.Group
            value={cycle}
            onChange={(event) => setCycle(event.target.value as BillingCycle)}
            optionType="button"
            buttonStyle="solid"
            options={[
              { value: 'MONTHLY', label: CYCLE_LABELS.MONTHLY },
              { value: 'YEARLY', label: CYCLE_LABELS.YEARLY },
            ]}
          />
          <div className="mt-3">
            <Typography.Text type="secondary">Số tiền thanh toán: </Typography.Text>
            <Typography.Text strong className="text-lg">
              {amount !== null ? formatCurrency(amount) : '—'}
            </Typography.Text>
          </div>
          <Typography.Paragraph type="secondary" className="mt-1 text-xs">
            Mỗi lần thanh toán cộng thêm {days} ngày, tính từ hôm nay hoặc từ ngày hết hạn hiện tại nếu gói còn hiệu
            lực, nên thanh toán sớm không mất ngày còn lại. Số tiền cuối cùng do máy chủ tính khi tạo giao dịch; liên
            kết thanh toán có hiệu lực 15 phút.
          </Typography.Paragraph>
          <Button type="primary" size="large" onClick={pay} loading={paying} disabled={paying}>
            {isRenewal ? 'Thanh toán gia hạn' : 'Thanh toán nâng cấp'}
            {amount !== null ? ` ${formatCurrency(amount)}` : ''}
          </Button>
        </>
      ) : (
        <Alert type="warning" showIcon message="Chưa tải được giá gói Chuyên Nghiệp" />
      )}

      {canDowngradeToFree(subscription) && (
        <>
          <Divider />
          <Typography.Paragraph type="secondary">
            Không muốn trả phí? Có thể chuyển về gói Khởi Đầu miễn phí với giới hạn thấp hơn.
          </Typography.Paragraph>
          <Button onClick={confirmDowngrade} loading={downgrading}>
            Chuyển về gói Khởi Đầu
          </Button>
        </>
      )}
    </Card>
  )
}
