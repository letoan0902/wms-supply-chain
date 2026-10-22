import { Card, Col, Row, Statistic, Tag, Typography } from 'antd'
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
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatCurrency, formatNumber } from '../utils/format'
import { kpiSamples, monthlyTrade, trendingItems } from './dashboardSampleData'

const totalTrendingQuantity = trendingItems.reduce((sum, item) => sum + item.value, 0)

// Chữ chú thích dùng màu trung tính thay vì màu của từng chuỗi dữ liệu
const renderLegendText = (value: ReactNode) => <span className="text-neutral-600">{value}</span>

export default function DashboardPage() {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2">
        <Typography.Text type="secondary">
          Tổng quan hoạt động kho và chuỗi cung ứng
        </Typography.Text>
        <Tag color="gold">Dữ liệu mẫu</Tag>
      </div>

      <Row gutter={[16, 16]}>
        {kpiSamples.map((kpi) => (
          <Col key={kpi.key} xs={24} sm={12} xl={6}>
            <Card>
              <Statistic
                title={kpi.title}
                value={kpi.value}
                formatter={() =>
                  kpi.kind === 'currency' ? formatCurrency(kpi.value) : formatNumber(kpi.value)
                }
              />
              <Typography.Text type="secondary">{kpi.note}</Typography.Text>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card title="Mua vào và bán ra theo tháng (triệu đồng)">
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={monthlyTrade}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="month" />
                <YAxis />
                <Tooltip formatter={(value) => `${formatNumber(Number(value))} triệu đồng`} />
                <Legend itemSorter="dataKey" formatter={renderLegendText} />
                <Bar dataKey="purchase" name="Mua vào" fill="#1677ff" radius={[4, 4, 0, 0]} />
                <Bar dataKey="sales" name="Bán ra" fill="#52c41a" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        <Col xs={24} xl={10}>
          <Card title="Trending Items - Mặt hàng bán chạy">
            <ResponsiveContainer width="100%" height={320}>
              <PieChart>
                <Pie
                  data={trendingItems}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={65}
                  outerRadius={100}
                  paddingAngle={2}
                >
                  <Label
                    value={`${formatNumber(totalTrendingQuantity)} sản phẩm`}
                    position="center"
                    fill="#262626"
                    fontSize={14}
                    fontWeight={600}
                  />
                </Pie>
                <Tooltip formatter={(value) => `${formatNumber(Number(value))} sản phẩm`} />
                <Legend verticalAlign="bottom" itemSorter={null} formatter={renderLegendText} />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
