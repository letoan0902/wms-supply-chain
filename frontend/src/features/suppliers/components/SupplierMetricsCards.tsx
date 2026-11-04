import {
  AlertOutlined,
  CheckCircleOutlined,
  DollarCircleOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { Card, Col, Row, Skeleton, Statistic } from 'antd'
import type { SupplierDebtSummary } from '../types'
import { formatCurrency, formatNumber } from '../../../utils/format'

interface Props {
  summary: SupplierDebtSummary | null
  loading: boolean
}

export default function SupplierMetricsCards({ summary, loading }: Props) {
  if (loading || !summary) {
    return (
      <Row gutter={[16, 16]}>
        {[1, 2, 3, 4].map((i) => (
          <Col xs={24} sm={12} lg={6} key={i}>
            <Card variant="borderless" className="shadow-xs">
              <Skeleton active paragraph={{ rows: 2 }} />
            </Card>
          </Col>
        ))}
      </Row>
    )
  }

  const inactiveCount = summary.totalSuppliers - summary.activeSuppliers
  const activePercent =
    summary.totalSuppliers > 0
      ? Math.round((summary.activeSuppliers / summary.totalSuppliers) * 100)
      : 100

  return (
    <Row gutter={[16, 16]}>
      {/* 1. Tổng số nhà cung cấp */}
      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Tổng số Nhà cung cấp</span>}
              value={formatNumber(summary.totalSuppliers)}
              suffix={<span className="text-xs text-gray-400">đối tác</span>}
              valueStyle={{ fontWeight: 600, color: '#1677ff' }}
            />
            <div className="p-3 bg-blue-50 rounded-xl text-blue-600 text-xl">
              <ShopOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500 flex items-center gap-1">
            <CheckCircleOutlined className="text-green-500" />
            <span>
              <strong>{summary.activeSuppliers}</strong> đang giao dịch / {inactiveCount} ngưng
            </span>
          </div>
        </Card>
      </Col>

      {/* 2. Tổng công nợ phải trả */}
      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Tổng công nợ phải trả</span>}
              value={formatCurrency(summary.totalDebt)}
              valueStyle={{ fontWeight: 600, color: '#fa8c16' }}
            />
            <div className="p-3 bg-amber-50 rounded-xl text-amber-600 text-xl">
              <DollarCircleOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            Nghĩa vụ công nợ thanh toán với toàn bộ NCC (FR09)
          </div>
        </Card>
      </Col>

      {/* 3. Vượt hạn mức tín dụng */}
      <Col xs={24} sm={12} lg={6}>
        <Card
          variant="borderless"
          className={`shadow-xs hover:shadow-md transition-shadow ${
            summary.overLimitSuppliers > 0 ? 'border-l-4 border-l-red-500' : ''
          }`}
        >
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Cảnh báo vượt hạn mức</span>}
              value={summary.overLimitSuppliers}
              suffix={<span className="text-xs text-gray-400">NCC</span>}
              valueStyle={{
                fontWeight: 600,
                color: summary.overLimitSuppliers > 0 ? '#ff4d4f' : '#52c41a',
              }}
            />
            <div
              className={`p-3 rounded-xl text-xl ${
                summary.overLimitSuppliers > 0
                  ? 'bg-red-50 text-red-600'
                  : 'bg-green-50 text-green-600'
              }`}
            >
              <AlertOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            {summary.overLimitSuppliers > 0 ? (
              <span className="text-red-500 font-medium">
                Cần ưu tiên thanh toán để tránh bị ngưng cung cấp
              </span>
            ) : (
              <span className="text-green-600">Công nợ tất cả NCC trong ngưỡng an toàn</span>
            )}
          </div>
        </Card>
      </Col>

      {/* 4. Tỷ lệ đối tác hoạt động */}
      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Đối tác sẵn sàng giao dịch</span>}
              value={activePercent}
              suffix={<span className="text-xs text-gray-400">%</span>}
              valueStyle={{ fontWeight: 600, color: '#52c41a' }}
            />
            <div className="p-3 bg-emerald-50 rounded-xl text-emerald-600 text-xl">
              <CheckCircleOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            Đang duy trì quan hệ cung ứng hàng hóa ổn định
          </div>
        </Card>
      </Col>
    </Row>
  )
}
