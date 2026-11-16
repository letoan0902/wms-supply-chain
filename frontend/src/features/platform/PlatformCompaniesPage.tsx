import { ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Breadcrumb, Button, Card, Input, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useState } from 'react'
import { formatNumber } from '../../utils/format'
import { formatInstantDate } from '../inventory/inventoryUtils'
import { useDebouncedValue } from '../inventory/useDebouncedValue'
import ApproveCompanyModal from './components/ApproveCompanyModal'
import CompanyDetailDrawer from './components/CompanyDetailDrawer'
import CompanyStatusModal, { type StatusTarget } from './components/CompanyStatusModal'
import RejectCompanyModal from './components/RejectCompanyModal'
import { CompanyStatusTag, SubscriptionStatusTag } from './components/StatusTags'
import { fetchPlatformCompanies, type PlatformCompanySummary } from './platformApi'
import { COMPANY_STATUS_LABELS, formatVietnamDateTime, PLAN_LABELS, PLAN_ORDER, planLabel } from './platformFormat'
import { PLATFORM_PAGE_SIZE } from './platformQuery'
import { useKeyedRequest } from './useKeyedRequest'

const { Title, Text } = Typography

/** Chờ người dùng ngừng gõ chừng này rồi mới tìm. */
const SEARCH_DEBOUNCE_MS = 400

const STATUS_OPTIONS = ['ACTIVE', 'PENDING', 'SUSPENDED'].map((value) => ({ value, label: COMPANY_STATUS_LABELS[value] }))
const PLAN_OPTIONS = PLAN_ORDER.map((value) => ({ value, label: PLAN_LABELS[value] }))

/**
 * Danh sách doanh nghiệp khách hàng: tìm kiếm, lọc theo trạng thái và gói, xem chi tiết, duyệt hoặc từ chối bản đăng ký
 * đang chờ, tạm khóa và mở khóa.
 */
