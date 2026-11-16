import {
  EyeOutlined,
  FileExcelOutlined,
  PlusOutlined,
  ReloadOutlined,
  ShopOutlined,
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
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import { useCan } from '../../auth/useCan'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../../services/api'
import { formatNumber } from '../../../utils/format'
import * as supplierApi from '../../suppliers/supplierApi'
import type { Supplier } from '../../suppliers/types'
import * as warehouseApi from '../../warehouses/warehouseApi'
import type { Warehouse } from '../../warehouses/types'
import * as grnApi from '../grnApi'
import {
  GRN_STATUS_CONFIG,
  type GoodsReceiptStatus,
  type GrnResponse,
  type GrnSummaryResponse,
} from '../grnTypes'
import GoodsReceiptDetailModal from './GoodsReceiptDetailModal'

const { Search } = Input
const { RangePicker } = DatePicker

interface GoodsReceiptTableProps {
  onOpenCreateGrn: (poId?: number) => void
}

export default function GoodsReceiptTable({ onOpenCreateGrn }: GoodsReceiptTableProps) {
  const can = useCan()
  const canCreateGrn = can('goodsReceipt.create')
  // Vai trò không phải ADMIN, ACCOUNTANT chưa được gán kho thì không đọc được danh sách kho (403)
  const canReadWarehouses = can('warehouse.read')
  const { message } = App.useApp()

  const [grnList, setGrnList] = useState<GrnResponse[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  // Filters
  const [keyword, setKeyword] = useState<string | undefined>(undefined)
  const [selectedStatus, setSelectedStatus] = useState<GoodsReceiptStatus | undefined>(undefined)
  const [selectedSupplierId, setSelectedSupplierId] = useState<number | undefined>(undefined)
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | undefined>(undefined)
  const [dateRange, setDateRange] = useState<[string | undefined, string | undefined]>([
    undefined,
    undefined,
  ])

  // Catalogs
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  // Metrics Summary
  const [summary, setSummary] = useState<GrnSummaryResponse | null>(null)
  const [loadingSummary, setLoadingSummary] = useState(false)
  const [exporting, setExporting] = useState(false)

  // Detail Modal
  const [selectedGrn, setSelectedGrn] = useState<GrnResponse | null>(null)
  const [isDetailOpen, setIsDetailOpen] = useState(false)

  const [refreshKey, setRefreshKey] = useState(0)
  const reloadData = () => setRefreshKey((k) => k + 1)

  // Tải danh mục lọc
  useEffect(() => {
    let ignore = false
    const loadCatalogs = async () => {
      try {
        const [supRes, whRes] = await Promise.all([
          supplierApi.fetchSuppliers({ active: true, size: 100 }),
          canReadWarehouses ? warehouseApi.fetchWarehouses({ active: true }) : Promise.resolve([]),
        ])
        if (!ignore) {
          setSuppliers(supRes.content || [])
          setWarehouses(whRes || [])
        }
      } catch (err) {
        console.error('Lỗi tải danh mục cho bảng GRN:', err)
      }
    }
    void loadCatalogs()
    return () => {
      ignore = true
    }
  }, [canReadWarehouses])

  // Tải danh sách GRN
  useEffect(() => {
    let ignore = false
    const fetchList = async () => {
      setLoading(true)
      try {
        const data = await grnApi.fetchGoodsReceipts({
          keyword,
          status: selectedStatus,
          supplierId: selectedSupplierId,
          warehouseId: selectedWarehouseId,
          fromReceiptDate: dateRange[0],
          toReceiptDate: dateRange[1],
          page,
          size: pageSize,
        })
        if (!ignore) {
          setGrnList(data.content || [])
          setTotal(data.totalElements || 0)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách phiếu nhập kho'))
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
  }, [
    keyword,
    selectedStatus,
    selectedSupplierId,
    selectedWarehouseId,
    dateRange,
    page,
    pageSize,
    refreshKey,
    message,
  ])

  // Tải summary metrics
  useEffect(() => {
    let ignore = false
    const loadSummary = async () => {
      setLoadingSummary(true)
      try {
        const res = await grnApi.fetchGoodsReceiptSummary()
        if (!ignore) {
          setSummary(res)
        }
      } catch (err) {
        console.error('Không thể tải tóm tắt GRN:', err)
      } finally {
        if (!ignore) {
          setLoadingSummary(false)
        }
      }
    }
    void loadSummary()
    return () => {
      ignore = true
    }
  }, [refreshKey])

  // Xuất Excel
  const handleExportExcel = async () => {
    try {
      setExporting(true)
      let curPage = 0
      let isLast = false
      let allGrns: GrnResponse[] = []

      while (!isLast) {
        const data = await grnApi.fetchGoodsReceipts({
          keyword,
          status: selectedStatus,
          supplierId: selectedSupplierId,
          warehouseId: selectedWarehouseId,
          fromReceiptDate: dateRange[0],
          toReceiptDate: dateRange[1],
          page: curPage,
          size: 100,
        })
        const items = data.content || []
        allGrns = allGrns.concat(items)
        isLast = Boolean(data.last || items.length === 0 || curPage >= (data.totalPages - 1))
        curPage++
      }

      if (allGrns.length === 0) {
        message.warning('Không có dữ liệu phiếu nhập kho để xuất Excel')
        return
      }

      const rows = allGrns.map((g, idx) => ({
        STT: idx + 1,
        'Mã Phiếu Nhập': g.grnNumber,
        'Mã Đơn PO': g.poNumber,
        'Nhà Cung Cấp': g.supplierName || `ID: ${g.supplierId}`,
        'Kho Tiếp Nhận': g.warehouseName || `Kho #${g.warehouseId}`,
        'Ngày Nhập Kho': g.receiptDate,
        'Người Tiếp Nhận': g.receivedBy || '',
        'SL Đạt Chuẩn': g.totalAcceptedQuantity,
        'SL Phế Phẩm/Hỏng': g.totalRejectedQuantity,
        'Trạng Thái': GRN_STATUS_CONFIG[g.status]?.label || g.status,
        'Ghi Chú': g.note || '',
      }))

      const worksheet = XLSX.utils.json_to_sheet(rows)
      const workbook = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(workbook, worksheet, 'Phieu_Nhap_Kho_GRN')
      XLSX.writeFile(workbook, `WMS_Phieu_Nhap_Kho_${new Date().toISOString().slice(0, 10)}.xlsx`)
      message.success(`Đã xuất Excel thành công ${allGrns.length} phiếu nhập kho!`)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi xuất file Excel'))
    } finally {
      setExporting(false)
    }
  }

  const columns: ColumnsType<GrnResponse> = [
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">#</span>,
      key: 'idx',
      width: 44,
      align: 'center',
      render: (_, __, idx) => (
        <span className="text-xs font-mono text-slate-400">{page * pageSize + idx + 1}</span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Mã phiếu nhập (GRN)</span>,
      width: 155,
      render: (_, r) => (
        <div className="py-0.5">
          <button
            type="button"
            onClick={() => {
              setSelectedGrn(r)
              setIsDetailOpen(true)
            }}
            className="font-mono text-sm font-semibold text-blue-600 hover:text-blue-800 hover:underline cursor-pointer text-left transition-colors"
          >
            {r.grnNumber}
          </button>
          <div className="text-xs text-slate-500 font-mono mt-0.5">PO: {r.poNumber}</div>
        </div>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Nhà cung cấp</span>,
      width: 200,
      render: (_, r) => (
        <span className="font-semibold text-slate-900 text-sm leading-snug line-clamp-1" title={r.supplierName || '---'}>
          {r.supplierName || '---'}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Kho nhận hàng</span>,
      width: 145,
      render: (_, r) => (
        <div className="flex items-center gap-1.5 text-sm text-slate-700 font-medium">
          <ShopOutlined className="text-slate-400 text-sm shrink-0" />
          <span className="truncate" title={r.warehouseName || `Kho #${r.warehouseId}`}>
            {r.warehouseName || `Kho #${r.warehouseId}`}
          </span>
        </div>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Ngày nhập</span>,
      width: 105,
      dataIndex: 'receiptDate',
      render: (val: string) => (
        <span className="text-xs text-slate-700 font-medium">{val ? new Date(val).toLocaleDateString('vi-VN') : '-'}</span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Người nhận</span>,
      width: 115,
      dataIndex: 'receivedBy',
      render: (val: string) => <span className="text-xs text-slate-700">{val || 'Hệ thống'}</span>,
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">SL Đạt</span>,
      width: 95,
      align: 'right',
      render: (_, r) => (
        <span className="text-sm text-slate-900 font-mono font-bold tabular-nums">
          +{formatNumber(r.totalAcceptedQuantity)}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">SL Hỏng</span>,
      width: 90,
      align: 'right',
      render: (_, r) =>
        r.totalRejectedQuantity > 0 ? (
          <span className="text-sm text-rose-700 font-mono font-bold tabular-nums">
            {formatNumber(r.totalRejectedQuantity)}
          </span>
        ) : (
          <span className="text-sm text-slate-400 font-mono">0</span>
        ),
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Trạng thái</span>,
      width: 125,
      align: 'center',
      render: (_, r) => {
        const conf = GRN_STATUS_CONFIG[r.status] || {
          label: r.status,
          color: 'default',
        }
        return (
          <Tag color={conf.color} className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full border m-0">
            {conf.label}
          </Tag>
        )
      },
    },
    {
      title: <span className="whitespace-nowrap font-medium text-slate-700">Thao tác</span>,
      width: 80,
      align: 'center',
      render: (_, r) => (
        <Button
          size="small"
          type="text"
          icon={<EyeOutlined />}
          onClick={() => {
            setSelectedGrn(r)
            setIsDetailOpen(true)
          }}
          className="text-slate-500 hover:text-blue-600 text-xs"
        >
          Xem
        </Button>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      {/* 4 Cards Thống kê Nhập kho */}
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Tổng Phiếu Nhập (GRN)
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatNumber(summary?.totalGrn ?? total)}
              </span>
              <span className="text-xs text-slate-500 font-normal">phiếu</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
              Tổng số đợt nhập kho đã ghi nhận
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Đã hoàn tất
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatNumber(summary?.completedGrn ?? 0)}
              </span>
              <span className="text-xs text-slate-500 font-normal">phiếu</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
              Nhập kho thành công, đã tăng tồn
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Tổng SL đạt chuẩn
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatNumber(summary?.totalAcceptedQuantity ?? 0)}
              </span>
              <span className="text-xs text-slate-500 font-normal">đơn vị</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
              Hàng hóa hợp lệ đưa vào lưu kho
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Tổng SL từ chối / hỏng
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatNumber(summary?.totalRejectedQuantity ?? 0)}
              </span>
              <span className="text-xs text-slate-500 font-normal">đơn vị</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
              Hàng không đạt, trả lại nhà cung cấp
            </div>
          </Card>
        </Col>
      </Row>

      {/* Thanh công cụ tìm kiếm và lọc */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2 flex-1">
          <Search
            placeholder="Tìm theo Mã GRN, Mã PO, NCC..."
            onSearch={(val) => {
              setKeyword(val.trim() || undefined)
              setPage(0)
            }}
            className="w-full sm:w-64"
            allowClear
          />

          <Select
            placeholder="Trạng thái"
            value={selectedStatus}
            onChange={(val) => {
              setSelectedStatus(val)
              setPage(0)
            }}
            allowClear
            className="w-36"
            options={[
              { value: 'COMPLETED', label: 'Đã hoàn tất' },
              { value: 'DRAFT', label: 'Bản nháp' },
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
            className="w-48"
            options={suppliers.map((s) => ({
              value: s.id,
              label: `[${s.code}] ${s.name}`,
            }))}
          />

          <Select
            placeholder="Kho nhận hàng"
            value={selectedWarehouseId}
            onChange={(val) => {
              setSelectedWarehouseId(val)
              setPage(0)
            }}
            allowClear
            showSearch
            filterOption={(input, opt) =>
              String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
            className="w-44"
            options={warehouses.map((w) => ({
              value: w.id,
              label: `[${w.code}] ${w.name}`,
            }))}
          />

          <RangePicker
            format="DD/MM/YYYY"
            placeholder={['Từ ngày nhập', 'Đến ngày nhập']}
            onChange={(dates) => {
              if (dates && dates[0] && dates[1]) {
                setDateRange([dates[0].format('YYYY-MM-DD'), dates[1].format('YYYY-MM-DD')])
              } else {
                setDateRange([undefined, undefined])
              }
              setPage(0)
            }}
            className="w-60"
          />

          {(keyword ||
            selectedStatus ||
            selectedSupplierId !== undefined ||
            selectedWarehouseId !== undefined ||
            dateRange[0]) && (
            <Button
              onClick={() => {
                setKeyword(undefined)
                setSelectedStatus(undefined)
                setSelectedSupplierId(undefined)
                setSelectedWarehouseId(undefined)
                setDateRange([undefined, undefined])
                setPage(0)
              }}
            >
              Xóa bộ lọc
            </Button>
          )}
        </div>

        <Space wrap>
          <Button
            icon={<FileExcelOutlined />}
            onClick={handleExportExcel}
            loading={exporting}
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

          {canCreateGrn && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => onOpenCreateGrn()}
              className="font-medium rounded-md shadow-xs"
            >
              Lập phiếu nhập kho
            </Button>
          )}
        </Space>
      </div>

      {/* Bảng GRN */}
      <Table
        rowKey="id"
        columns={columns}
        dataSource={grnList}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (totalCount) => `Tổng cộng ${totalCount} phiếu nhập kho`,
          onChange: (p, ps) => {
            setPage(p - 1)
            setPageSize(ps)
          },
        }}
      />

      {/* Modal chi tiết GRN */}
      <GoodsReceiptDetailModal
        open={isDetailOpen}
        grn={selectedGrn}
        onClose={() => {
          setIsDetailOpen(false)
          setSelectedGrn(null)
        }}
      />
    </div>
  )
}
