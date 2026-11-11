import {
  CheckCircleOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  FileExcelOutlined,
  FileTextOutlined,
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
import { getStoredUser } from '../../../utils/storage'
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
  const currentUser = getStoredUser()
  const canRecordPayment =
    currentUser?.roles?.some((r) => ['ADMIN', 'ACCOUNTANT'].includes(r)) ?? false

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
      title: 'Mã Hóa Đơn',
      dataIndex: 'billNumber',
      key: 'billNumber',
      width: 150,
      render: (val, r) => (
        <a
          onClick={() => {
            setSelectedBill(r)
            setIsDetailOpen(true)
          }}
          className="font-mono font-semibold text-blue-600 hover:underline"
        >
          {val}
        </a>
      ),
    },
    {
      title: 'Phiếu Nhập (GRN)',
      dataIndex: 'grnNumber',
      key: 'grnNumber',
      width: 150,
      render: (val) =>
        val ? (
          <span className="font-mono text-xs text-emerald-700 font-medium">
            {val}
          </span>
        ) : (
          <span className="text-slate-400">-</span>
        ),
    },
    {
      title: 'Đơn Mua (PO)',
      dataIndex: 'poNumber',
      key: 'poNumber',
      width: 150,
      render: (val) =>
        val ? (
          <span className="font-mono text-xs text-slate-700">{val}</span>
        ) : (
          <span className="text-slate-400">-</span>
        ),
    },
    {
      title: 'Nhà cung cấp',
      dataIndex: 'supplierName',
      key: 'supplierName',
      render: (val) => <span className="font-medium text-slate-800">{val || 'N/A'}</span>,
    },
    {
      title: 'Ngày HĐ',
      dataIndex: 'billDate',
      key: 'billDate',
      width: 110,
      render: (val) => (val ? dayjs(val).format('DD/MM/YYYY') : '-'),
    },
    {
      title: 'Hạn TT',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 110,
      render: (val, r) => {
        if (!val) return <span className="text-slate-400">-</span>
        const isOverdue =
          r.status !== 'PAID' && r.status !== 'CANCELLED' && dayjs(val).isBefore(dayjs(), 'day')
        return (
          <span className={isOverdue ? 'text-red-600 font-bold' : 'text-slate-600'}>
            {dayjs(val).format('DD/MM/YYYY')}
          </span>
        )
      },
    },
    {
      title: 'Tổng tiền (VNĐ)',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 140,
      align: 'right',
      render: (val) => <span className="font-semibold">{formatCurrency(val)}</span>,
    },
    {
      title: 'Đã trả',
      dataIndex: 'paidAmount',
      key: 'paidAmount',
      width: 130,
      align: 'right',
      render: (val) => <span className="text-emerald-700">{formatCurrency(val)}</span>,
    },
    {
      title: 'Còn nợ (VNĐ)',
      dataIndex: 'remainingAmount',
      key: 'remainingAmount',
      width: 140,
      align: 'right',
      render: (val, r) => (
        <span
          className={
            val > 0
              ? r.status === 'UNPAID'
                ? 'font-bold text-red-600'
                : 'font-semibold text-amber-700'
              : 'text-slate-400'
          }
        >
          {formatCurrency(val)}
        </span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      align: 'center',
      render: (status: PurchaseBillStatus) => {
        const cfg = BILL_STATUS_CONFIG[status] || {
          label: status,
          color: 'default',
        }
        return (
          <Tag color={cfg.color} className="font-semibold text-xs m-0">
            {cfg.label}
          </Tag>
        )
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 160,
      fixed: 'right',
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
                icon={<EyeOutlined />}
                onClick={() => {
                  setSelectedBill(r)
                  setIsDetailOpen(true)
                }}
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
                className="bg-emerald-600 hover:bg-emerald-700 text-xs"
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
          <Card size="small" className="border-l-4 border-l-blue-500 shadow-2xs">
            <div className="flex justify-between items-start">
              <div>
                <span className="text-xs text-slate-500 font-medium">Tổng hóa đơn mua</span>
                <div className="text-2xl font-bold text-slate-800 mt-1">
                  {summary ? formatNumber(summary.totalBills) : 0}
                </div>
              </div>
              <FileTextOutlined className="text-2xl text-blue-500 opacity-80" />
            </div>
            <div className="text-[11px] text-slate-500 mt-2">
              Chưa trả:{' '}
              <strong className="text-red-600 font-bold">
                {summary ? summary.unpaidBills : 0}
              </strong>{' '}
              | Trả 1 phần: {summary ? summary.partiallyPaidBills : 0} | Đã xong: {summary ? summary.paidBills : 0}
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-amber-500 shadow-2xs">
            <div className="flex justify-between items-start">
              <div>
                <span className="text-xs text-slate-500 font-medium">Tổng nợ phải trả NCC</span>
                <div className="text-xl font-bold text-amber-700 mt-1">
                  {summary ? formatCurrency(summary.totalPayablesAmount) : '0 đ'}
                </div>
              </div>
              <DollarOutlined className="text-2xl text-amber-500 opacity-80" />
            </div>
            <div className="text-[11px] text-slate-500 mt-2">
              Công nợ mua phát sinh từ các đợt nhận hàng GRN
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-emerald-500 shadow-2xs">
            <div className="flex justify-between items-start">
              <div>
                <span className="text-xs text-slate-500 font-medium">Đã thanh toán cho NCC</span>
                <div className="text-xl font-bold text-emerald-700 mt-1">
                  {summary ? formatCurrency(summary.totalPaidAmount) : '0 đ'}
                </div>
              </div>
              <CheckCircleOutlined className="text-2xl text-emerald-500 opacity-80" />
            </div>
            <div className="text-[11px] text-slate-500 mt-2">
              Tổng tiền mua hàng đã giải ngân thực tế
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-red-500 shadow-2xs">
            <div className="flex justify-between items-start">
              <div>
                <span className="text-xs text-slate-500 font-medium">Công nợ còn nợ lại</span>
                <div className="text-xl font-bold text-red-600 mt-1">
                  {summary ? formatCurrency(summary.totalRemainingAmount) : '0 đ'}
                </div>
              </div>
              <ExclamationCircleOutlined className="text-2xl text-red-500 opacity-80" />
            </div>
            <div className="text-[11px] text-slate-500 mt-2">
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
            className="text-emerald-700 hover:text-emerald-800"
          >
            Xuất Excel
          </Button>

          <Button
            icon={<ReloadOutlined />}
            onClick={reloadData}
            loading={loading || loadingSummary}
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
        scroll={{ x: 1300 }}
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