export default function PlatformCompaniesPage() {
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebouncedValue(keyword.trim(), SEARCH_DEBOUNCE_MS)
  const [status, setStatus] = useState<string | undefined>()
  const [plan, setPlan] = useState<string | undefined>()
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(PLATFORM_PAGE_SIZE)
  const [reloadToken, setReloadToken] = useState(0)

  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detailReloadToken, setDetailReloadToken] = useState(0)
  const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null)
  const [approveTarget, setApproveTarget] = useState<StatusTarget | null>(null)
  const [rejectTarget, setRejectTarget] = useState<StatusTarget | null>(null)

  const load = useCallback(
    () => fetchPlatformCompanies({ keyword: debouncedKeyword, status, plan, page, size: pageSize }),
    [debouncedKeyword, status, plan, page, pageSize],
  )
  const key = JSON.stringify([debouncedKeyword, status, plan, page, pageSize, reloadToken])
  const { data, error, loading } = useKeyedRequest(key, load, 'Không tải được danh sách doanh nghiệp')

  const hasFilters = Boolean(keyword || status || plan)

  const resetFilters = () => {
    setKeyword('')
    setStatus(undefined)
    setPlan(undefined)
    setPage(0)
  }

  // Sau khi đổi trạng thái, duyệt hoặc từ chối: tải lại danh sách và chi tiết đang mở
  const handleCompanyChanged = () => {
    setStatusTarget(null)
    setApproveTarget(null)
    setRejectTarget(null)
    setReloadToken((token) => token + 1)
    setDetailReloadToken((token) => token + 1)
  }

  const columns: ColumnsType<PlatformCompanySummary> = [
    {
      title: 'Mã',
      dataIndex: 'code',
      key: 'code',
      render: (value: string) => <span className="font-mono text-xs text-slate-700">{value}</span>,
    },
    {
      title: 'Tên doanh nghiệp',
      dataIndex: 'name',
      key: 'name',
      render: (value: string) => <span className="font-semibold text-slate-900">{value}</span>,
    },
    { title: 'Mã số thuế', key: 'taxCode', render: (_, row) => row.taxCode || '—' },
    { title: 'Email', key: 'email', render: (_, row) => row.email || '—' },
    { title: 'Gói', key: 'plan', render: (_, row) => planLabel(row.plan, row.planName) },
    { title: 'Thuê bao', key: 'subscriptionStatus', render: (_, row) => <SubscriptionStatusTag status={row.subscriptionStatus} /> },
    {
      title: 'Hạn',
      key: 'expiresAt',
      render: (_, row) => (row.expiresAt ? formatInstantDate(row.expiresAt) : row.plan ? 'Không thời hạn' : '—'),
    },
    {
      title: 'Người dùng',
      dataIndex: 'userCount',
      key: 'userCount',
      align: 'right',
      render: (value: number) => <span className="tabular-nums">{formatNumber(value)}</span>,
    },
    { title: 'Trạng thái', key: 'status', render: (_, row) => <CompanyStatusTag status={row.status} /> },
    {
      title: 'Ngày đăng ký',
      key: 'createdAt',
      render: (_, row) => <span className="whitespace-nowrap">{formatVietnamDateTime(row.createdAt)}</span>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      render: (_, row) =>
        row.status === 'PENDING' ? (
          <Space size="middle">
            <Button
              type="link"
              size="small"
              className="px-0"
              onClick={(event) => {
                event.stopPropagation()
                setApproveTarget(row)
              }}
            >
              Duyệt
            </Button>
            <Button
              type="link"
              size="small"
              danger
              className="px-0"
              onClick={(event) => {
                event.stopPropagation()
                setRejectTarget(row)
              }}
            >
              Từ chối
            </Button>
          </Space>
        ) : row.status === 'ACTIVE' || row.status === 'SUSPENDED' ? (
          <Button
            type="link"
            size="small"
            danger={row.status === 'ACTIVE'}
            className="px-0"
            onClick={(event) => {
              event.stopPropagation()
              setStatusTarget(row)
            }}
          >
            {row.status === 'ACTIVE' ? 'Tạm khóa' : 'Mở khóa'}
          </Button>
        ) : null,
    },
  ]

  return (
    <div className="flex flex-col gap-5">
      <div>
        <Breadcrumb items={[{ title: 'Quản trị nền tảng' }, { title: 'Doanh nghiệp' }]} className="text-xs mb-1" />
        <Title level={3} className="!mb-0 font-bold text-gray-900">
          Doanh nghiệp
        </Title>
        <Text type="secondary" className="text-xs">
          Các doanh nghiệp khách hàng đã đăng ký; bấm vào một dòng để xem chi tiết
        </Text>
      </div>

      <Card variant="borderless" className="shadow-xs">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 mb-4">
          <div className="flex flex-wrap items-center gap-2">
            <Input
              placeholder="Tìm theo tên, mã, mã số thuế, email"
              prefix={<SearchOutlined className="text-gray-400" />}
              value={keyword}
              onChange={(event) => {
                setKeyword(event.target.value)
                setPage(0)
              }}
              allowClear
              className="w-full sm:w-72"
            />
            <Select
              placeholder="Trạng thái"
              value={status}
              onChange={(value) => {
                setStatus(value)
                setPage(0)
              }}
              allowClear
              className="w-40"
              options={STATUS_OPTIONS}
            />
            <Select
              placeholder="Gói"
              value={plan}
              onChange={(value) => {
                setPlan(value)
                setPage(0)
              }}
              allowClear
              className="w-40"
              options={PLAN_OPTIONS}
            />
            {hasFilters && <Button onClick={resetFilters}>Đặt lại</Button>}
          </div>
          <Button icon={<ReloadOutlined />} onClick={() => setReloadToken((token) => token + 1)} loading={loading}>
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
              <Button size="small" onClick={() => setReloadToken((token) => token + 1)}>
                Thử lại
              </Button>
            }
          />
        )}

        <Table<PlatformCompanySummary>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={loading}
          scroll={{ x: 'max-content' }}
          onRow={(row) => ({ onClick: () => setSelectedId(row.id), className: 'cursor-pointer' })}
          locale={{ emptyText: hasFilters ? 'Không có doanh nghiệp nào khớp bộ lọc' : 'Chưa có doanh nghiệp nào đăng ký' }}
          pagination={{
            current: page + 1,
            pageSize,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (total, range) => `Hiển thị ${range[0]} - ${range[1]} của ${total} doanh nghiệp`,
            onChange: (nextPage, nextSize) => {
              setPage(nextSize !== pageSize ? 0 : nextPage - 1)
              setPageSize(nextSize)
            },
          }}
        />
      </Card>

      <CompanyDetailDrawer
        companyId={selectedId}
        reloadToken={detailReloadToken}
        onClose={() => setSelectedId(null)}
        onRequestStatusChange={setStatusTarget}
        onRequestApprove={setApproveTarget}
        onRequestReject={setRejectTarget}
      />

      <CompanyStatusModal target={statusTarget} onCancel={() => setStatusTarget(null)} onDone={handleCompanyChanged} />
      <ApproveCompanyModal target={approveTarget} onCancel={() => setApproveTarget(null)} onDone={handleCompanyChanged} />
      <RejectCompanyModal target={rejectTarget} onCancel={() => setRejectTarget(null)} onDone={handleCompanyChanged} />
    </div>
  )
}
