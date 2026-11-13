import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EyeOutlined,
  LockOutlined,
  ReloadOutlined,
  SearchOutlined,
  UnlockOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Input,
  Radio,
  Space,
  Table,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
import { fetchCompanies } from './companyApi'
import ApproveCompanyModal from './components/ApproveCompanyModal'
import CompanyDetailDrawer from './components/CompanyDetailDrawer'
import CompanyStatusConfirmModal from './components/CompanyStatusConfirmModal'
import RejectCompanyModal from './components/RejectCompanyModal'
import { renderCompanyStatusBadge, renderPlanBadge } from './statusConfig'
import type { AdminCompanyItem, CompanyStatus } from './types'
import { formatDateTime } from '../../utils/format'
import { getApiErrorMessage } from '../../services/api'

export default function CompaniesPage() {
  const { message } = App.useApp()

  // State danh sách & phân trang
  const [loading, setLoading] = useState(false)
  const [companies, setCompanies] = useState<AdminCompanyItem[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  // Bộ lọc
  const [statusFilter, setStatusFilter] = useState<CompanyStatus | 'ALL'>('ALL')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [searchInput, setSearchInput] = useState('')

  // Modals & Drawer
  const [selectedCompany, setSelectedCompany] = useState<AdminCompanyItem | null>(null)
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false)
  const [approveModalOpen, setApproveModalOpen] = useState(false)
  const [rejectModalOpen, setRejectModalOpen] = useState(false)
  const [statusModalOpen, setStatusModalOpen] = useState(false)

  // Load danh sách doanh nghiệp
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const res = await fetchCompanies({
        keyword: searchKeyword,
        status: statusFilter,
        page: currentPage - 1,
        size: pageSize,
      })
      setCompanies(res.content)
      setTotalElements(res.totalElements)
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err, 'Không thể tải danh sách doanh nghiệp.')
      message.error(msg)
    } finally {
      setLoading(false)
    }
  }, [currentPage, pageSize, searchKeyword, statusFilter, message])

  useEffect(() => {
    loadData()
  }, [loadData])

  // Thống kê nhanh từ danh sách hiện tại
  const pendingCount = companies.filter((c) => c.status === 'PENDING').length
  const activeCount = companies.filter((c) => c.status === 'ACTIVE').length
  const suspendedCount = companies.filter((c) => c.status === 'SUSPENDED').length

  const handleSearch = () => {
    setCurrentPage(1)
    setSearchKeyword(searchInput.trim())
  }

  // Định nghĩa các cột của bảng theo chuẩn /unslop
  const columns: ColumnsType<AdminCompanyItem> = [
    {
      title: 'Doanh nghiệp',
      key: 'company',
      width: 260,
      render: (_, record) => (
        <div>
          <div className="font-medium text-xs text-slate-900">{record.name}</div>
          <div className="text-[11px] font-mono text-slate-500 mt-0.5">{record.code}</div>
        </div>
      ),
    },
    {
      title: 'Mã số thuế',
      dataIndex: 'taxCode',
      key: 'taxCode',
      width: 130,
      render: (val) => (
        <span className="font-mono text-xs text-slate-700">{val || '—'}</span>
      ),
    },
    {
      title: 'Quản trị viên đại diện',
      key: 'adminUser',
      width: 240,
      render: (_, record) => {
        const u = record.adminUser
        if (!u) return <span className="text-slate-400 text-xs">—</span>
        return (
          <div className="text-xs">
            <div className="font-medium text-slate-800">{u.fullName || u.username}</div>
            <div className="text-[11px] text-slate-500 font-mono">{u.email}</div>
          </div>
        )
      },
    },
    {
      title: 'Gói dịch vụ',
      key: 'subscription',
      width: 160,
      render: (_, record) => (
        <div className="space-y-1">
          {renderPlanBadge(record.subscription?.plan)}
          {record.subscription?.trialEndsAt && (
            <div className="text-[10px] text-slate-500 font-mono">
              Trial: {formatDateTime(record.subscription.trialEndsAt).split(' ')[0]}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: CompanyStatus) => renderCompanyStatusBadge(status),
    },
    {
      title: 'Ngày nộp đơn',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (val: string) => (
        <span className="font-mono text-xs text-slate-600">{formatDateTime(val)}</span>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Tooltip title="Xem chi tiết hồ sơ">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => {
                setSelectedCompany(record)
                setDetailDrawerOpen(true)
              }}
              className="text-slate-600 hover:text-slate-900"
            />
          </Tooltip>

          {record.status === 'PENDING' && (
            <>
              <Tooltip title="Phê duyệt đơn đăng ký">
                <Button
                  type="text"
                  size="small"
                  icon={<CheckCircleOutlined className="text-emerald-600" />}
                  onClick={() => {
                    setSelectedCompany(record)
                    setApproveModalOpen(true)
                  }}
                  className="hover:!bg-emerald-50"
                />
              </Tooltip>
              <Tooltip title="Từ chối yêu cầu">
                <Button
                  type="text"
                  size="small"
                  icon={<CloseCircleOutlined className="text-rose-600" />}
                  onClick={() => {
                    setSelectedCompany(record)
                    setRejectModalOpen(true)
                  }}
                  className="hover:!bg-rose-50"
                />
              </Tooltip>
            </>
          )}

          {record.status === 'ACTIVE' && (
            <Tooltip title="Tạm khóa doanh nghiệp">
              <Button
                type="text"
                size="small"
                icon={<LockOutlined className="text-rose-600" />}
                onClick={() => {
                  setSelectedCompany(record)
                  setStatusModalOpen(true)
                }}
                className="hover:!bg-rose-50"
              />
            </Tooltip>
          )}

          {record.status === 'SUSPENDED' && (
            <Tooltip title="Kích hoạt lại">
              <Button
                type="text"
                size="small"
                icon={<UnlockOutlined className="text-emerald-600" />}
                onClick={() => {
                  setSelectedCompany(record)
                  setStatusModalOpen(true)
                }}
                className="hover:!bg-emerald-50"
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-5">
      {/* Tiêu đề trang chuẩn /unslop: không emoji thừa, câu từ chính xác */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight">
            Quản lý khách hàng doanh nghiệp
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Kiểm soát danh sách công ty khách hàng, xét duyệt đơn đăng ký dùng thử và quản lý tài khoản SaaS
          </p>
        </div>
        <div>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => loadData()}
            loading={loading}
            className="text-xs font-medium"
          >
            Làm mới
          </Button>
        </div>
      </div>

      {/* Metric Cards - 4px grid, graphite neutrals, tabular-nums */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-2xs">
          <div className="text-[11px] font-medium text-slate-500">Tổng số doanh nghiệp</div>
          <div className="text-2xl font-bold font-mono tabular-nums text-slate-900 mt-1.5">
            {totalElements}
          </div>
        </div>
        <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-2xs">
          <div className="flex items-center gap-1.5 text-[11px] font-medium text-amber-700">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            <span>Chờ phê duyệt</span>
          </div>
          <div className="text-2xl font-bold font-mono tabular-nums text-amber-700 mt-1.5">
            {pendingCount}
          </div>
        </div>
        <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-2xs">
          <div className="flex items-center gap-1.5 text-[11px] font-medium text-emerald-700">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            <span>Đang hoạt động</span>
          </div>
          <div className="text-2xl font-bold font-mono tabular-nums text-emerald-700 mt-1.5">
            {activeCount}
          </div>
        </div>
        <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-2xs">
          <div className="flex items-center gap-1.5 text-[11px] font-medium text-rose-700">
            <span className="w-2 h-2 rounded-full bg-rose-500" />
            <span>Tạm khóa</span>
          </div>
          <div className="text-2xl font-bold font-mono tabular-nums text-rose-700 mt-1.5">
            {suspendedCount}
          </div>
        </div>
      </div>

      {/* Khu vực Bảng dữ liệu & Thanh công cụ lọc */}
      <div className="bg-white rounded-xl border border-slate-200/80 shadow-2xs overflow-hidden">
        {/* Toolbar: Tabs lọc trạng thái + Tìm kiếm từ khóa */}
        <div className="p-4 border-b border-slate-100 flex flex-col md:flex-row md:items-center justify-between gap-3">
          <Radio.Group
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value)
              setCurrentPage(1)
            }}
            optionType="button"
            buttonStyle="solid"
            size="middle"
            className="text-xs"
          >
            <Radio.Button value="ALL">Tất cả ({totalElements})</Radio.Button>
            <Radio.Button value="PENDING">Chờ duyệt</Radio.Button>
            <Radio.Button value="ACTIVE">Đang hoạt động</Radio.Button>
            <Radio.Button value="SUSPENDED">Tạm khóa</Radio.Button>
          </Radio.Group>

          <div className="flex items-center gap-2 max-w-sm w-full">
            <Input
              placeholder="Tìm theo tên, mã hoặc email..."
              prefix={<SearchOutlined className="text-slate-400 text-xs" />}
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onPressEnter={handleSearch}
              allowClear
              className="text-xs rounded-lg"
            />
            <Button
              type="primary"
              onClick={handleSearch}
              className="bg-slate-900 hover:!bg-slate-800 text-xs font-medium rounded-lg"
            >
              Tìm
            </Button>
          </div>
        </div>

        {/* Table danh sách */}
        <Table
          columns={columns}
          dataSource={companies}
          rowKey="id"
          loading={loading}
          pagination={{
            current: currentPage,
            pageSize,
            total: totalElements,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            onChange: (page, size) => {
              setCurrentPage(page)
              setPageSize(size)
            },
            showTotal: (total) => (
              <span className="text-xs text-slate-500 font-mono">
                Tổng cộng {total} doanh nghiệp
              </span>
            ),
          }}
          scroll={{ x: 1000 }}
          className="[&_.ant-table-thead_th]:!bg-slate-50/80 [&_.ant-table-thead_th]:!text-slate-600 [&_.ant-table-thead_th]:!font-semibold [&_.ant-table-thead_th]:!text-xs [&_.ant-table-tbody_td]:!py-3"
        />
      </div>

      {/* Drawer xem chi tiết */}
      <CompanyDetailDrawer
        company={selectedCompany}
        open={detailDrawerOpen}
        onClose={() => {
          setDetailDrawerOpen(false)
          setSelectedCompany(null)
        }}
        onApprove={(c) => {
          setSelectedCompany(c)
          setApproveModalOpen(true)
        }}
        onReject={(c) => {
          setSelectedCompany(c)
          setRejectModalOpen(true)
        }}
        onToggleStatus={(c) => {
          setSelectedCompany(c)
          setStatusModalOpen(true)
        }}
      />

      {/* Modal Phê duyệt */}
      <ApproveCompanyModal
        company={selectedCompany}
        open={approveModalOpen}
        onClose={() => {
          setApproveModalOpen(false)
          setSelectedCompany(null)
        }}
        onSuccess={() => {
          loadData()
        }}
      />

      {/* Modal Từ chối */}
      <RejectCompanyModal
        company={selectedCompany}
        open={rejectModalOpen}
        onClose={() => {
          setRejectModalOpen(false)
          setSelectedCompany(null)
        }}
        onSuccess={() => {
          loadData()
        }}
      />

      {/* Modal Khóa / Mở khóa */}
      <CompanyStatusConfirmModal
        company={selectedCompany}
        open={statusModalOpen}
        onClose={() => {
          setStatusModalOpen(false)
          setSelectedCompany(null)
        }}
        onSuccess={() => {
          loadData()
        }}
      />
    </div>
  )
}
