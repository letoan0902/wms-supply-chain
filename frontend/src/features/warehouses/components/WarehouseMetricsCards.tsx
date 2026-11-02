import {
  CheckCircleOutlined,
  DatabaseOutlined,
  InboxOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { Card, Col, Progress, Row, Skeleton, Statistic } from 'antd'
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
            <Card variant="borderless" className="shadow-xs">
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
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Tổng số kho bãi</span>}
              value={summary.totalWarehouses}
              suffix={<span className="text-xs text-gray-400">kho</span>}
              valueStyle={{ fontWeight: 600, color: '#1677ff' }}
            />
            <div className="p-3 bg-blue-50 rounded-xl text-blue-600 text-xl">
              <ShopOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500 flex items-center gap-1">
            <CheckCircleOutlined className="text-green-500" />
            <span>
              <strong>{summary.activeWarehouses}</strong> đang hoạt động / {summary.totalWarehouses - summary.activeWarehouses} tạm dừng
            </span>
          </div>
        </Card>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Tổng sức chứa thiết kế</span>}
              value={formatNumber(summary.totalCapacity)}
              suffix={<span className="text-xs text-gray-400">vị trí</span>}
              valueStyle={{ fontWeight: 600, color: '#52c41a' }}
            />
            <div className="p-3 bg-green-50 rounded-xl text-green-600 text-xl">
              <DatabaseOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            Sức chứa định mức đăng ký trên toàn bộ các kho
          </div>
        </Card>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Vị trí Ô/Kệ hiện hữu</span>}
              value={formatNumber(summary.totalLocations)}
              suffix={<span className="text-xs text-gray-400">vị trí</span>}
              valueStyle={{ fontWeight: 600, color: '#722ed1' }}
            />
            <div className="p-3 bg-purple-50 rounded-xl text-purple-600 text-xl">
              <InboxOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500 flex justify-between">
            <span>Đang dùng: <strong>{formatNumber(summary.occupiedLocations)}</strong></span>
            <span>Còn trống: <strong>{formatNumber(summary.availableLocations)}</strong></span>
          </div>
        </Card>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Tỷ lệ lấp đầy hệ thống</span>}
              value={summary.overallUtilizationRate}
              precision={1}
              suffix="%"
              valueStyle={{
                fontWeight: 600,
                color: summary.overallUtilizationRate > 85 ? '#cf1322' : '#fa8c16',
              }}
            />
            <div className="p-3 bg-amber-50 rounded-xl text-amber-600 text-xl">
              <InboxOutlined />
            </div>
          </div>
          <div className="mt-1">
            <Progress
              percent={summary.overallUtilizationRate}
              status={summary.overallUtilizationRate > 90 ? 'exception' : 'normal'}
              strokeColor={summary.overallUtilizationRate > 85 ? '#ff4d4f' : '#fa8c16'}
              showInfo={false}
              size="small"
            />
          </div>
        </Card>
      </Col>
    </Row>
  )
}

