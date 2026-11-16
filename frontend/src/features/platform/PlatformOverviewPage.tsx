import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Col, Empty, Row, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { formatCurrency, formatNumber } from '../../utils/format'
import { useDashboardRequest } from '../dashboard/useDashboardRequest'
import { formatInstantDate } from '../inventory/inventoryUtils'
import { PlanDistributionChart, RegistrationsChart, RevenueChart } from './components/OverviewCharts'
import { CompanyStatusTag } from './components/StatusTags'
import { fetchPlatformOverview, type PlatformCompanySummary, type PlatformOverview, type PlatformPayment } from './platformApi'
import { formatVietnamDateTime, planLabel } from './platformFormat'

const { Title, Text } = Typography

interface StatCard {
  key: string
  title: string
  value: string
  subText: string
}

function buildStatCards(data: PlatformOverview): StatCard[] {
  return [
    { key: 'total', title: 'Tổng doanh nghiệp', value: formatNumber(data.companies.total), subText: 'Mọi doanh nghiệp đã đăng ký' },
    { key: 'active', title: 'Đang hoạt động', value: formatNumber(data.companies.active), subText: 'Đã kích hoạt và không bị khóa' },
    { key: 'pending', title: 'Chờ kích hoạt', value: formatNumber(data.companies.pending), subText: 'Chưa xác nhận email đăng ký' },
    {
      key: 'suspended',
      title: 'Bị tạm khóa',
      value: formatNumber(data.companies.suspended),
      subText: 'Người dùng không đăng nhập được',
    },
    {
      key: 'users',
      title: 'Người dùng',
      value: formatNumber(data.users.total),
      subText: `${formatNumber(data.users.active)} tài khoản đang hoạt động`,
    },
    {
      key: 'thisMonth',
      title: 'Doanh thu tháng này',
      value: formatCurrency(data.revenue.thisMonth),
      subText: 'Thanh toán thành công trong tháng hiện tại',
    },
    {
      key: 'revenue',
      title: 'Tổng doanh thu',
      value: formatCurrency(data.revenue.total),
      subText: `${formatNumber(data.revenue.paidCount)} giao dịch thành công`,
    },
    {
      key: 'expiring',
      title: 'Thuê bao sắp hết hạn',
      value: formatNumber(data.subscriptions.expiringSoon),
      subText: `Hết hạn trong 7 ngày tới; ${formatNumber(data.subscriptions.expired)} thuê bao đã hết hạn`,
    },
  ]
}

const companyColumns: ColumnsType<PlatformCompanySummary> = [
  {
    title: 'Doanh nghiệp',
    key: 'company',
    render: (_, row) => (
      <div className="min-w-0">
        <div className="font-semibold text-slate-900 text-xs">{row.name}</div>
        <div className="text-[11px] text-slate-500 font-mono">{row.code}</div>
      </div>
    ),
  },
  { title: 'Gói', key: 'plan', render: (_, row) => <span className="text-xs">{planLabel(row.plan, row.planName)}</span> },
  { title: 'Trạng thái', key: 'status', render: (_, row) => <CompanyStatusTag status={row.status} /> },
  {
    title: 'Ngày đăng ký',
    key: 'createdAt',
    responsive: ['sm'],
    render: (_, row) => <span className="text-xs text-slate-600">{formatInstantDate(row.createdAt)}</span>,
  },
]

const paymentColumns: ColumnsType<PlatformPayment> = [
  {
    title: 'Doanh nghiệp',
    key: 'company',
    render: (_, row) => (
      <div className="min-w-0">
        <div className="font-semibold text-slate-900 text-xs">{row.companyName}</div>
        <div className="text-[11px] text-slate-500">
          {planLabel(row.plan)} · <span className="font-mono">{row.orderCode}</span>
        </div>
      </div>
    ),
  },
  {
    title: 'Số tiền',
    key: 'amount',
    align: 'right',
    render: (_, row) => <span className="text-xs font-medium tabular-nums">{formatCurrency(row.amount)}</span>,
  },
  {
    title: 'Thanh toán lúc',
    key: 'paidAt',
    responsive: ['sm'],
    render: (_, row) => <span className="text-xs text-slate-600">{formatVietnamDateTime(row.paidAt)}</span>,
  },
]

