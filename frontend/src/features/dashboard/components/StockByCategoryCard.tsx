import { Alert, Button, Card, Empty, Tag, Typography } from 'antd'
import type { ReactNode } from 'react'
import { Label, Legend, Pie, PieChart, ResponsiveContainer, Tooltip as RechartsTooltip } from 'recharts'
import { formatNumber } from '../../../utils/format'
import type { StockByCategory, StockCategorySlice } from '../types'
import type { DashboardRequest } from '../useDashboardRequest'

const { Text } = Typography

/** Màu các nhóm hàng theo thứ tự lớn trước; hai nhóm gom ("Chưa phân nhóm hàng", "Các nhóm khác") dùng màu xám. */
const CATEGORY_COLORS = ['#1890ff', '#52c41a', '#fa8c16', '#eb2f96', '#13c2c2', '#722ed1', '#faad14', '#2f54eb']
const UNCATEGORIZED_COLOR = '#8c8c8c'
const OTHERS_COLOR = '#bfbfbf'

const renderLegendText = (value: ReactNode) => <span className="text-neutral-600 text-xs">{value}</span>

function colorOf(slice: StockCategorySlice, index: number): string {
  if (slice.kind === 'UNCATEGORIZED') return UNCATEGORIZED_COLOR
  if (slice.kind === 'OTHERS') return OTHERS_COLOR
  return CATEGORY_COLORS[index % CATEGORY_COLORS.length]
}

interface Props {
  request: DashboardRequest<StockByCategory>
  onRetry: () => void
}

/**
 * Biểu đồ donut tỷ trọng tồn kho theo nhóm hàng. Tồn kho chưa có giá vốn (như thẻ "Giá trị tồn kho") nên backend tính
 * theo số lượng tồn thực tế; thẻ ghi rõ cơ sở tính để không bị hiểu là tỷ trọng giá trị.
 */
export default function StockByCategoryCard({ request, onRetry }: Props) {
  const { data, error, loading } = request
  const byValue = data?.basis === 'VALUE' && data.valueVisible
  const slices = (data?.slices ?? []).map((slice, index) => ({
    ...slice,
    quantity: Number(slice.quantity),
    amount: Number(slice.value ?? 0),
    fill: colorOf(slice, index),
  }))

  return (
    <Card
      variant="borderless"
      className="shadow-none border border-slate-200/80 bg-white h-full"
      loading={loading}
      title={
        <div className="flex flex-wrap items-center justify-between gap-2 py-1">
          <div className="min-w-0">
            <div className="font-semibold text-slate-900 text-sm">Tỷ trọng tồn kho theo nhóm hàng</div>
            <div className="text-[11px] font-normal text-slate-500">Tồn tại thời điểm xem, không đổi theo khoảng thời gian</div>
          </div>
          {data && !error && (
            <Tag className="text-xs m-0">{byValue ? 'Theo giá trị tồn' : 'Theo số lượng tồn'}</Tag>
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
      ) : !data || slices.length === 0 ? (
        <Empty description="Chưa có hàng tồn kho trong phạm vi đang chọn" />
      ) : (
        <div className="flex flex-col gap-2">
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={slices}
                dataKey={byValue ? 'amount' : 'quantity'}
                nameKey="name"
                innerRadius={65}
                outerRadius={100}
                paddingAngle={2}
                isAnimationActive={false}
              >
                <Label
                  value={formatNumber(Number(data.totalQuantity))}
                  position="center"
                  fill="#0f172a"
                  fontSize={14}
                  fontWeight={600}
                />
              </Pie>
              <RechartsTooltip
                formatter={(_value, _name, item) => {
                  const slice = item?.payload as (typeof slices)[number] | undefined
                  if (!slice) return ''
                  return `${formatNumber(slice.quantity)} (${slice.percentage}%), ${formatNumber(slice.productCount)} mặt hàng`
                }}
              />
              <Legend verticalAlign="bottom" itemSorter={null} formatter={renderLegendText} />
            </PieChart>
          </ResponsiveContainer>
          <Text type="secondary" className="text-[11px]">
            Tổng {formatNumber(Number(data.totalQuantity))} {data.unit} của {formatNumber(data.productCount)} mặt hàng,{' '}
            {formatNumber(data.categoryCount)} nhóm hàng. {data.basisNote}.
          </Text>
          {data.dataNote && <Alert type="warning" showIcon className="text-xs" message={data.dataNote} />}
        </div>
      )}
    </Card>
  )
}
