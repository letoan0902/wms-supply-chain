import { Progress, Typography } from 'antd'
import { formatLimit, isLimitReached, usagePercent } from '../../subscription/subscriptionFormat'
import type { PlatformCompanyLimits, PlatformCompanyUsage } from '../platformApi'
import { formatNumber } from '../../../utils/format'

/** Máy chủ không đọc được số liệu của mục này (khác với 0). */
const NO_DATA = 'Không có dữ liệu'

interface Props {
  limits: PlatformCompanyLimits | null
  usage: PlatformCompanyUsage | null
}

/** Mức dùng của một doanh nghiệp so với giới hạn gói: người dùng, kho, SKU, lượt AI trong tháng, quyền API key. */
export default function CompanyUsageList({ limits, usage }: Props) {
  if (!limits && !usage) {
    return <Typography.Text type="secondary" className="text-xs">Doanh nghiệp chưa có thuê bao nên chưa có giới hạn và mức dùng.</Typography.Text>
  }

  const rows = [
    { key: 'users', label: 'Người dùng', used: usage?.users, max: limits?.maxUsers },
    { key: 'warehouses', label: 'Kho', used: usage?.warehouses, max: limits?.maxWarehouses },
    { key: 'skus', label: 'SKU', used: usage?.skus, max: limits?.maxSkus },
    { key: 'ai', label: 'Lượt AI trong tháng', used: usage?.aiRequests, max: limits?.maxAiRequests },
  ]

  return (
    <div className="flex flex-col gap-3">
      {rows.map((row) => {
        const noData = row.used === null || row.used === undefined
        const percent = usagePercent(row.used, row.max)
        const reached = isLimitReached(row.used, row.max)
        return (
          <div key={row.key}>
            <div className="flex items-baseline justify-between gap-2 text-sm">
              <span className="text-slate-700">{row.label}</span>
              <Typography.Text type={noData ? 'secondary' : reached ? 'danger' : undefined} className="tabular-nums">
                {noData ? NO_DATA : formatNumber(Number(row.used))} / {formatLimit(row.max)}
              </Typography.Text>
            </div>
            {percent !== null && (
              <Progress percent={percent} status={reached ? 'exception' : 'normal'} showInfo={false} size="small" />
            )}
          </div>
        )
      })}
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span className="text-slate-700">Quyền dùng API key</span>
        <span className="text-slate-900">
          {limits?.apiAccess === undefined || limits?.apiAccess === null ? NO_DATA : limits.apiAccess ? 'Có' : 'Không'}
        </span>
      </div>
    </div>
  )
}
