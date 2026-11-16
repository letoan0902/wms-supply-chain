import {
  DollarOutlined,
  EyeOutlined,
  FileExcelOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency, formatNumber } from '../../../utils/format'
import { useCan } from '../../auth/useCan'
import * as supplierApi from '../../suppliers/supplierApi'
import type { Supplier } from '../../suppliers/types'
import * as purchaseBillsApi from '../purchaseBillsApi'
import {
  BILL_STATUS_CONFIG,
  type PurchaseBill,
  type PurchaseBillStatus,
  type PurchaseBillSummary,
} from '../purchaseBillTypes'
import PurchaseBillDetailModal from './PurchaseBillDetailModal'
import RecordPaymentModal from './RecordPaymentModal'

const { RangePicker } = DatePicker

interface PurchaseBillsTableProps {
  onBillUpdated?: () => void
}

export default function PurchaseBillsTable({ onBillUpdated }: PurchaseBillsTableProps) {
  const { message } = App.useApp()
  // Ghi thanh toán hóa đơn mua: ADMIN, Kế toán (features/auth/permissions.ts)
  const canRecordPayment = useCan()('purchaseBill.pay')

  // Danh sách hóa đơn & phân trang
  const [bills, setBills] = useState<PurchaseBill[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  // Bộ lọc
  const [searchInput, setSearchInput] = useState<string>('')
  const [keyword, setKeyword] = useState<string>('')
  const [selectedStatus, setSelectedStatus] = useState<PurchaseBillStatus | undefined>(undefined)
  const [selectedSupplierId, setSelectedSupplierId] = useState<number | undefined>(undefined)
  const [dateRange, setDateRange] = useState<[string | undefined, string | undefined]>([
    undefined,
    undefined,
  ])

  // Danh mục NCC phục vụ bộ lọc
  const [suppliers, setSuppliers] = useState<Supplier[]>([])

  // Thống kê tổng quan
  const [summary, setSummary] = useState<PurchaseBillSummary | null>(null)
  const [loadingSummary, setLoadingSummary] = useState(false)

  // Modal xem chi tiết & Ghi nhận thanh toán
  const [selectedBill, setSelectedBill] = useState<PurchaseBill | null>(null)
  const [isDetailOpen, setIsDetailOpen] = useState(false)

  const [billToPay, setBillToPay] = useState<PurchaseBill | null>(null)
  const [isPaymentOpen, setIsPaymentOpen] = useState(false)

  const [refreshKey, setRefreshKey] = useState(0)
  const reloadData = () => setRefreshKey((k) => k + 1)

  // Tải danh mục NCC
  useEffect(() => {
    let ignore = false
    const loadSuppliers = async () => {
      try {
        const res = await supplierApi.fetchSuppliers({ active: true, size: 100 })
        if (!ignore) {
          setSuppliers(res.content || [])
        }
      } catch (err) {
        console.error('Không thể tải NCC', err)
      }
    }
    void loadSuppliers()
    return () => {
      ignore = true
    }
  }, [])

  // Tải danh sách hóa đơn và báo cáo tổng hợp
  useEffect(() => {
    let ignore = false
    const fetchBillsAndSummary = async () => {
      setLoading(true)
      setLoadingSummary(true)
      try {
        const [billsRes, sumRes] = await Promise.all([
          purchaseBillsApi.fetchPurchaseBills({
            keyword: keyword ? keyword.trim() : undefined,
            status: selectedStatus,
            supplierId: selectedSupplierId,
            fromDate: dateRange[0],
            toDate: dateRange[1],
            page,
            size: pageSize,
          }),
          purchaseBillsApi.fetchPurchaseBillSummary(),
        ])
        if (!ignore) {
          setBills(billsRes.content || [])
          setTotal(billsRes.totalElements || 0)
          setSummary(sumRes)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách hóa đơn mua hàng'))
        }
      } finally {
        if (!ignore) {
          setLoading(false)
          setLoadingSummary(false)
        }
      }
    }

    void fetchBillsAndSummary()
    return () => {
      ignore = true
    }
  }, [page, pageSize, keyword, selectedStatus, selectedSupplierId, dateRange, refreshKey, message])

  const handleSearch = (val: string) => {
    setKeyword(val.trim())
    setPage(0)
  }

  const handleResetFilters = () => {
    setSearchInput('')
    setKeyword('')
    setSelectedStatus(undefined)
    setSelectedSupplierId(undefined)
    setDateRange([undefined, undefined])
    setPage(0)
  }

  // Xuất file Excel báo cáo
  const handleExportExcel = () => {
    if (bills.length === 0) {
      message.warning('Không có dữ liệu hóa đơn mua để xuất Excel!')
      return
    }

    const rows = bills.map((b, idx) => ({
      STT: idx + 1,
      'Mã Hóa Đơn': b.billNumber,
      'Phiếu Nhập Kho (GRN)': b.grnNumber || '',
      'Đơn Mua Hàng (PO)': b.poNumber || '',
      'Nhà Cung Cấp': b.supplierName || '',
      'Ngày Lập': b.billDate,
      'Hạn Thanh Toán': b.dueDate || '',
      'Tổng Tiền (VNĐ)': b.totalAmount,
      'Đã Thanh Toán (VNĐ)': b.paidAmount,
      'Còn Nợ Lại (VNĐ)': b.remainingAmount,
      'Trạng Thái': BILL_STATUS_CONFIG[b.status]?.label || b.status,
      'Ghi Chú': b.note || '',
    }))

    const ws = XLSX.utils.json_to_sheet(rows)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, 'HoaDonMuaHang')
    const fileName = `Danh_Sach_Hoa_Don_Mua_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`
    XLSX.writeFile(wb, fileName)
    message.success(`Đã xuất thành công file ${fileName}`)
  }

  const columns: ColumnsType<PurchaseBill> = [
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">#</span>,
      key: 'idx',
      width: 48,
      align: 'center',
      render: (_, __, idx) => (
        <span className="text-xs font-mono text-slate-400">{page * pageSize + idx + 1}</span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Mã hóa đơn</span>,
      dataIndex: 'billNumber',
      key: 'billNumber',
      width: 155,
      render: (val, r) => (
        <button
          type="button"
          onClick={() => {
            setSelectedBill(r)
            setIsDetailOpen(true)
          }}
          className="font-mono text-xs font-semibold text-blue-600 hover:text-blue-700 hover:underline cursor-pointer text-left transition-colors bg-blue-50/80 hover:bg-blue-100/80 px-2 py-0.5 rounded border border-blue-200/60"
        >
          {val}
        </button>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Phiếu nhập (GRN)</span>,
      dataIndex: 'grnNumber',
      key: 'grnNumber',
      width: 155,
      render: (val) =>
        val ? (
          <span className="font-mono text-xs text-slate-700 font-medium">
            {val}
          </span>
        ) : (
          <span className="text-slate-400">-</span>
        ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Đơn mua (PO)</span>,
      dataIndex: 'poNumber',
      key: 'poNumber',
      width: 155,
      render: (val) =>
        val ? (
          <span className="font-mono text-xs text-slate-700">{val}</span>
        ) : (
          <span className="text-slate-400">-</span>
        ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Nhà cung cấp</span>,
      dataIndex: 'supplierName',
      key: 'supplierName',
      width: 200,
      render: (val) => <span className="font-medium text-slate-900 text-xs leading-snug">{val || 'N/A'}</span>,
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Ngày HĐ</span>,
      dataIndex: 'billDate',
      key: 'billDate',
      width: 110,
      render: (val) => (
        <span className="text-xs text-slate-600">{val ? dayjs(val).format('DD/MM/YYYY') : '-'}</span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Hạn thanh toán</span>,
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 120,
      render: (val, r) => {
        if (!val) return <span className="text-slate-400 text-xs">-</span>
        const isOverdue =
          r.status !== 'PAID' && r.status !== 'CANCELLED' && dayjs(val).isBefore(dayjs(), 'day')
        return (
          <span className={`text-xs ${isOverdue ? 'text-rose-600 font-bold' : 'text-slate-600 font-medium'}`}>
            {dayjs(val).format('DD/MM/YYYY')}
          </span>
        )
      },
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Tổng tiền (VNĐ)</span>,
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 140,
      align: 'right',
      render: (val) => (
        <span className="font-mono font-semibold text-slate-900 text-xs sm:text-sm tabular-nums">
          {formatCurrency(val)}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Đã trả</span>,
      dataIndex: 'paidAmount',
      key: 'paidAmount',
      width: 130,
      align: 'right',
      render: (val) => (
        <span className="font-mono text-slate-900 font-bold text-sm tabular-nums">
          {formatCurrency(val)}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Còn nợ (VNĐ)</span>,
      dataIndex: 'remainingAmount',
      key: 'remainingAmount',
      width: 140,
      align: 'right',
      render: (val, r) => (
        <span
          className={`font-mono text-xs tabular-nums ${
            val > 0
              ? r.status === 'UNPAID'
                ? 'font-bold text-rose-600'
                : 'font-semibold text-amber-700'
              : 'text-slate-400'
          }`}
        >
          {formatCurrency(val)}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Trạng thái</span>,
      dataIndex: 'status',
      key: 'status',
      width: 130,
      align: 'center',
      render: (status: PurchaseBillStatus) => {
        const cfg = BILL_STATUS_CONFIG[status] || {
          label: status,
          color: 'default',
        }
        return (
          <Tag color={cfg.color} className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full border">
            {cfg.label}
          </Tag>
        )
      },
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Thao tác</span>,
      key: 'actions',
      width: 120,
      align: 'center',
      render: (_, r) => {
        const canPay =
          canRecordPayment &&
          r.status !== 'PAID' &&
          r.status !== 'CANCELLED' &&
          r.remainingAmount > 0
        return (
          <Space size="small">
            <Tooltip title="Xem chi tiết hóa đơn">
              <Button
                size="small"
                type="text"
                icon={<EyeOutlined />}
                onClick={() => {
                  setSelectedBill(r)
                  setIsDetailOpen(true)
                }}
                className="text-slate-500 hover:text-blue-600"
              />
            </Tooltip>

            {canPay && (
              <Button
                size="small"
                type="primary"
                icon={<DollarOutlined />}
                onClick={() => {
                  setBillToPay(r)
                  setIsPaymentOpen(true)
                }}
                className="text-xs font-medium rounded shadow-xs"
              >
                Trả tiền
              </Button>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <div className="space-y-4">
      {/* 1. Thẻ thống kê tổng quan hóa đơn mua & công nợ NCC (FR13) */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Tổng hóa đơn mua
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-slate-900 tracking-tight tabular-nums">
                {summary ? formatNumber(summary.totalBills) : 0}
              </span>
              <span className="text-xs text-slate-500 font-normal">hóa đơn</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
              Chưa trả: <strong className="text-rose-600">{summary ? summary.unpaidBills : 0}</strong> • Đã xong: {summary ? summary.paidBills : 0}
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Tổng nợ phải trả NCC
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-slate-900 tracking-tight tabular-nums truncate">
                {summary ? formatCurrency(summary.totalPayablesAmount) : '0 đ'}
              </span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
              Công nợ mua phát sinh từ phiếu GRN
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Đã thanh toán cho NCC
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-slate-900 tracking-tight tabular-nums truncate">
                {summary ? formatCurrency(summary.totalPaidAmount) : '0 đ'}
              </span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
              Tổng tiền mua hàng đã giải ngân
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Công nợ còn nợ lại
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-slate-900 tracking-tight tabular-nums truncate">
                {summary ? formatCurrency(summary.totalRemainingAmount) : '0 đ'}
              </span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
              Khoản phải thanh toán sắp tới hạn
            </div>
          </Card>
        </Col>
      </Row>

      {/* 2. Thanh công cụ & Bộ lọc */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 bg-white p-3 rounded-lg border border-slate-200">
        <div className="flex flex-wrap items-center gap-2">
          <Input.Search
            placeholder="Tìm theo Mã HĐ, GRN, PO..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onSearch={handleSearch}
            className="w-56"
            allowClear
          />

          <Select
            placeholder="Trạng thái thanh toán"
            value={selectedStatus}
            onChange={(val) => {
              setSelectedStatus(val)
              setPage(0)
            }}
            allowClear
            className="w-48"
            options={[
              { value: 'UNPAID', label: 'Chưa thanh toán' },
              { value: 'PARTIALLY_PAID', label: 'Thanh toán 1 phần' },
              { value: 'PAID', label: 'Đã thanh toán đủ' },
              { value: 'CANCELLED', label: 'Đã hủy' },
            ]}
          />

          <Select
            placeholder="Nhà cung cấp"
            value={selectedSupplierId}
            onChange={(val) => {
              setSelectedSupplierId(val)
              setPage(0)
            }}
            allowClear
            showSearch
            filterOption={(input, opt) =>
              String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
            className="w-52"
            options={suppliers.map((s) => ({
              value: s.id,
              label: `[${s.code}] ${s.name}`,
            }))}
          />

          <RangePicker
            format="DD/MM/YYYY"
            placeholder={['Từ ngày lập', 'Đến ngày lập']}
            onChange={(dates) => {
              if (dates && dates[0] && dates[1]) {
                setDateRange([
                  dates[0].format('YYYY-MM-DD'),
                  dates[1].format('YYYY-MM-DD'),
                ])
              } else {
                setDateRange([undefined, undefined])
              }
              setPage(0)
            }}
            className="w-64"
          />

          {(searchInput || keyword || selectedStatus || selectedSupplierId || dateRange[0]) && (
            <Button onClick={handleResetFilters}>Xóa lọc</Button>
          )}
        </div>

        <Space>
          <Button
            icon={<FileExcelOutlined />}
            onClick={handleExportExcel}
            className="rounded-md text-xs font-medium"
          >
            Xuất Excel
          </Button>

          <Button
            icon={<ReloadOutlined />}
            onClick={reloadData}
            loading={loading || loadingSummary}
            className="rounded-md text-xs font-medium"
          >
            Làm mới
          </Button>
        </Space>
      </div>

      {/* 3. Bảng danh sách Hóa đơn mua hàng */}
      <Table
        rowKey="id"
        columns={columns}
        dataSource={bills}
        loading={loading}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (totalCount) => `Tổng cộng ${totalCount} hóa đơn mua hàng`,
          onChange: (p, ps) => {
            setPage(p - 1)
            setPageSize(ps)
          },
        }}
      />

      {/* Modal Chi tiết Hóa đơn mua */}
      <PurchaseBillDetailModal
        open={isDetailOpen}
        bill={selectedBill}
        onClose={() => {
          setIsDetailOpen(false)
          setSelectedBill(null)
        }}
        onOpenPayment={(b) => {
          setIsDetailOpen(false)
          setBillToPay(b)
          setIsPaymentOpen(true)
        }}
      />

      {/* Modal Ghi nhận thanh toán */}
      <RecordPaymentModal
        open={isPaymentOpen}
        bill={billToPay}
        onClose={() => {
          setIsPaymentOpen(false)
          setBillToPay(null)
        }}
        onSuccess={() => {
          reloadData()
          if (onBillUpdated) onBillUpdated()
        }}
      />
    </div>
  )
}
