import { Alert, Button, Card, Empty, Segmented, Typography } from 'antd'
import type { ReactNode } from 'react'
import { useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatCurrency, formatNumber } from '../../../utils/format'
import type { PurchaseSalesFigures, PurchaseSalesTrend } from '../types'
import type { DashboardRequest } from '../useDashboardRequest'

const { Text } = Typography

type Metric = 'amount' | 'count' | 'quantity'

const PURCHASE_COLOR = '#1890ff'
const SALES_COLOR = '#52c41a'

const compactFormatter = new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 })

const renderLegendText = (value: ReactNode) => <span className="text-neutral-600 text-xs">{value}</span>

function pick(figures: PurchaseSalesFigures, metric: Metric): { purchase: number; sales: number } {
  switch (metric) {
    case 'amount':
      return { purchase: Number(figures.purchaseAmount ?? 0), sales: Number(figures.salesAmount ?? 0) }
    case 'quantity':
      return { purchase: Number(figures.purchaseQuantity), sales: Number(figures.salesQuantity) }
    default:
      return { purchase: figures.purchaseOrderCount, sales: figures.salesOrderCount }
  }
}

function formatMetric(value: number, metric: Metric): string {
  if (metric === 'amount') return formatCurrency(value)
  if (metric === 'count') return `${formatNumber(value)} đơn`
  return formatNumber(value)
}

interface Props {
  request: DashboardRequest<PurchaseSalesTrend>
  /** Vai trò được xem số tiền (bảng quyền); backend vẫn là nơi quyết định qua `amountVisible` */
  canSeeAmounts: boolean
  onRetry: () => void
}

/**
 * Biểu đồ cột đôi so sánh mua hàng và bán hàng theo ngày hoặc theo tháng. Chọn xem theo số tiền (chỉ vai trò được xem
 * tài chính), số đơn hoặc số lượng; kỳ không phát sinh vẫn có cột bằng 0 để trục thời gian liền mạch.
 */
export default function PurchaseSalesTrendCard({ request, canSeeAmounts, onRetry }: Props) {
  const { data, error, loading } = request
  const amountAllowed = canSeeAmounts && Boolean(data?.amountVisible)
  const [metric, setMetric] = useState<Metric>(canSeeAmounts ? 'amount' : 'count')
  const activeMetric: Metric = metric === 'amount' && !amountAllowed ? 'count' : metric

  const options = [
    ...(amountAllowed ? [{ label: 'Giá trị', value: 'amount' as const }] : []),
    { label: 'Số đơn', value: 'count' as const },
    { label: 'Số lượng', value: 'quantity' as const },
  ]

  const chartData = (data?.points ?? []).map((point) => ({
    label: point.label,
    ...pick(point.figures, activeMetric),
  }))
  const totals = data ? pick(data.totals, activeMetric) : null
  const hasOrders = Boolean(data && (data.totals.purchaseOrderCount > 0 || data.totals.salesOrderCount > 0))

  return (
    <Card
      variant="borderless"
      className="shadow-none border border-slate-200/80 bg-white h-full"
      loading={loading}
      title={
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 py-1">
          <div className="min-w-0">
            <div className="font-semibold text-slate-900 text-sm">Mua hàng và bán hàng theo thời gian</div>
            {data && (
              <div className="text-[11px] font-normal text-slate-500">
                {data.bucket === 'MONTH' ? 'Gom theo tháng' : 'Gom theo ngày'}, theo ngày đặt hàng
              </div>
            )}
          </div>
          {data && !error && (
            <Segmented
              size="small"
              value={activeMetric}
              onChange={(value) => setMetric(value as Metric)}
              options={options}
            />
          )}
        </div>
      }
    >
      {error ? (
        <Alert
          type="error"
          showIcon
          message={error}
          action={
            <Button size="small" onClick={onRetry}>
              Thử lại
            </Button>
          }
        />
      ) : !data || !hasOrders ? (
        <Empty description="Chưa có đơn mua đã duyệt hoặc đơn bán đã xác nhận trong khoảng thời gian này" />
      ) : (
        <div className="flex flex-col gap-3">
          {totals && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <div className="rounded-md border border-slate-200/80 px-3 py-2">
                <div className="text-[11px] text-slate-500">Mua hàng trong kỳ</div>
                <div className="text-sm font-semibold text-slate-900 tabular-nums break-words">
                  {formatMetric(totals.purchase, activeMetric)}
                </div>
                {activeMetric !== 'count' && (
                  <div className="text-[11px] text-slate-500">{formatNumber(data.totals.purchaseOrderCount)} đơn mua</div>
                )}
              </div>
              <div className="rounded-md border border-slate-200/80 px-3 py-2">
                <div className="text-[11px] text-slate-500">Bán hàng trong kỳ</div>
                <div className="text-sm font-semibold text-slate-900 tabular-nums break-words">
                  {formatMetric(totals.sales, activeMetric)}
                </div>
                {activeMetric !== 'count' && (
                  <div className="text-[11px] text-slate-500">{formatNumber(data.totals.salesOrderCount)} đơn bán</div>
                )}
              </div>
            </div>
          )}
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
              <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#64748b' }} interval="preserveStartEnd" />
              <YAxis
                tick={{ fontSize: 11, fill: '#64748b' }}
                width={56}
                allowDecimals={activeMetric === 'quantity'}
                tickFormatter={(value: number) => compactFormatter.format(value)}
              />
              <RechartsTooltip formatter={(value) => formatMetric(Number(value), activeMetric)} />
              <Legend verticalAlign="bottom" formatter={renderLegendText} />
              <Bar dataKey="purchase" name="Mua hàng" fill={PURCHASE_COLOR} radius={[3, 3, 0, 0]} isAnimationActive={false} />
              <Bar dataKey="sales" name="Bán hàng" fill={SALES_COLOR} radius={[3, 3, 0, 0]} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
          <div className="flex flex-col gap-0.5">
            <Text type="secondary" className="text-[11px]">
              Mua hàng: {data.purchaseDefinition}.
            </Text>
            <Text type="secondary" className="text-[11px]">
              Bán hàng: {data.salesDefinition}.
            </Text>
            {activeMetric === 'quantity' && (
              <Text type="secondary" className="text-[11px]">
                {data.quantityNote}.
              </Text>
            )}
            {!amountAllowed && data.amountNote && (
              <Text type="secondary" className="text-[11px]">
                {data.amountNote}.
              </Text>
            )}
          </div>
        </div>
      )}
    </Card>
  )
}
