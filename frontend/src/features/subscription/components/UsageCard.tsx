import { Card, Progress, Typography } from 'antd'
import type { PlanLimits, PlanUsage } from '../types'
import { formatLimit, formatUsage, isLimitReached, isUnlimited, usagePercent } from '../subscriptionFormat'

interface UsageRow {
  key: string
  label: string
  used: number | null | undefined
  max: number | null | undefined
}

interface UsageCardProps {
  limits: PlanLimits | null
  usage: PlanUsage | null
}

/** Bốn thanh mức dùng so với giới hạn của gói: người dùng, kho, SKU, lượt AI trong tháng. */
export default function UsageCard({ limits, usage }: UsageCardProps) {
  const rows: UsageRow[] = [
    { key: 'users', label: 'Người dùng đang hoạt động', used: usage?.users, max: limits?.maxUsers },
    { key: 'warehouses', label: 'Kho đang hoạt động', used: usage?.warehouses, max: limits?.maxWarehouses },
    { key: 'skus', label: 'SKU đang kinh doanh', used: usage?.skus, max: limits?.maxSkus },
    { key: 'ai', label: 'Lượt AI trong tháng', used: usage?.aiRequests, max: limits?.maxAiRequests },
  ]

  return (
    <Card title="Mức sử dụng" className="h-full">
      <div className="flex flex-col gap-4">
        {rows.map((row) => {
          const percent = usagePercent(row.used, row.max)
          const reached = isLimitReached(row.used, row.max)
          const noData = row.used === null || row.used === undefined
          return (
            <div key={row.key}>
              <div className="flex items-baseline justify-between gap-2">
                <Typography.Text strong>{row.label}</Typography.Text>
                <Typography.Text type={noData ? 'secondary' : reached ? 'danger' : undefined}>
                  {formatUsage(row.used)} / {formatLimit(row.max)}
                </Typography.Text>
              </div>
              {percent !== null ? (
                <Progress
                  percent={percent}
                  status={reached ? 'exception' : 'normal'}
                  showInfo={false}
                  size="small"
                />
              ) : (
                <Typography.Text type="secondary" className="text-xs">
                  {noData
                    ? 'Hệ thống chưa ghi nhận số liệu cho mục này.'
                    : isUnlimited(row.max)
                      ? 'Gói hiện tại không giới hạn mục này.'
                      : 'Chưa có giới hạn cho mục này.'}
                </Typography.Text>
              )}
              {reached && (
                <Typography.Text type="danger" className="text-xs">
                  Đã chạm giới hạn của gói, không thể tạo thêm.
                </Typography.Text>
              )}
            </div>
          )
        })}
      </div>
    </Card>
  )
}
