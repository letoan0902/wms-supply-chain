import { Button, Card, Descriptions, Result, Spin, Tag } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage, getErrorStatus } from '../../services/api'
import { formatCurrency, formatDateTime } from '../../utils/format'
import { cancelPayment, fetchPayment } from './paymentApi'
import {
  isCancelReturn,
  parseOrderCode,
  PAYMENT_STATUS_COLORS,
  PAYMENT_STATUS_LABELS,
  POLL_INTERVAL_MS,
  POLL_TIMEOUT_MS,
  shouldKeepPolling,
} from './paymentLogic'
import { CYCLE_LABELS, effectiveExpiry, formatDate, PLAN_LABELS } from './subscriptionFormat'
import { useSubscription } from './subscriptionStore'
import type { PaymentInfo } from './types'

type Phase = 'checking' | 'paid' | 'cancelled' | 'expired' | 'pending-timeout' | 'error'

/** Lỗi phía người dùng (400, 403, 404...) thì dừng hỏi lại; lỗi mạng hoặc máy chủ thì hỏi lại tới hết thời gian. */
function isFinalError(error: unknown): boolean {
  const status = getErrorStatus(error)
  return status !== undefined && status >= 400 && status < 500 && status !== 408 && status !== 429
}

function finalErrorMessage(error: unknown): string {
  if (getErrorStatus(error) === 404) {
    return 'Không tìm thấy giao dịch này trong doanh nghiệp của bạn. Kiểm tra lại mã đơn hoặc xem lịch sử giao dịch.'
  }
  return getApiErrorMessage(error, 'Không kiểm tra được trạng thái giao dịch')
}

/**
 * Trang kết quả thanh toán (PAYOS_RETURN_URL và PAYOS_CANCEL_URL trỏ về đây). Đọc `orderCode` trên địa chỉ, hỏi lại
 * GET /api/auth/payments/{orderCode} mỗi 2 giây trong tối đa 60 giây. Quay về từ nút hủy thì gọi API hủy trước.
 */
