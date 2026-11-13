import { Alert, Button, Card, Descriptions, Space, Spin, Tag, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage, getErrorStatus } from '../../services/api'
import { formatCurrency, formatDateTime } from '../../utils/format'
import { cancelPayment, fetchPayment, mockConfirmPayment } from './paymentApi'
import { parseOrderCode, PAYMENT_STATUS_COLORS, PAYMENT_STATUS_LABELS } from './paymentLogic'
import { CYCLE_LABELS, PLAN_LABELS } from './subscriptionFormat'
import type { PaymentInfo } from './types'

type Action = 'confirm' | 'cancel' | null

/**
 * Trang thanh toán giả lập `/payment/mock?orderCode=...`: máy chủ trả liên kết tới đây thay cho trang PayOS khi
 * PAYOS_MOCK=true (chưa cấu hình khóa PayOS). Xác nhận gọi mock-confirm (máy chủ xử lý như webhook hợp lệ), hủy
 * gọi API hủy, rồi chuyển sang trang kết quả.
 */
export default function MockCheckoutPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const orderCode = useMemo(() => parseOrderCode(searchParams), [searchParams])
  const [payment, setPayment] = useState<PaymentInfo | null>(null)
  const [loading, setLoading] = useState(orderCode !== null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [action, setAction] = useState<Action>(null)

  useEffect(() => {
    if (orderCode === null) return undefined
    let ignore = false
    fetchPayment(orderCode)
      .then((result) => {
        if (!ignore) setPayment(result)
      })
      .catch((err: unknown) => {
        if (ignore) return
        setLoadError(
          getErrorStatus(err) === 404
            ? 'Không tìm thấy giao dịch này trong doanh nghiệp của bạn.'
            : getApiErrorMessage(err, 'Không tải được thông tin giao dịch'),
        )
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [orderCode])

  const goToResult = (code: number) => navigate(`/payment/result?orderCode=${code}`, { replace: true })

  const run = async (kind: Exclude<Action, null>) => {
    if (orderCode === null) return
    setAction(kind)
    setActionError(null)
    try {
      if (kind === 'confirm') {
        await mockConfirmPayment(orderCode)
      } else {
        await cancelPayment(orderCode)
      }
      goToResult(orderCode)
    } catch (err) {
      const status = getErrorStatus(err)
      setActionError(
        kind === 'confirm' && status === 404
          ? 'Máy chủ không bật thanh toán giả lập (PAYOS_MOCK=false) hoặc không tìm thấy giao dịch.'
          : getApiErrorMessage(err, kind === 'confirm' ? 'Không xác nhận được thanh toán' : 'Không hủy được giao dịch'),
      )
      setAction(null)
    }
  }

  const isPending = payment?.status === 'PENDING'

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 p-4" data-read-only-allowed>
      <Card className="w-full max-w-lg" title="Thanh toán giả lập">
        {orderCode === null ? (
          <Alert type="error" showIcon message="Địa chỉ không có mã đơn hợp lệ" />
        ) : loading ? (
          <div className="flex flex-col items-center gap-2 py-10">
            <Spin />
            <Typography.Text type="secondary">Đang tải giao dịch...</Typography.Text>
          </div>
        ) : loadError ? (
          <Alert type="error" showIcon message={loadError} />
        ) : payment ? (
          <div className="flex flex-col gap-4">
            <div className="flex aspect-square w-full max-w-60 items-center justify-center self-center rounded-lg border-2 border-dashed border-slate-300 bg-white p-4 text-center text-sm text-slate-500">
              Mã QR giả lập: đây là trang thanh toán mô phỏng, dùng khi máy chủ chưa cấu hình khóa PayOS. Không có
              khoản tiền thật nào được chuyển.
            </div>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Mã đơn">
                <span className="font-mono">{payment.orderCode}</span>
              </Descriptions.Item>
              <Descriptions.Item label="Gói">
                {PLAN_LABELS[payment.plan] ?? payment.plan} ({CYCLE_LABELS[payment.cycle] ?? payment.cycle})
              </Descriptions.Item>
              <Descriptions.Item label="Số tiền">
                <Typography.Text strong>{formatCurrency(payment.amount)}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <Tag color={PAYMENT_STATUS_COLORS[payment.status] ?? 'default'}>
                  {PAYMENT_STATUS_LABELS[payment.status] ?? payment.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Liên kết hết hạn">{formatDateTime(payment.expiresAt)}</Descriptions.Item>
            </Descriptions>
            {actionError && <Alert type="error" showIcon message={actionError} />}
            {isPending ? (
              <Space className="justify-end" wrap>
                <Button onClick={() => void run('cancel')} loading={action === 'cancel'} disabled={action !== null}>
                  Hủy
                </Button>
                <Button
                  type="primary"
                  onClick={() => void run('confirm')}
                  loading={action === 'confirm'}
                  disabled={action !== null}
                >
                  Xác nhận đã thanh toán
                </Button>
              </Space>
            ) : (
              <Alert
                type="info"
                showIcon
                message="Giao dịch không còn chờ thanh toán"
                description={<Link to={`/payment/result?orderCode=${payment.orderCode}`}>Xem kết quả giao dịch</Link>}
              />
            )}
          </div>
        ) : null}
        <div className="mt-4 text-right">
          <Link to="/subscription">Về trang gói dịch vụ</Link>
        </div>
      </Card>
    </div>
  )
}
