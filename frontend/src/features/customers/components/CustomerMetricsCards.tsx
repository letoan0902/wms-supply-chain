import {
  BankOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  LockOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Card, Col, Row, Skeleton, Statistic, Tag } from 'antd'
import type { CustomerDebtSummary } from '../types'

interface CustomerMetricsCardsProps {
  summary: CustomerDebtSummary | null
  loading: boolean
  activeFilter?: 'ALL' | 'OVER_LIMIT' | 'DEBT_LOCKED' | 'ACTIVE'
  onSelectFilter?: (filter: 'ALL' | 'OVER_LIMIT' | 'DEBT_LOCKED' | 'ACTIVE') => void
}

const formatCurrency = (val: number): string =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val)

export default function CustomerMetricsCards({
  summary,
  loading,
  activeFilter = 'ALL',
  onSelectFilter,
}: CustomerMetricsCardsProps) {
  if (loading && !summary) {
    return (
      <Row gutter={[16, 16]} className="mb-6">
        {[1, 2, 3, 4, 5].map((i) => (
          <Col xs={24} sm={12} lg={i === 5 ? 24 : 6} xl={i === 5 ? 6 : 4} key={i}>
            <Card size="small" bordered={false} className="shadow-sm">
              <Skeleton active paragraph={{ rows: 1 }} />
            </Card>
          </Col>
        ))}
      </Row>
    )
  }

  const cards = [
    {
      key: 'ALL' as const,
      title: 'Tổng khách hàng',
      value: summary?.totalCustomers ?? 0,
      prefix: <TeamOutlined className="text-blue-500" />,
      color: 'border-l-4 border-l-blue-500',
      tag: 'Tất cả đối tác',
    },
    {
      key: 'ACTIVE' as const,
      title: 'Đang hợp tác',
      value: summary?.activeCustomers ?? 0,
      prefix: <CheckCircleOutlined className="text-emerald-500" />,
      color: 'border-l-4 border-l-emerald-500',
      tag: `${summary?.totalCustomers ? Math.round(((summary.activeCustomers ?? 0) / summary.totalCustomers) * 100) : 0}% hoạt động`,
    },
    {
      key: 'OVER_LIMIT' as const,
      title: 'Vượt hạn mức nợ (FR14)',
      value: summary?.overLimitCustomers ?? 0,
      prefix: <ExclamationCircleOutlined className="text-amber-500" />,
      color: 'border-l-4 border-l-amber-500',
      tag: (
        <Tag color={(summary?.overLimitCustomers ?? 0) > 0 ? 'volcano' : 'default'} className="m-0">
          {(summary?.overLimitCustomers ?? 0) > 0 ? 'Cảnh báo rủi ro' : 'An toàn'}
        </Tag>
      ),
    },
    {
      key: 'DEBT_LOCKED' as const,
      title: 'Bị khóa nợ (FR14)',
      value: summary?.debtLockedCustomers ?? 0,
      prefix: <LockOutlined className="text-red-500" />,
      color: 'border-l-4 border-l-red-500',
      tag: (
        <Tag color={(summary?.debtLockedCustomers ?? 0) > 0 ? 'error' : 'default'} className="m-0">
          {(summary?.debtLockedCustomers ?? 0) > 0 ? 'Chặn bán hàng' : 'Bình thường'}
        </Tag>
      ),
    },
  ]

  return (
    <div className="mb-6">
      <Row gutter={[16, 16]}>
        {cards.map((c) => {
          const isSelected = activeFilter === c.key
          return (
            <Col xs={24} sm={12} md={6} xl={5} key={c.key}>
              <Card
                size="small"
                hoverable
                onClick={() => onSelectFilter?.(c.key)}
                className={`transition-all shadow-sm ${c.color} ${
                  isSelected ? 'ring-2 ring-blue-500 bg-blue-50/20' : 'bg-white'
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs text-gray-500 font-medium">{c.title}</span>
                  {typeof c.tag === 'string' ? (
                    <span className="text-xs text-gray-400">{c.tag}</span>
                  ) : (
                    c.tag
                  )}
                </div>
                <Statistic
                  value={c.value}
                  prefix={c.prefix}
                  valueStyle={{ fontSize: '1.4rem', fontWeight: 600 }}
                />
              </Card>
            </Col>
          )
        })}

        {/* Tổng công nợ toàn hệ thống */}
        <Col xs={24} sm={24} md={24} xl={4}>
          <Card
            size="small"
            className="shadow-sm border-l-4 border-l-indigo-600 bg-gradient-to-br from-indigo-50/50 to-white"
          >
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs text-indigo-900 font-medium">Tổng nợ phải thu</span>
              <BankOutlined className="text-indigo-600" />
            </div>
            <div className="text-base lg:text-lg font-bold text-indigo-700 truncate">
              {formatCurrency(summary?.totalDebt ?? 0)}
            </div>
            <span className="text-[11px] text-gray-400">Công nợ toàn bộ khách hàng</span>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
