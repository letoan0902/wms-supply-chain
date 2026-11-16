import { Tag } from 'antd'
import {
  COMPANY_STATUS_COLORS,
  COMPANY_STATUS_LABELS,
  enumColor,
  enumLabel,
  PAYMENT_STATUS_COLORS,
  PAYMENT_STATUS_LABELS,
  SUBSCRIPTION_STATUS_COLORS,
  SUBSCRIPTION_STATUS_LABELS,
} from '../platformFormat'

interface StatusProps {
  status: string | null | undefined
}

/** Trạng thái doanh nghiệp: Đang hoạt động, Chờ kích hoạt, Tạm khóa. */
export function CompanyStatusTag({ status }: StatusProps) {
  return (
    <Tag color={enumColor(COMPANY_STATUS_COLORS, status)} className="m-0">
      {enumLabel(COMPANY_STATUS_LABELS, status)}
    </Tag>
  )
}

/** Trạng thái thuê bao; chưa có thuê bao thì hiện gạch ngang. */
export function SubscriptionStatusTag({ status }: StatusProps) {
  if (!status) return <span className="text-slate-400">—</span>
  return (
    <Tag color={enumColor(SUBSCRIPTION_STATUS_COLORS, status)} className="m-0">
      {enumLabel(SUBSCRIPTION_STATUS_LABELS, status)}
    </Tag>
  )
}

/** Trạng thái thanh toán, cùng nhãn và màu với lịch sử giao dịch trên trang Gói dịch vụ. */
export function PaymentStatusTag({ status }: StatusProps) {
  return (
    <Tag color={enumColor(PAYMENT_STATUS_COLORS, status)} className="m-0">
      {enumLabel(PAYMENT_STATUS_LABELS, status)}
    </Tag>
  )
}
