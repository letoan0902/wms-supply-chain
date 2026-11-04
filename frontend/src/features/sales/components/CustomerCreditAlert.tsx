import {
  ExclamationCircleOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Alert, Card, Col, Progress, Row, Space, Tag, Typography } from 'antd'
import type { CustomerCreditCheckResponse } from '../../customers/types'
import { formatCurrency } from '../../../utils/format'

const { Text } = Typography

interface Props {
  creditInfo: CustomerCreditCheckResponse | null
  loading?: boolean
}

export default function CustomerCreditAlert({ creditInfo, loading }: Props) {
  if (!creditInfo) {
    return null
  }

  const {
    customerName,
    customerCode,
    creditLimit,
    currentDebt,
    availableCredit,
    projectedDebt,
    orderAmount,
    isDebtLocked,
    isOverLimit,
    status,
    message,
  } = creditInfo

  // Tỷ lệ công nợ dự kiến so với hạn mức (nếu có hạn mức > 0)
  const usagePercent =
    creditLimit > 0
      ? Math.min(100, Math.round((projectedDebt / creditLimit) * 100))
      : 0

  const getAlertConfig = () => {
    if (isDebtLocked || status === 'BLOCKED_DEBT_LOCKED') {
      return {
        type: 'error' as const,
        icon: <LockOutlined />,
        title: 'KHÁCH HÀNG ĐANG BỊ KHÓA CÔNG NỢ',
        description: message || 'Khách hàng có công nợ quá hạn hoặc nợ xấu. Không thể tạo đơn bán!',
      }
    }
    if (isOverLimit || status === 'BLOCKED_OVER_LIMIT') {
      return {
        type: 'error' as const,
        icon: <ExclamationCircleOutlined />,
        title: 'CẢNH BÁO: VƯỢT HẠN MỨC CÔNG NỢ',
        description: message || 'Giá trị đơn hàng khiến tổng công nợ vượt quá hạn mức cho phép!',
      }
    }
    if (status === 'WARNING_NEAR_LIMIT') {
      return {
        type: 'warning' as const,
        icon: <WarningOutlined />,
        title: 'CẢNH BÁO: SẮP CHẠM HẠN MỨC NỢ (≥ 90%)',
        description: message || 'Công nợ sau đơn hàng này sắp đạt trần hạn mức tín dụng!',
      }
    }
    return {
      type: 'info' as const,
      icon: <SafetyCertificateOutlined />,
      title: 'HẠN MỨC CÔNG NỢ HỢP LỆ',
      description: message || 'Khách hàng đủ điều kiện mua hàng trong hạn mức tín dụng.',
    }
  }

  const alertCfg = getAlertConfig()

  return (
    <Card
      size="small"
      className="mb-4 bg-slate-50 border-slate-200"
      loading={loading}
    >
      <Alert
        message={
          <div className="flex items-center justify-between">
            <span className="font-semibold text-sm">{alertCfg.title}</span>
            <Space>
              <Tag color="blue">{customerCode}</Tag>
              <Text strong>{customerName}</Text>
            </Space>
          </div>
        }
        description={
          <div className="mt-2 space-y-2">
            <div>{alertCfg.description}</div>
            <Row gutter={[16, 8]} className="pt-2 border-t border-slate-200 text-xs">
              <Col xs={12} sm={6}>
                <span className="text-gray-500">Hạn mức nợ tối đa:</span>
                <div className="font-semibold text-slate-800">
                  {creditLimit > 0 ? formatCurrency(creditLimit) : 'Không áp dụng nợ (0 đ)'}
                </div>
              </Col>
              <Col xs={12} sm={6}>
                <span className="text-gray-500">Công nợ hiện tại:</span>
                <div className="font-semibold text-amber-600">
                  {formatCurrency(currentDebt)}
                </div>
              </Col>
              <Col xs={12} sm={6}>
                <span className="text-gray-500">Hạn mức còn lại:</span>
                <div className="font-semibold text-emerald-600">
                  {formatCurrency(availableCredit)}
                </div>
              </Col>
              <Col xs={12} sm={6}>
                <span className="text-gray-500">Dự kiến sau đơn này:</span>
                <div
                  className={`font-semibold ${
                    isOverLimit ? 'text-red-600' : 'text-blue-600'
                  }`}
                >
                  {formatCurrency(projectedDebt)} (Đơn: {formatCurrency(orderAmount)})
                </div>
              </Col>
            </Row>
            {creditLimit > 0 && (
              <div className="pt-1">
                <Progress
                  percent={usagePercent}
                  status={isOverLimit ? 'exception' : usagePercent >= 90 ? 'normal' : 'active'}
                  strokeColor={isOverLimit ? '#ff4d4f' : usagePercent >= 90 ? '#faad14' : '#52c41a'}
                  size="small"
                />
              </div>
            )}
          </div>
        }
        type={alertCfg.type}
        showIcon
      />
    </Card>
  )
}
