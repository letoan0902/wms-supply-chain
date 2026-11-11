import { Card, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ReactNode } from 'react'
import { formatCurrency } from '../../../utils/format'
import type { PlanInfo } from '../types'
import { formatLimit } from '../subscriptionFormat'

const PLAN_ORDER = ['FREE', 'TRIAL', 'PRO']

interface FeatureRow {
  key: string
  feature: string
  render: (plan: PlanInfo) => ReactNode
}

function formatPrice(value: number | null): string {
  if (value === null || value === undefined) return '—'
  return value === 0 ? 'Miễn phí' : formatCurrency(value)
}

const FEATURE_ROWS: FeatureRow[] = [
  { key: 'monthly', feature: 'Giá theo tháng', render: (plan) => formatPrice(plan.priceMonthly) },
  { key: 'yearly', feature: 'Giá theo năm', render: (plan) => formatPrice(plan.priceYearly) },
  { key: 'users', feature: 'Số người dùng', render: (plan) => formatLimit(plan.maxUsers) },
  { key: 'warehouses', feature: 'Số kho', render: (plan) => formatLimit(plan.maxWarehouses) },
  { key: 'skus', feature: 'Số SKU', render: (plan) => formatLimit(plan.maxSkus) },
  { key: 'ai', feature: 'Lượt AI mỗi tháng', render: (plan) => formatLimit(plan.maxAiRequests) },
  {
    key: 'api',
    feature: 'API key cho hệ thống ngoài',
    render: (plan) => (plan.apiAccess === null ? '—' : plan.apiAccess ? 'Có' : 'Không'),
  },
  {
    key: 'trial',
    feature: 'Thời gian dùng thử',
    render: (plan) => (plan.trialDays ? `${plan.trialDays} ngày` : '—'),
  },
]

interface PlanComparisonTableProps {
  plans: PlanInfo[]
  currentPlan: string | null
  loading: boolean
}

/** Bảng so sánh các gói, số liệu lấy từ GET /api/auth/plans. */
export default function PlanComparisonTable({ plans, currentPlan, loading }: PlanComparisonTableProps) {
  const sorted = [...plans].sort((a, b) => {
    const ia = PLAN_ORDER.indexOf(a.code)
    const ib = PLAN_ORDER.indexOf(b.code)
    return (ia === -1 ? PLAN_ORDER.length : ia) - (ib === -1 ? PLAN_ORDER.length : ib)
  })

  const columns: ColumnsType<FeatureRow> = [
    { title: 'Tính năng', dataIndex: 'feature', key: 'feature', fixed: 'left', width: 200 },
    ...sorted.map((plan) => ({
      key: plan.code,
      align: 'center' as const,
      title: (
        <div className="flex flex-col items-center gap-1">
          <span>{plan.name}</span>
          {plan.code === currentPlan && <Tag color="blue">Gói hiện tại</Tag>}
        </div>
      ),
      onCell: () => ({ className: plan.code === currentPlan ? 'bg-blue-50' : undefined }),
      render: (_: unknown, row: FeatureRow) => row.render(plan),
    })),
  ]

  return (
    <Card title="So sánh các gói">
      <Table<FeatureRow>
        rowKey="key"
        columns={columns}
        dataSource={FEATURE_ROWS}
        loading={loading}
        pagination={false}
        size="middle"
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Chưa tải được bảng giá' }}
      />
    </Card>
  )
}
