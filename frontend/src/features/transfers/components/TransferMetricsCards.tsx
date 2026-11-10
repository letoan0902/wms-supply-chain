import {
  CarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { Card, Col, Row, Skeleton, Statistic } from 'antd'
import { formatNumber } from '../../../utils/format'
import type { StockTransfer } from '../types'

interface Props {
  transfers: StockTransfer[]
  totalCount: number
  loading: boolean
}

export default function TransferMetricsCards({ transfers, totalCount, loading }: Props) {
  if (loading) {
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

  // Thống kê nhanh theo danh sách lệnh
  const draftCount = transfers.filter((t) => t.status === 'DRAFT').length
  const inTransitCount = transfers.filter((t) => t.status === 'IN_TRANSIT').length
  const completedCount = transfers.filter(
    (t) => t.status === 'COMPLETED' || t.status === 'PARTIAL_RECEIVED'
  ).length

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Tổng số lệnh điều chuyển</span>}
              value={formatNumber(totalCount)}
              suffix={<span className="text-xs text-gray-400">lệnh</span>}
              valueStyle={{ fontWeight: 600, color: '#1677ff' }}
            />
            <div className="p-3 bg-blue-50 rounded-xl text-blue-600 text-xl">
              <SwapOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            Toàn bộ các lệnh điều chuyển nội bộ giữa các kho
          </div>
        </Card>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Chờ xuất kho (Bản nháp)</span>}
              value={formatNumber(draftCount)}
              suffix={<span className="text-xs text-gray-400">lệnh</span>}
              valueStyle={{ fontWeight: 600, color: '#fa8c16' }}
            />
            <div className="p-3 bg-orange-50 rounded-xl text-orange-600 text-xl">
              <ClockCircleOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            Lệnh đã lập, đang chờ thủ kho duyệt xuất đi đường
          </div>
        </Card>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Đang trên đường vận chuyển</span>}
              value={formatNumber(inTransitCount)}
              suffix={<span className="text-xs text-gray-400">lệnh</span>}
              valueStyle={{ fontWeight: 600, color: '#13c2c2' }}
            />
            <div className="p-3 bg-cyan-50 rounded-xl text-cyan-600 text-xl">
              <CarOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            Hàng đã xuất kho nguồn, đang chuyển tới kho đích
          </div>
        </Card>
      </Col>

      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Đã hoàn thành / Nhận hàng</span>}
              value={formatNumber(completedCount)}
              suffix={<span className="text-xs text-gray-400">lệnh</span>}
              valueStyle={{ fontWeight: 600, color: '#52c41a' }}
            />
            <div className="p-3 bg-green-50 rounded-xl text-green-600 text-xl">
              <CheckCircleOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            Kho đích đã kiểm đếm và hoàn tất nhập kho
          </div>
        </Card>
      </Col>
    </Row>
  )
}

