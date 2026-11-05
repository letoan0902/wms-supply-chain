import {
  CheckCircleOutlined,
  ContainerOutlined,
  LockOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Card, Col, Row, Skeleton, Statistic } from 'antd'
import type { UserMetrics } from '../types'

interface Props {
  metrics: UserMetrics
  loading: boolean
}

export default function UserMetricsCards({ metrics, loading }: Props) {
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

  const activePercent =
    metrics.totalUsers > 0
      ? Math.round((metrics.activeUsers / metrics.totalUsers) * 100)
      : 100

  return (
    <Row gutter={[16, 16]}>
      {/* 1. Tổng số tài khoản */}
      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Tổng số tài khoản</span>}
              value={metrics.totalUsers}
              suffix={<span className="text-xs text-gray-400">nhân sự</span>}
              valueStyle={{ fontWeight: 600, color: '#1677ff' }}
            />
            <div className="p-3 bg-blue-50 rounded-xl text-blue-600 text-xl">
              <UserOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            <span>Toàn bộ tài khoản phân quyền RBAC</span>
          </div>
        </Card>
      </Col>

      {/* 2. Đang hoạt động */}
      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Đang hoạt động</span>}
              value={metrics.activeUsers}
              suffix={<span className="text-xs text-gray-400">tài khoản</span>}
              valueStyle={{ fontWeight: 600, color: '#52c41a' }}
            />
            <div className="p-3 bg-green-50 rounded-xl text-green-600 text-xl">
              <CheckCircleOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500 flex items-center gap-1">
            <CheckCircleOutlined className="text-green-500" />
            <span>
              Chiếm <strong>{activePercent}%</strong> tổng nhân sự
            </span>
          </div>
        </Card>
      </Col>

      {/* 3. Tạm khóa / Cần chú ý */}
      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Tài khoản bị khóa</span>}
              value={metrics.lockedUsers}
              suffix={<span className="text-xs text-gray-400">tài khoản</span>}
              valueStyle={{
                fontWeight: 600,
                color: metrics.lockedUsers > 0 ? '#ff4d4f' : '#8c8c8c',
              }}
            />
            <div
              className={`p-3 rounded-xl text-xl ${
                metrics.lockedUsers > 0
                  ? 'bg-red-50 text-red-600'
                  : 'bg-gray-100 text-gray-500'
              }`}
            >
              <LockOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            <span>
              {metrics.lockedUsers > 0
                ? 'Khóa do sai mật khẩu hoặc Admin khóa'
                : 'Không có tài khoản bị khóa'}
            </span>
          </div>
        </Card>
      </Col>

      {/* 4. Nhân sự vận hành kho */}
      <Col xs={24} sm={12} lg={6}>
        <Card variant="borderless" className="shadow-xs hover:shadow-md transition-shadow">
          <div className="flex items-start justify-between">
            <Statistic
              title={<span className="text-gray-500 font-medium">Nhân sự vận hành kho</span>}
              value={metrics.warehouseOpsUsers}
              suffix={<span className="text-xs text-gray-400">nhân sự</span>}
              valueStyle={{ fontWeight: 600, color: '#13c2c2' }}
            />
            <div className="p-3 bg-cyan-50 rounded-xl text-cyan-600 text-xl">
              <ContainerOutlined />
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            <span>Quản lý kho & Nhân viên kho (Warehouse Scope)</span>
          </div>
        </Card>
      </Col>
    </Row>
  )
}
