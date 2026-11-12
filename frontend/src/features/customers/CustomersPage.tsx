import {
  DeleteOutlined,
  DollarCircleOutlined,
  EditOutlined,
  EyeOutlined,
  FileExcelOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  UnlockOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Col,
  Input,
  Popconfirm,
  Progress,
  Row,
  Select,
  Space,
  Table,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../services/api'
import ForbiddenResult from '../../components/ForbiddenResult'
import { useCan } from '../auth/useCan'
import AiImportButton from '../ai/components/AiImportButton'
import CustomerDetailModal from './components/CustomerDetailModal'
import CustomerFormModal from './components/CustomerFormModal'
import CustomerMetricsCards from './components/CustomerMetricsCards'
import RecordCustomerTransactionModal from './components/RecordCustomerTransactionModal'
import * as customerApi from './customerApi'
import type {
  CreateCustomerPayload,
  Customer,
  CustomerDebtSummary,
  CustomerType,
  RecordCustomerTransactionPayload,
  UpdateCustomerPayload,
} from './types'

const formatCurrency = (val?: number): string =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val ?? 0)

export default function CustomersPage() {
  const { message, modal } = App.useApp()

  // Phân quyền theo bảng quyền dùng chung (features/auth/permissions.ts, khớp backend)
  const can = useCan()
  const canRead = can('customer.read')
  const canCreate = can('customer.write')
  const canEdit = can('customer.write')
  const canDelete = can('customer.write')
  // Khóa, mở khóa công nợ là sửa khách hàng (PUT)
  const canToggleLock = can('customer.write')
  const canRecordTransaction = can('customer.transaction')

  // State danh sách
  const [customers, setCustomers] = useState<Customer[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  // Filters
  const [keyword, setKeyword] = useState<string>('')
  const [customerType, setCustomerType] = useState<CustomerType | undefined>(undefined)
  const [active, setActive] = useState<boolean | undefined>(undefined)
  const [debtLocked, setDebtLocked] = useState<boolean | undefined>(undefined)
  const [filterOverLimit, setFilterOverLimit] = useState<boolean>(false)

  // Metrics Summary
  const [summary, setSummary] = useState<CustomerDebtSummary | null>(null)
  const [summaryLoading, setSummaryLoading] = useState(false)

  // Modals state
  const [isFormModalOpen, setIsFormModalOpen] = useState(false)
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null)
  const [formSubmitting, setFormSubmitting] = useState(false)

  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false)
  const [detailCustomer, setDetailCustomer] = useState<Customer | null>(null)

  const [isRecordModalOpen, setIsRecordModalOpen] = useState(false)
  const [selectedCustomerForRecord, setSelectedCustomerForRecord] = useState<Customer | null>(null)
  const [recordSubmitting, setRecordSubmitting] = useState(false)

  const [exporting, setExporting] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const reloadData = () => setRefreshKey((k) => k + 1)

  // Tải danh sách khách hàng
  useEffect(() => {
    if (!canRead) return
    let ignore = false
    const fetchList = async () => {
      setLoading(true)
      try {
        const data = await customerApi.fetchCustomers({
          keyword: keyword.trim() || undefined,
          type: customerType,
          active,
          debtLocked,
          overLimit: filterOverLimit ? true : undefined,
          page,
          size: pageSize,
        })
        if (!ignore) {
          setCustomers(data.content || [])
          setTotal(data.totalElements || 0)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách khách hàng'))
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }

    void fetchList()
    return () => {
      ignore = true
    }
  }, [canRead, keyword, customerType, active, debtLocked, filterOverLimit, page, pageSize, refreshKey, message])

  // Tải báo cáo tóm tắt công nợ (FR14)
  useEffect(() => {
    if (!canRead) return
    let ignore = false
    const fetchMetrics = async () => {
      setSummaryLoading(true)
      try {
        const data = await customerApi.fetchCustomerSummary()
        if (!ignore) {
          setSummary(data)
        }
      } catch {
        // Tránh gián đoạn giao diện nếu lỗi gọi summary
      } finally {
        if (!ignore) {
          setSummaryLoading(false)
        }
      }
    }

    void fetchMetrics()
    return () => {
      ignore = true
    }
  }, [canRead, refreshKey])

  // Xử lý tạo mới hoặc sửa khách hàng
  const handleSaveCustomer = async (
    payload: CreateCustomerPayload | UpdateCustomerPayload
  ) => {
    setFormSubmitting(true)
    try {
      if (editingCustomer) {
        await customerApi.updateCustomer(
          editingCustomer.id,
          payload as UpdateCustomerPayload
        )
        message.success('Cập nhật hồ sơ khách hàng thành công!')
      } else {
        await customerApi.createCustomer(payload as CreateCustomerPayload)
        message.success('Tạo mới hồ sơ khách hàng thành công!')
      }
      setIsFormModalOpen(false)
      setEditingCustomer(null)
      reloadData()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể lưu hồ sơ khách hàng'))
    } finally {
      setFormSubmitting(false)
    }
  }

  // Xử lý xóa mềm khách hàng
  const handleDeleteCustomer = async (id: number) => {
    try {
      await customerApi.deleteCustomer(id)
      message.success('Đã chuyển khách hàng sang trạng thái ngưng hoạt động')
      reloadData()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể xóa khách hàng'))
    }
  }

  // Khóa / Mở khóa nợ nhanh (Toggle Lock)
  const handleToggleDebtLock = (customer: Customer) => {
    const nextLocked = !customer.debtLocked
    modal.confirm({
      title: nextLocked
        ? `Xác nhận KHÓA NỢ khách hàng: ${customer.name}?`
        : `Xác nhận MỞ KHÓA NỢ khách hàng: ${customer.name}?`,
      icon: nextLocked ? (
        <LockOutlined className="text-red-500" />
      ) : (
        <UnlockOutlined className="text-emerald-500" />
      ),
      content: nextLocked
        ? 'Khi bị khóa nợ, hệ thống sẽ CHẶN toàn bộ các đơn bán hàng SO mới của khách hàng này cho tới khi hoàn tất thanh toán nợ quá hạn!'
        : 'Sau khi mở khóa, khách hàng có thể tiếp tục tạo đơn bán hàng nếu công nợ còn trong hạn mức cho phép.',
      okText: nextLocked ? 'Khóa nợ ngay' : 'Mở khóa nợ',
      okType: nextLocked ? 'danger' : 'primary',
      cancelText: 'Hủy bỏ',
      onOk: async () => {
        try {
          await customerApi.updateCustomer(customer.id, {
            name: customer.name,
            customerType: customer.customerType,
            taxCode: customer.taxCode,
            contactName: customer.contactName,
            email: customer.email,
            phone: customer.phone,
            address: customer.address,
            paymentTerms: customer.paymentTerms,
            creditLimit: customer.creditLimit,
            notes: customer.notes,
            active: customer.active,
            debtLocked: nextLocked,
          })
          message.success(
            nextLocked
              ? `Đã khóa nợ khách hàng ${customer.name}`
              : `Đã mở khóa nợ cho khách hàng ${customer.name}`
          )
          reloadData()
        } catch (err) {
          message.error(getApiErrorMessage(err, 'Không thể cập nhật trạng thái khóa nợ'))
        }
      },
    })
  }

  // Ghi nhận giao dịch công nợ
  const handleRecordTransaction = async (
    payload: RecordCustomerTransactionPayload
  ) => {
    if (!selectedCustomerForRecord) return
    setRecordSubmitting(true)
    try {
      await customerApi.recordCustomerTransaction(
        selectedCustomerForRecord.id,
        payload
      )
      message.success('Đã ghi nhận giao dịch công nợ thành công!')
      setIsRecordModalOpen(false)
      setSelectedCustomerForRecord(null)
      reloadData()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể ghi nhận giao dịch công nợ'))
    } finally {
      setRecordSubmitting(false)
    }
  }

  // Xuất file Excel toàn bộ dữ liệu có lặp phân trang an toàn
  const handleExportExcel = async () => {
    setExporting(true)
    try {
      const allCustomers: Customer[] = []
      let currentPage = 0
      const exportPageSize = 100
      let hasNext = true

      while (hasNext) {
        const res = await customerApi.fetchCustomers({
          keyword: keyword.trim() || undefined,
          type: customerType,
          active,
          debtLocked,
          overLimit: filterOverLimit ? true : undefined,
          page: currentPage,
          size: exportPageSize,
        })

        const content = res.content || []
        allCustomers.push(...content)

        if (res.last || content.length < exportPageSize || allCustomers.length >= (res.totalElements || 0)) {
          hasNext = false
        } else {
          currentPage++
        }
      }

      if (allCustomers.length === 0) {
        message.warning('Không có dữ liệu khách hàng để xuất Excel')
        return
      }

      const rows = allCustomers.map((c, index) => {
        const isOver = c.creditLimit > 0 && c.currentDebt > c.creditLimit
        const isNear = c.creditLimit > 0 && c.currentDebt >= c.creditLimit * 0.9 && !isOver
        let debtStatusText = 'An toàn'
        if (c.debtLocked) debtStatusText = 'BỊ KHÓA NỢ'
        else if (isOver) debtStatusText = 'VƯỢT HẠN MỨC'
        else if (isNear) debtStatusText = 'CẬN HẠN MỨC (>=90%)'
        else if (c.creditLimit === 0) debtStatusText = 'Thanh toán ngay'

        return {
          STT: index + 1,
          'Mã khách hàng': c.code,
          'Tên khách hàng': c.name,
          'Phân loại': c.customerType === 'WHOLESALE' ? 'Khách sỉ / Đại lý' : 'Khách lẻ',
          'Mã số thuế': c.taxCode || '',
          'Người đại diện': c.contactName || '',
          'Số điện thoại': c.phone || '',
          Email: c.email || '',
          'Địa chỉ': c.address || '',
          'Điều khoản TT': c.paymentTerms || '',
          'Hạn mức tín dụng (VNĐ)': c.creditLimit,
          'Công nợ hiện tại (VNĐ)': c.currentDebt,
          'Hạn mức khả dụng (VNĐ)': c.availableCredit ?? Math.max(0, c.creditLimit - c.currentDebt),
          'Cảnh báo công nợ': debtStatusText,
          'Khóa nợ': c.debtLocked ? 'ĐÃ KHÓA' : 'Bình thường',
          'Trạng thái': c.active ? 'Đang hợp tác' : 'Ngưng hợp tác',
          'Ghi chú': c.notes || '',
        }
      })

      const worksheet = XLSX.utils.json_to_sheet(rows)
      const workbook = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(workbook, worksheet, 'KhachHang')
      const fileName = `Danh_Sach_Khach_Hang_${new Date().toISOString().slice(0, 10)}.xlsx`
      XLSX.writeFile(workbook, fileName)
      message.success(`Đã xuất thành công ${allCustomers.length} khách hàng ra file Excel!`)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi xuất file Excel'))
    } finally {
      setExporting(false)
    }
  }

  // Xử lý click thẻ Metric để lọc nhanh
  const handleSelectFilterCard = (filter: 'ALL' | 'OVER_LIMIT' | 'DEBT_LOCKED' | 'ACTIVE') => {
    if (filter === 'ALL') {
      setCustomerType(undefined)
      setActive(undefined)
      setDebtLocked(undefined)
      setFilterOverLimit(false)
    } else if (filter === 'ACTIVE') {
      setActive(true)
      setDebtLocked(undefined)
      setFilterOverLimit(false)
    } else if (filter === 'OVER_LIMIT') {
      setActive(undefined)
      setDebtLocked(undefined)
      setFilterOverLimit(true)
    } else if (filter === 'DEBT_LOCKED') {
      setActive(undefined)
      setDebtLocked(true)
      setFilterOverLimit(false)
    }
    setPage(0)
  }

  // Cột bảng danh sách khách hàng
  const columns: ColumnsType<Customer> = [
    {
      title: 'Mã & Tên khách hàng',
      key: 'name',
      width: 250,
      render: (_, r) => (
        <div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => {
                setDetailCustomer(r)
                setIsDetailModalOpen(true)
              }}
              className="text-left font-medium text-slate-900 hover:text-blue-600 cursor-pointer text-sm"
            >
              {r.name}
            </button>
          </div>
          <div className="flex items-center gap-2 mt-1">
            <span className="text-xs text-slate-700 bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200/60 tabular-nums">
              {r.code}
            </span>
            <span className="text-[11px] text-slate-600 bg-slate-100 px-1.5 py-0.5 rounded">
              {r.customerType === 'WHOLESALE' ? 'Khách sỉ / Đại lý' : 'Khách lẻ'}
            </span>
          </div>
          {r.taxCode && (
            <div className="text-[11px] text-slate-400 mt-0.5">MST: {r.taxCode}</div>
          )}
        </div>
      ),
    },
    {
      title: 'Liên hệ',
      key: 'contact',
      width: 180,
      render: (_, r) => (
        <div className="text-xs space-y-0.5">
          {r.contactName && <div className="font-medium text-slate-800">{r.contactName}</div>}
          {r.phone && <div className="text-slate-600 tabular-nums">{r.phone}</div>}
          {r.email && <div className="text-slate-400 truncate max-w-[160px]">{r.email}</div>}
          {!r.contactName && !r.phone && !r.email && (
            <span className="text-slate-300">-</span>
          )}
        </div>
      ),
    },
    {
      title: 'Hạn mức tín dụng',
      dataIndex: 'creditLimit',
      key: 'creditLimit',
      width: 160,
      align: 'right',
      render: (val: number, r) => (
        <div>
          <div className="text-xs font-semibold text-slate-800 tabular-nums">
            {formatCurrency(val)}
          </div>
          <div className="text-[11px] text-slate-400">
            {r.paymentTerms ? `Điều khoản: ${r.paymentTerms}` : 'Chưa đặt điều khoản'}
          </div>
        </div>
      ),
    },
    {
      title: 'Công nợ hiện tại',
      key: 'currentDebt',
      width: 160,
      align: 'right',
      render: (_, r) => {
        const isOver = r.creditLimit > 0 && r.currentDebt > r.creditLimit
        return (
          <div>
            <div
              className={`text-xs font-semibold tabular-nums ${
                isOver || r.debtLocked ? 'text-red-600' : 'text-slate-900'
              }`}
            >
              {formatCurrency(r.currentDebt)}
            </div>
            {r.creditLimit > 0 && (
              <div className="text-[11px] text-slate-400 tabular-nums">
                Còn lại: {formatCurrency(Math.max(0, r.creditLimit - r.currentDebt))}
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Cảnh báo hạn mức',
      key: 'warningStatus',
      width: 190,
      render: (_, r) => {
        const limit = r.creditLimit || 0
        const debt = r.currentDebt || 0
        const isOver = limit > 0 && debt > limit
        const isNear = limit > 0 && debt >= limit * 0.9 && !isOver
        const percent = limit > 0 ? Math.min(Math.round((debt / limit) * 100), 100) : 0
        const rawPercent = limit > 0 ? ((debt / limit) * 100).toFixed(0) : '0'

        if (r.debtLocked) {
          return (
            <div>
              <span className="inline-flex items-center gap-1.5 text-xs text-red-700 font-medium">
                <span className="w-1.5 h-1.5 rounded-full bg-red-600" />
                Đang khóa nợ
              </span>
              <div className="text-[11px] text-red-500 font-normal mt-0.5">
                Chặn đơn bán hàng
              </div>
            </div>
          )
        }

        if (isOver) {
          return (
            <div>
              <span className="inline-flex items-center gap-1.5 text-xs text-red-600 font-medium">
                <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
                Vượt hạn mức ({rawPercent}%)
              </span>
              <Progress
                percent={percent}
                size="small"
                status="exception"
                showInfo={false}
                className="mt-1 m-0"
              />
            </div>
          )
        }

        if (isNear) {
          return (
            <div>
              <span className="inline-flex items-center gap-1.5 text-xs text-amber-600 font-medium">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                Cận hạn mức ({rawPercent}%)
              </span>
              <Progress
                percent={percent}
                size="small"
                strokeColor="#f59e0b"
                showInfo={false}
                className="mt-1 m-0"
              />
            </div>
          )
        }

        if (limit === 0) {
          return (
            <span className="text-xs text-slate-500">
              Thanh toán ngay
            </span>
          )
        }

        return (
          <div>
            <span className="inline-flex items-center gap-1.5 text-xs text-emerald-600">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
              An toàn ({rawPercent}%)
            </span>
            <Progress
              percent={percent}
              size="small"
              strokeColor="#10b981"
              showInfo={false}
              className="mt-1 m-0"
            />
          </div>
        )
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      key: 'active',
      width: 120,
      align: 'center',
      render: (val: boolean) => (
        <span className="inline-flex items-center gap-1.5 text-xs text-slate-600">
          <span className={`w-1.5 h-1.5 rounded-full ${val ? 'bg-emerald-500' : 'bg-slate-400'}`} />
          {val ? 'Hoạt động' : 'Tạm dừng'}
        </span>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 170,
      fixed: 'right',
      render: (_, r) => (
        <Space size={2}>
          <Tooltip title="Xem chi tiết & Kiểm tra hạn mức">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined className="text-slate-600" />}
              onClick={() => {
                setDetailCustomer(r)
                setIsDetailModalOpen(true)
              }}
            />
          </Tooltip>

          {canEdit && (
            <Tooltip title="Chỉnh sửa hồ sơ & Hạn mức">
              <Button
                type="text"
                size="small"
                icon={<EditOutlined className="text-slate-600" />}
                onClick={() => {
                  setEditingCustomer(r)
                  setIsFormModalOpen(true)
                }}
              />
            </Tooltip>
          )}

          {canToggleLock && (
            <Tooltip title={r.debtLocked ? 'Mở khóa nợ cho khách' : 'Khóa nợ chặn đơn bán'}>
              <Button
                type="text"
                size="small"
                icon={
                  r.debtLocked ? (
                    <UnlockOutlined className="text-slate-600" />
                  ) : (
                    <LockOutlined className="text-slate-600" />
                  )
                }
                onClick={() => handleToggleDebtLock(r)}
              />
            </Tooltip>
          )}

          {canRecordTransaction && (
            <Tooltip title="Ghi nhận phát sinh nợ / Thu tiền">
              <Button
                type="text"
                size="small"
                icon={<DollarCircleOutlined className="text-slate-600" />}
                onClick={() => {
                  setSelectedCustomerForRecord(r)
                  setIsRecordModalOpen(true)
                }}
              />
            </Tooltip>
          )}

          {canDelete && r.active && (
            <Popconfirm
              title="Ngưng hợp tác với khách hàng?"
              description="Hồ sơ khách hàng sẽ chuyển sang trạng thái ngưng hoạt động."
              onConfirm={() => handleDeleteCustomer(r.id)}
              okText="Đồng ý"
              cancelText="Hủy"
              okButtonProps={{ danger: true }}
            >
              <Tooltip title="Ngưng hoạt động">
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                />
              </Tooltip>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  if (!canRead) {
    return (
      <ForbiddenResult description="Danh mục khách hàng dành cho Quản trị viên, Quản lý kho, Bán hàng và Kế toán." />
    )
  }

  return (
    <div className="space-y-6">
      {/* HEADER */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900 tracking-tight">
            Khách hàng
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Quản lý danh sách khách hàng, cấu hình hạn mức tín dụng và kiểm soát công nợ phải thu.
          </p>
        </div>

        <Space wrap>
          <Button
            icon={<ReloadOutlined className="text-slate-500" />}
            onClick={reloadData}
            loading={loading}
            className="text-slate-700"
          >
            Làm mới
          </Button>

          <Button
            icon={<FileExcelOutlined className="text-slate-500" />}
            onClick={handleExportExcel}
            loading={exporting}
            className="text-slate-700"
          >
            Xuất Excel
          </Button>

          <AiImportButton dataType="CUSTOMER" />

          {canCreate && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingCustomer(null)
                setIsFormModalOpen(true)
              }}
            >
              Thêm khách hàng
            </Button>
          )}
        </Space>
      </div>

      {/* METRICS CARDS */}
      <CustomerMetricsCards
        summary={summary}
        loading={summaryLoading}
        activeFilter={
          debtLocked ? 'DEBT_LOCKED' : filterOverLimit ? 'OVER_LIMIT' : active ? 'ACTIVE' : 'ALL'
        }
        onSelectFilter={handleSelectFilterCard}
      />

      {/* SEARCH, FILTERS & TABLE */}
      <div className="bg-white border border-slate-200/80 rounded-lg p-5">
        <Row gutter={[12, 12]} align="middle" className="mb-4">
          <Col xs={24} sm={10} md={8}>
            <Input.Search
              placeholder="Tìm theo Mã, Tên, Số điện thoại..."
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setPage(0)
              }}
              onSearch={() => setPage(0)}
              allowClear
            />
          </Col>

          <Col xs={12} sm={6} md={4}>
            <Select
              className="w-full"
              placeholder="Loại khách hàng"
              allowClear
              value={customerType}
              onChange={(val) => {
                setCustomerType(val)
                setPage(0)
              }}
              options={[
                { label: 'Tất cả loại', value: undefined },
                { label: 'Khách sỉ / Đại lý', value: 'WHOLESALE' },
                { label: 'Khách lẻ', value: 'RETAIL' },
              ]}
            />
          </Col>

          <Col xs={12} sm={8} md={5}>
            <Select
              className="w-full"
              placeholder="Cảnh báo công nợ"
              allowClear
              value={
                debtLocked ? 'LOCKED' : filterOverLimit ? 'OVER_LIMIT' : undefined
              }
              onChange={(val) => {
                if (val === 'LOCKED') {
                  setDebtLocked(true)
                  setFilterOverLimit(false)
                } else if (val === 'OVER_LIMIT') {
                  setDebtLocked(undefined)
                  setFilterOverLimit(true)
                } else {
                  setDebtLocked(undefined)
                  setFilterOverLimit(false)
                }
                setPage(0)
              }}
              options={[
                { label: 'Tất cả cảnh báo nợ', value: undefined },
                { label: 'Vượt hạn mức nợ', value: 'OVER_LIMIT' },
                { label: 'Đang bị khóa nợ', value: 'LOCKED' },
              ]}
            />
          </Col>

          <Col xs={12} sm={6} md={4}>
            <Select
              className="w-full"
              placeholder="Trạng thái"
              allowClear
              value={active}
              onChange={(val) => {
                setActive(val)
                setPage(0)
              }}
              options={[
                { label: 'Tất cả trạng thái', value: undefined },
                { label: 'Đang hợp tác', value: true },
                { label: 'Ngưng hợp tác', value: false },
              ]}
            />
          </Col>

          <Col xs={12} sm={6} md={3}>
            <Button
              className="w-full"
              onClick={() => {
                setKeyword('')
                setCustomerType(undefined)
                setActive(undefined)
                setDebtLocked(undefined)
                setFilterOverLimit(false)
                setPage(0)
              }}
            >
              Đặt lại
            </Button>
          </Col>
        </Row>

        <Table<Customer>
          columns={columns}
          dataSource={customers}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            onChange: (p, s) => {
              setPage(p - 1)
              setPageSize(s)
            },
            showTotal: (totalCount) => `Tổng số ${totalCount} khách hàng`,
          }}
          scroll={{ x: 1100 }}
        />
      </div>

      {/* MODAL THÊM / SỬA KHÁCH HÀNG */}
      <CustomerFormModal
        open={isFormModalOpen}
        initialData={editingCustomer}
        submitting={formSubmitting}
        onCancel={() => {
          setIsFormModalOpen(false)
          setEditingCustomer(null)
        }}
        onSubmit={handleSaveCustomer}
      />

      {/* MODAL CHI TIẾT & CREDIT SIMULATOR */}
      <CustomerDetailModal
        open={isDetailModalOpen}
        customer={detailCustomer}
        onClose={() => {
          setIsDetailModalOpen(false)
          setDetailCustomer(null)
        }}
        onOpenRecordTransaction={(c) => {
          setSelectedCustomerForRecord(c)
          setIsRecordModalOpen(true)
        }}
        canRecordTransaction={canRecordTransaction}
      />

      {/* MODAL GHI NHẬN BIẾN ĐỘNG CÔNG NỢ */}
      <RecordCustomerTransactionModal
        open={isRecordModalOpen}
        customer={selectedCustomerForRecord}
        submitting={recordSubmitting}
        onCancel={() => {
          setIsRecordModalOpen(false)
          setSelectedCustomerForRecord(null)
        }}
        onSubmit={handleRecordTransaction}
      />
    </div>
  )
}
