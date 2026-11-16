import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Breadcrumb, Button, Card, DatePicker, Select, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useState } from 'react'
import { formatCurrency } from '../../utils/format'
import { PaymentStatusTag } from './components/StatusTags'
import { fetchPlatformPayments, type PlatformPayment } from './platformApi'
import { CYCLE_LABELS, enumLabel, formatVietnamDateTime, PAYMENT_STATUS_LABELS, planLabel } from './platformFormat'
import { PLATFORM_PAGE_SIZE } from './platformQuery'
import { useKeyedRequest } from './useKeyedRequest'

const { Title, Text } = Typography
const { RangePicker } = DatePicker

const STATUS_OPTIONS = ['PENDING', 'PAID', 'CANCELLED', 'EXPIRED'].map((value) => ({
  value,
  label: PAYMENT_STATUS_LABELS[value],
}))

type DateRange = [string | undefined, string | undefined]

const columns: ColumnsType<PlatformPayment> = [
  {
    title: 'Mã đơn',
    dataIndex: 'orderCode',
    key: 'orderCode',
    render: (value: number) => <span className="font-mono text-xs">{value}</span>,
  },
  {
    title: 'Doanh nghiệp',
    key: 'company',
    render: (_, row) => (
      <div className="min-w-0">
        <div className="font-semibold text-slate-900">{row.companyName}</div>
        <div className="text-[11px] text-slate-500 font-mono">{row.companyCode}</div>
      </div>
    ),
  },
  { title: 'Gói', key: 'plan', render: (_, row) => planLabel(row.plan) },
  { title: 'Chu kỳ', key: 'cycle', render: (_, row) => enumLabel(CYCLE_LABELS, row.cycle) },
  {
    title: 'Số tiền',
    key: 'amount',
    align: 'right',
    render: (_, row) => <span className="tabular-nums font-medium">{formatCurrency(row.amount)}</span>,
  },
  { title: 'Trạng thái', key: 'status', render: (_, row) => <PaymentStatusTag status={row.status} /> },
  {
    title: 'Tạo lúc',
    key: 'createdAt',
    render: (_, row) => <span className="whitespace-nowrap">{formatVietnamDateTime(row.createdAt)}</span>,
  },
  {
    title: 'Thanh toán lúc',
    key: 'paidAt',
    render: (_, row) => <span className="whitespace-nowrap">{formatVietnamDateTime(row.paidAt)}</span>,
  },
]

/** Giao dịch thanh toán của mọi doanh nghiệp khách hàng: lọc theo trạng thái và khoảng ngày tạo, phân trang. */
export default function PlatformPaymentsPage() {
  const [status, setStatus] = useState<string | undefined>()
  const [dateRange, setDateRange] = useState<DateRange>([undefined, undefined])
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(PLATFORM_PAGE_SIZE)
  const [reloadToken, setReloadToken] = useState(0)

  const [from, to] = dateRange
  const load = useCallback(
    () => fetchPlatformPayments({ status, from, to, page, size: pageSize }),
    [status, from, to, page, pageSize],
  )
  const key = JSON.stringify([status, from, to, page, pageSize, reloadToken])
  const { data, error, loading } = useKeyedRequest(key, load, 'Không tải được danh sách thanh toán')
  const hasFilters = Boolean(status || from)
  const reload = () => setReloadToken((token) => token + 1)

  return (
    <div className="flex flex-col gap-5">
      <div>
        <Breadcrumb items={[{ title: 'Quản trị nền tảng' }, { title: 'Thanh toán' }]} className="text-xs mb-1" />
        <Title level={3} className="!mb-0 font-bold text-gray-900">
          Thanh toán
        </Title>
        <Text type="secondary" className="text-xs">
          Giao dịch nâng cấp và gia hạn gói của các doanh nghiệp, mới tạo trước
        </Text>
      </div>

      <Card variant="borderless" className="shadow-xs">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 mb-4">
          <div className="flex flex-wrap items-center gap-2">
            <Select
              placeholder="Trạng thái"
              value={status}
              onChange={(value) => {
                setStatus(value)
                setPage(0)
              }}
              allowClear
              className="w-44"
              options={STATUS_OPTIONS}
            />
            <RangePicker
              format="DD/MM/YYYY"
              placeholder={['Từ ngày tạo', 'Đến ngày tạo']}
              onChange={(dates) => {
                if (dates && dates[0] && dates[1]) {
                  setDateRange([dates[0].format('YYYY-MM-DD'), dates[1].format('YYYY-MM-DD')])
                } else {
                  setDateRange([undefined, undefined])
                }
                setPage(0)
              }}
              className="w-full sm:w-64"
            />
          </div>
          <Button icon={<ReloadOutlined />} onClick={reload} loading={loading}>
            Làm mới
          </Button>
        </div>

        {error && (
          <Alert
            type="error"
            showIcon
            message={error}
            className="mb-3"
            action={
              <Button size="small" onClick={reload}>
                Thử lại
              </Button>
            }
          />
        )}

        <Table<PlatformPayment>
          rowKey="orderCode"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={loading}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: hasFilters ? 'Không có giao dịch nào khớp bộ lọc' : 'Chưa có giao dịch nào' }}
          pagination={{
            current: page + 1,
            pageSize,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (total, range) => `Hiển thị ${range[0]} - ${range[1]} của ${total} giao dịch`,
            onChange: (nextPage, nextSize) => {
              setPage(nextSize !== pageSize ? 0 : nextPage - 1)
              setPageSize(nextSize)
            },
          }}
        />
      </Card>
    </div>
  )
}