export default function PaymentResultPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const orderCode = useMemo(() => parseOrderCode(searchParams), [searchParams])
  const cancelReturn = useMemo(() => isCancelReturn(searchParams), [searchParams])
  const { subscription, refresh } = useSubscription()

  const [phase, setPhase] = useState<Phase>('checking')
  const [payment, setPayment] = useState<PaymentInfo | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [runKey, setRunKey] = useState(0)

  useEffect(() => {
    if (orderCode === null) return undefined
    let stopped = false
    let timer: ReturnType<typeof setTimeout> | undefined
    const startedAt = Date.now()

    const applyStatus = (current: PaymentInfo): boolean => {
      setPayment(current)
      if (current.status === 'PAID') {
        setPhase('paid')
        // Tải lại thuê bao: gói mới có hiệu lực, chế độ chỉ đọc tắt ngay
        void refresh()
        return true
      }
      if (current.status === 'CANCELLED') {
        setPhase('cancelled')
        return true
      }
      if (current.status === 'EXPIRED') {
        setPhase('expired')
        return true
      }
      return false
    }

    const check = async () => {
      try {
        const current = await fetchPayment(orderCode)
        if (stopped || applyStatus(current)) return
        if (shouldKeepPolling(current.status, Date.now() - startedAt, POLL_TIMEOUT_MS)) {
          timer = setTimeout(() => void check(), POLL_INTERVAL_MS)
        } else {
          setPhase('pending-timeout')
        }
      } catch (err) {
        if (stopped) return
        if (isFinalError(err)) {
          setErrorMessage(finalErrorMessage(err))
          setPhase('error')
        } else if (Date.now() - startedAt < POLL_TIMEOUT_MS) {
          timer = setTimeout(() => void check(), POLL_INTERVAL_MS)
        } else {
          setErrorMessage(getApiErrorMessage(err, 'Không kết nối được máy chủ'))
          setPhase('pending-timeout')
        }
      }
    }

    const start = async () => {
      // Quay về từ nút hủy của PayOS: đóng giao dịch PENDING. Đã PAID, hết hạn hoặc đã hủy thì máy chủ trả 409,
      // khi đó hỏi lại trạng thái thật để hiển thị.
      if (cancelReturn && runKey === 0) {
        try {
          const cancelled = await cancelPayment(orderCode)
          if (stopped || applyStatus(cancelled)) return
        } catch (err) {
          if (stopped) return
          if (getErrorStatus(err) !== 409 && isFinalError(err)) {
            setErrorMessage(finalErrorMessage(err))
            setPhase('error')
            return
          }
        }
      }
      await check()
    }

    void start()
    return () => {
      stopped = true
      if (timer) clearTimeout(timer)
    }
  }, [orderCode, cancelReturn, runKey, refresh])

  const checkAgain = () => {
    setErrorMessage(null)
    setPhase('checking')
    setRunKey((key) => key + 1)
  }

  const goToSubscription = (
    <Button type="primary" key="subscription" onClick={() => navigate('/subscription')}>
      Về trang gói dịch vụ
    </Button>
  )

  if (orderCode === null) {
    return (
      <Card data-read-only-allowed>
        <Result
          status="error"
          title="Địa chỉ không có mã đơn hợp lệ"
          subTitle="Trang kết quả cần tham số orderCode do cổng thanh toán gửi về. Xem trạng thái giao dịch trong lịch sử giao dịch."
          extra={[goToSubscription]}
        />
      </Card>
    )
  }

  const details = payment && (
    <Descriptions column={1} size="small" bordered className="mx-auto max-w-xl text-left">
      <Descriptions.Item label="Mã đơn">
        <span className="font-mono">{payment.orderCode}</span>
      </Descriptions.Item>
      <Descriptions.Item label="Gói">
        {PLAN_LABELS[payment.plan] ?? payment.plan} ({CYCLE_LABELS[payment.cycle] ?? payment.cycle})
      </Descriptions.Item>
      <Descriptions.Item label="Số tiền">{formatCurrency(payment.amount)}</Descriptions.Item>
      <Descriptions.Item label="Trạng thái">
        <Tag color={PAYMENT_STATUS_COLORS[payment.status] ?? 'default'}>
          {PAYMENT_STATUS_LABELS[payment.status] ?? payment.status}
        </Tag>
      </Descriptions.Item>
      {payment.paidAt && <Descriptions.Item label="Thanh toán lúc">{formatDateTime(payment.paidAt)}</Descriptions.Item>}
      {payment.reference && <Descriptions.Item label="Mã giao dịch">{payment.reference}</Descriptions.Item>}
    </Descriptions>
  )

  let result
  switch (phase) {
    case 'paid':
      result = (
        <Result
          status="success"
          title="Thanh toán thành công"
          subTitle={
            subscription && subscription.plan === 'PRO' && !subscription.readOnly
              ? `Gói Chuyên Nghiệp có hiệu lực đến ${formatDate(effectiveExpiry(subscription))}. Các thao tác tạo, sửa, xóa đã mở lại.`
              : 'Gói dịch vụ đã được kích hoạt. Thông tin gói đang được tải lại.'
          }
          extra={[goToSubscription]}
        />
      )
      break
    case 'cancelled':
      result = (
        <Result
          status="info"
          title="Giao dịch đã bị hủy"
          subTitle="Không có khoản tiền nào được ghi nhận cho giao dịch này. Có thể tạo giao dịch mới trên trang gói dịch vụ."
          extra={[goToSubscription]}
        />
      )
      break
    case 'expired':
      result = (
        <Result
          status="warning"
          title="Liên kết thanh toán đã hết hạn"
          subTitle="Giao dịch quá 15 phút chưa được thanh toán. Hãy tạo giao dịch mới trên trang gói dịch vụ."
          extra={[goToSubscription]}
        />
      )
      break
    case 'pending-timeout':
      result = (
        <Result
          status="warning"
          title="Đang chờ ngân hàng xác nhận"
          subTitle={
            errorMessage ??
            'Hệ thống chưa nhận được xác nhận thanh toán. Nếu đã chuyển khoản, vui lòng đợi thêm rồi kiểm tra lại.'
          }
          extra={[
            <Button type="primary" key="again" onClick={checkAgain}>
              Kiểm tra lại
            </Button>,
            <Button key="subscription" onClick={() => navigate('/subscription')}>
              Về trang gói dịch vụ
            </Button>,
          ]}
        />
      )
      break
    case 'error':
      result = (
        <Result
          status="error"
          title="Không kiểm tra được giao dịch"
          subTitle={errorMessage}
          extra={[goToSubscription]}
        />
      )
      break
    default:
      result = (
        <Result
          icon={<Spin size="large" />}
          title={cancelReturn ? 'Đang hủy giao dịch...' : 'Đang kiểm tra kết quả thanh toán...'}
          subTitle="Hệ thống hỏi lại trạng thái giao dịch mỗi 2 giây, tối đa 60 giây."
        />
      )
  }

  return (
    <Card data-read-only-allowed>
      {result}
      {details}
    </Card>
  )
}