function TableCard({ title, linkTo, children }: { title: string; linkTo: string; children: ReactNode }) {
  return (
    <Card
      variant="borderless"
      className="shadow-none border border-slate-200/80 bg-white h-full"
      title={<span className="font-semibold text-slate-900 text-sm">{title}</span>}
      extra={
        <Link to={linkTo} className="text-xs">
          Xem tất cả
        </Link>
      }
    >
      {children}
    </Card>
  )
}

/** Tổng quan nền tảng: số liệu doanh nghiệp, người dùng, doanh thu, biểu đồ 6 tháng và các bản ghi mới nhất. */
export default function PlatformOverviewPage() {
  const [reloadToken, setReloadToken] = useState(0)
  const load = useCallback(() => fetchPlatformOverview(), [])
  const { data, error, loading } = useDashboardRequest(
    `overview:${reloadToken}`,
    load,
    'Không tải được số liệu tổng quan nền tảng',
  )
  const reload = () => setReloadToken((token) => token + 1)

  return (
    <div className="flex flex-col gap-5 w-full">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-4 rounded-lg border border-slate-200/80 shadow-xs">
        <div className="min-w-0">
          <Title level={4} className="!mb-0 font-semibold text-slate-900">
            Tổng quan nền tảng
          </Title>
          <Text type="secondary" className="text-xs">
            Số liệu của các doanh nghiệp khách hàng, không tính tài khoản quản trị nền tảng
          </Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={reload} loading={loading} className="text-xs self-start sm:self-auto">
          Làm mới
        </Button>
      </div>

      {error ? (
        <Alert
          type="error"
          showIcon
          message="Không tải được số liệu tổng quan nền tảng"
          description={error}
          action={
            <Button size="small" onClick={reload}>
              Thử lại
            </Button>
          }
          className="rounded-lg"
        />
      ) : (
        <>
          <Row gutter={[16, 16]}>
            {loading || !data
              ? Array.from({ length: 8 }, (_, i) => (
                  <Col key={i} xs={24} sm={12} xl={6}>
                    <Card variant="borderless" loading className="h-full border border-slate-200/80" />
                  </Col>
                ))
              : buildStatCards(data).map((card) => (
                  <Col key={card.key} xs={24} sm={12} xl={6}>
                    <Card variant="borderless" className="h-full border border-slate-200/80 bg-white">
                      <div className="text-xs font-medium text-slate-500">{card.title}</div>
                      <div className="mt-1 text-[1.35rem] font-semibold text-slate-900 tabular-nums break-words">
                        {card.value}
                      </div>
                      <span className="text-xs text-slate-500 mt-1 block">{card.subText}</span>
                    </Card>
                  </Col>
                ))}
          </Row>

          {data && !loading && (
            <>
              <Row gutter={[16, 16]}>
                <Col xs={24} lg={12} xl={8}>
                  <RegistrationsChart data={data.registrationsByMonth} />
                </Col>
                <Col xs={24} lg={12} xl={8}>
                  <RevenueChart data={data.revenueByMonth} />
                </Col>
                <Col xs={24} xl={8}>
                  <PlanDistributionChart subscriptions={data.subscriptions} />
                </Col>
              </Row>

              <Row gutter={[16, 16]}>
                <Col xs={24} xl={12}>
                  <TableCard title="Doanh nghiệp mới đăng ký" linkTo="/platform/companies">
                    <Table<PlatformCompanySummary>
                      rowKey="id"
                      columns={companyColumns}
                      dataSource={data.recentCompanies}
                      pagination={false}
                      size="middle"
                      locale={{ emptyText: <Empty description="Chưa có doanh nghiệp nào đăng ký" /> }}
                    />
                  </TableCard>
                </Col>
                <Col xs={24} xl={12}>
                  <TableCard title="Thanh toán gần đây" linkTo="/platform/payments">
                    <Table<PlatformPayment>
                      rowKey="orderCode"
                      columns={paymentColumns}
                      dataSource={data.recentPayments}
                      pagination={false}
                      size="middle"
                      locale={{ emptyText: <Empty description="Chưa có thanh toán thành công nào" /> }}
                    />
                  </TableCard>
                </Col>
              </Row>
            </>
          )}
        </>
      )}
    </div>
  )
}
