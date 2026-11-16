import { Card, Empty } from 'antd'
import type { ReactNode } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Label,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatCurrency, formatNumber } from '../../../utils/format'
import type { PlatformOverview } from '../platformApi'
import { formatMonthLong, formatMonthShort, PLAN_COLORS, PLAN_LABELS, PLAN_ORDER } from '../platformFormat'

const REGISTRATION_COLOR = '#1890ff'
const REVENUE_COLOR = '#52c41a'
const CHART_HEIGHT = 260

const compactFormatter = new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 })

const renderLegendText = (value: ReactNode) => <span className="text-neutral-600 text-xs">{value}</span>

function ChartCard({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <Card
      variant="borderless"
      className="shadow-none border border-slate-200/80 bg-white h-full"
      title={
        <div className="py-1 min-w-0">
          <div className="font-semibold text-slate-900 text-sm">{title}</div>
          <div className="text-[11px] font-normal text-slate-500">{subtitle}</div>
        </div>
      }
    >
      {children}
    </Card>
  )
}

/** Số doanh nghiệp đăng ký mới trong 6 tháng gần nhất (tháng theo giờ Việt Nam). */
export function RegistrationsChart({ data }: { data: PlatformOverview['registrationsByMonth'] }) {
  const points = data.map((point) => ({ ...point, label: formatMonthShort(point.month) }))
  const hasData = points.some((point) => point.count > 0)
  return (
    <ChartCard title="Doanh nghiệp đăng ký mới" subtitle="6 tháng gần nhất, theo ngày đăng ký">
      {hasData ? (
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <BarChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
            <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#64748b' }} />
            <YAxis tick={{ fontSize: 11, fill: '#64748b' }} width={40} allowDecimals={false} />
            <RechartsTooltip
              labelFormatter={(_label, payload) => formatMonthLong(String(payload?.[0]?.payload?.month ?? ''))}
              formatter={(value) => [`${formatNumber(Number(value))} doanh nghiệp`, 'Đăng ký mới']}
            />
            <Bar dataKey="count" name="Đăng ký mới" fill={REGISTRATION_COLOR} radius={[3, 3, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      ) : (
        <Empty description="Chưa có doanh nghiệp đăng ký trong 6 tháng gần nhất" />
      )}
    </ChartCard>
  )
}

/** Doanh thu (thanh toán thành công, theo thời điểm thanh toán) trong 6 tháng gần nhất. */
export function RevenueChart({ data }: { data: PlatformOverview['revenueByMonth'] }) {
  const points = data.map((point) => ({ ...point, label: formatMonthShort(point.month) }))
  const hasData = points.some((point) => point.amount > 0)
  return (
    <ChartCard title="Doanh thu theo tháng" subtitle="6 tháng gần nhất, chỉ tính thanh toán thành công">
      {hasData ? (
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <BarChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
            <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#64748b' }} />
            <YAxis
              tick={{ fontSize: 11, fill: '#64748b' }}
              width={56}
              tickFormatter={(value: number) => compactFormatter.format(value)}
            />
            <RechartsTooltip
              labelFormatter={(_label, payload) => formatMonthLong(String(payload?.[0]?.payload?.month ?? ''))}
              formatter={(value) => [formatCurrency(Number(value)), 'Doanh thu']}
            />
            <Bar dataKey="amount" name="Doanh thu" fill={REVENUE_COLOR} radius={[3, 3, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      ) : (
        <Empty description="Chưa có doanh thu trong 6 tháng gần nhất" />
      )}
    </ChartCard>
  )
}

/** Phân bổ doanh nghiệp theo gói đang dùng (kể cả thuê bao đã hết hạn). */
export function PlanDistributionChart({ subscriptions }: { subscriptions: PlatformOverview['subscriptions'] }) {
  const counts: Record<(typeof PLAN_ORDER)[number], number> = {
    FREE: subscriptions.free,
    TRIAL: subscriptions.trial,
    PRO: subscriptions.pro,
  }
  const slices = PLAN_ORDER.map((plan) => ({
    plan,
    name: PLAN_LABELS[plan],
    value: counts[plan],
    fill: PLAN_COLORS[plan],
  })).filter((slice) => slice.value > 0)
  const total = slices.reduce((sum, slice) => sum + slice.value, 0)

  return (
    <ChartCard title="Doanh nghiệp theo gói" subtitle="Gói của thuê bao, kể cả thuê bao đã hết hạn">
      {total > 0 ? (
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <PieChart>
            <Pie
              data={slices}
              dataKey="value"
              nameKey="name"
              innerRadius={58}
              outerRadius={88}
              paddingAngle={2}
              isAnimationActive={false}
            >
              <Label value={formatNumber(total)} position="center" fill="#0f172a" fontSize={14} fontWeight={600} />
            </Pie>
            <RechartsTooltip
              formatter={(value) => {
                const count = Number(value)
                return `${formatNumber(count)} doanh nghiệp (${Math.round((count / total) * 100)}%)`
              }}
            />
            <Legend verticalAlign="bottom" formatter={renderLegendText} />
          </PieChart>
        </ResponsiveContainer>
      ) : (
        <Empty description="Chưa có doanh nghiệp nào có thuê bao" />
      )}
    </ChartCard>
  )
}
