import {
  DatabaseOutlined,
  InboxOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { Card, Col, Progress, Row, Skeleton } from 'antd'
import type { WarehouseSummary } from '../types'
import { formatNumber } from '../../../utils/format'

interface Props {
  summary: WarehouseSummary | null
  loading: boolean
}

export default function WarehouseMetricsCards({ summary, loading }: Props) {
  if (loading || !summary) {
    return (
      <Row gutter={[16, 16]}>
        {[1, 2, 3, 4].map((i) => (
          <Col xs={24} sm={12} lg={6} key={i}>
            <Card className="border border-slate-200/80 rounded-lg shadow-none">
              <Skeleton active paragraph={{ rows: 2 }} />
            </Card>
          </Col>
        ))}
      </Row>
    )
  }

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} sm={12} lg={6}>
        <div className="bg-white border border-slate-200/80 rounded-lg p-4 transition-all">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">
                Tổng số kho
              </div>
              <div className="text-2xl font-semibold text-slate-900 tabular-nums">
                {summary.totalWarehouses}{' '}
                <span className="text-xs font-normal text-slate-400 font-sans">kho</span>
              </div>
            </div>
            <div className="w-9 h-9 rounded-md bg-slate-50 border border-slate-200/60 flex items-center justify-center text-slate-600 text-base">
              <ShopOutlined />
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-500 flex items-center gap-1.5 pt-2 border-t border-slate-100">
            <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500" />
            <span>
              <strong className="text-slate-700 font-semibold">{summary.activeWarehouses}</strong> hoạt động / {summary.totalWarehouses - summary.activeWarehouses} tạm dừng
            </span>
          </div>
        </div>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <div className="bg-white border border-slate-200/80 rounded-lg p-4 transition-all">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">
                Sức chứa định mức
              </div>
              <div className="text-2xl font-semibold text-slate-900 tabular-nums">
                {formatNumber(summary.totalCapacity)}{' '}
                <span className="text-xs font-normal text-slate-400 font-sans">vị trí</span>
              </div>
            </div>
            <div className="w-9 h-9 rounded-md bg-slate-50 border border-slate-200/60 flex items-center justify-center text-slate-600 text-base">
              <DatabaseOutlined />
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-500 pt-2 border-t border-slate-100 truncate">
            Tổng dung tích thiết kế trên toàn hệ thống
          </div>
        </div>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <div className="bg-white border border-slate-200/80 rounded-lg p-4 transition-all">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">
                Vị trí ô/kệ hiện hữu
              </div>
              <div className="text-2xl font-semibold text-slate-900 tabular-nums">
                {formatNumber(summary.totalLocations)}{' '}
                <span className="text-xs font-normal text-slate-400 font-sans">ô</span>
              </div>
            </div>
            <div className="w-9 h-9 rounded-md bg-slate-50 border border-slate-200/60 flex items-center justify-center text-slate-600 text-base">
              <InboxOutlined />
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-500 flex justify-between pt-2 border-t border-slate-100">
            <span>Đang dùng: <strong className="text-slate-700 font-semibold">{formatNumber(summary.occupiedLocations)}</strong></span>
            <span>Còn trống: <strong className="text-emerald-600 font-semibold">{formatNumber(summary.availableLocations)}</strong></span>
          </div>
        </div>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <div className="bg-white border border-slate-200/80 rounded-lg p-4 transition-all">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">
                Tỷ lệ lấp đầy
              </div>
              <div className="text-2xl font-semibold text-slate-900 tabular-nums">
                {summary.overallUtilizationRate.toFixed(1)}%
              </div>
            </div>
            <div className="w-9 h-9 rounded-md bg-slate-50 border border-slate-200/60 flex items-center justify-center text-slate-600 text-base">
              <InboxOutlined />
            </div>
          </div>
          <div className="mt-3 pt-2 border-t border-slate-100">
            <Progress
              percent={summary.overallUtilizationRate}
              strokeColor={
                summary.overallUtilizationRate > 85
                  ? '#ef4444'
                  : summary.overallUtilizationRate > 65
                  ? '#f59e0b'
                  : '#10b981'
              }
              showInfo={false}
              size="small"
              className="m-0"
            />
          </div>
        </div>
      </Col>
    </Row>
  )
}

