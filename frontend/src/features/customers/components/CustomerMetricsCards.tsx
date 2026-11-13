import {
  BankOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  LockOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Skeleton } from 'antd'
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
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4 mb-6">
        {[1, 2, 3, 4, 5].map((i) => (
          <div key={i} className="bg-white border border-slate-200/80 rounded-lg p-4 shadow-none">
            <Skeleton active paragraph={{ rows: 2 }} />
          </div>
        ))}
      </div>
    )
  }

  const activePercent = summary?.totalCustomers
    ? Math.round(((summary.activeCustomers ?? 0) / summary.totalCustomers) * 100)
    : 0

  const filterCards = [
    {
      key: 'ALL' as const,
      title: 'Tổng khách hàng',
      value: summary?.totalCustomers ?? 0,
      icon: <TeamOutlined className="text-slate-500" />,
      footer: <span className="text-slate-400">Tất cả đối tác</span>,
    },
    {
      key: 'ACTIVE' as const,
      title: 'Đang hợp tác',
      value: summary?.activeCustomers ?? 0,
      icon: <CheckCircleOutlined className="text-emerald-500" />,
      footer: (
        <span className="inline-flex items-center gap-1.5 text-slate-600">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
          <span>{activePercent}% hoạt động</span>
        </span>
      ),
    },
    {
      key: 'OVER_LIMIT' as const,
      title: 'Vượt hạn mức nợ',
      value: summary?.overLimitCustomers ?? 0,
      icon: <ExclamationCircleOutlined className="text-amber-500" />,
      footer:
        (summary?.overLimitCustomers ?? 0) > 0 ? (
          <span className="inline-flex items-center gap-1.5 text-amber-600 font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
            <span>Cảnh báo rủi ro</span>
          </span>
        ) : (
          <span className="inline-flex items-center gap-1.5 text-slate-500">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            <span>Trong hạn mức</span>
          </span>
        ),
    },
    {
      key: 'DEBT_LOCKED' as const,
      title: 'Bị khóa nợ',
      value: summary?.debtLockedCustomers ?? 0,
      icon: <LockOutlined className="text-red-500" />,
      footer:
        (summary?.debtLockedCustomers ?? 0) > 0 ? (
          <span className="inline-flex items-center gap-1.5 text-red-600 font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
            <span>Chặn bán hàng</span>
          </span>
        ) : (
          <span className="inline-flex items-center gap-1.5 text-slate-400">
            <span className="w-1.5 h-1.5 rounded-full bg-slate-300" />
            <span>Bình thường</span>
          </span>
        ),
    },
  ]

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4 mb-6">
      {filterCards.map((c) => {
        const isSelected = activeFilter === c.key
        return (
          <div
            key={c.key}
            onClick={() => onSelectFilter?.(c.key)}
            className={`border rounded-lg p-4 cursor-pointer transition-all flex flex-col justify-between h-full min-h-[112px] ${
              isSelected
                ? 'bg-slate-100 border-slate-200 shadow-2xs'
                : 'bg-white border-slate-200/80 hover:bg-slate-50/70 hover:border-slate-300'
            }`}
          >
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <span
                  className={`text-xs uppercase tracking-wider ${
                    isSelected ? 'font-semibold text-slate-800' : 'font-medium text-slate-500'
                  }`}
                >
                  {c.title}
                </span>
                <span className="text-sm">{c.icon}</span>
              </div>
              <div className="text-2xl font-semibold text-slate-900 tabular-nums">
                {c.value}
              </div>
            </div>
            <div
              className={`mt-3 pt-2 border-t text-xs ${
                isSelected ? 'border-slate-200/80' : 'border-slate-100'
              }`}
            >
              {c.footer}
            </div>
          </div>
        )
      })}

      {/* Thẻ 5: Tổng công nợ toàn hệ thống (cân đối 100% về kích thước và cấu trúc) */}
      <div className="bg-white border border-slate-200/80 rounded-lg p-4 flex flex-col justify-between h-full min-h-[112px]">
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
              Tổng nợ phải thu
            </span>
            <span className="text-sm">
              <BankOutlined className="text-slate-400" />
            </span>
          </div>
          <div className="text-xl font-semibold text-slate-900 tabular-nums truncate" title={formatCurrency(summary?.totalDebt ?? 0)}>
            {formatCurrency(summary?.totalDebt ?? 0)}
          </div>
        </div>
        <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-400 truncate">
          Toàn bộ khách hàng
        </div>
      </div>
    </div>
  )
}
