import {
  EyeOutlined,
  FileExcelOutlined,
  PlusOutlined,
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
      title: 'STT',
      width: 50,
      align: 'center',
      render: (_, __, idx) => page * pageSize + idx + 1,
    },
    {
      title: 'Mã Phiếu Nhập',
      width: 170,
      render: (_, r) => (
        <div>
          <a
            className="font-mono font-semibold text-emerald-700 hover:underline"
            onClick={() => {
              setSelectedGrn(r)
              setIsDetailOpen(true)
            }}
          >
            {r.grnNumber}
          </a>
          <div className="text-xs text-slate-500 mt-0.5">PO: {r.poNumber}</div>
        </div>
      ),
    },
    {
      title: 'Nhà cung cấp',
      width: 220,
      render: (_, r) => (
        <span className="font-medium text-slate-800">{r.supplierName || '---'}</span>
      ),
    },
    {
      title: 'Kho nhận',
      width: 160,
      render: (_, r) => (
        <span className="text-slate-700">{r.warehouseName || `Kho #${r.warehouseId}`}</span>
      ),
    },
    {
      title: 'Ngày nhập',
      width: 110,
      dataIndex: 'receiptDate',
    },
    {
      title: 'Người nhận',
      width: 120,
      dataIndex: 'receivedBy',
      render: (val) => val || 'Hệ thống',
    },
    {
      title: 'SL Đạt',
      width: 100,
      align: 'right',
      render: (_, r) => (
        <strong className="text-emerald-700 font-mono">
          {formatNumber(r.totalAcceptedQuantity)}
        </strong>
      ),
    },
    {
      title: 'SL Hỏng',
      width: 90,
      align: 'right',
      render: (_, r) =>
        r.totalRejectedQuantity > 0 ? (
          <span className="text-amber-700 font-semibold font-mono">
            {formatNumber(r.totalRejectedQuantity)}
          </span>
        ) : (
          <span className="text-slate-400">0</span>
        ),
    },
    {
      title: 'Trạng thái',
      width: 120,
      render: (_, r) => {
        const conf = GRN_STATUS_CONFIG[r.status] || {
          label: r.status,
          color: 'default',
        }
        return <Tag color={conf.color}>{conf.label}</Tag>
      },
    },
    {
      title: 'Thao tác',
      width: 90,
      align: 'center',
      fixed: 'right',
      render: (_, r) => (
        <Button
          size="small"
          type="text"
          icon={<EyeOutlined />}
          onClick={() => {
            setSelectedGrn(r)
            setIsDetailOpen(true)
          }}
          className="text-blue-600 hover:bg-blue-50"
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
          <Card size="small" className="border-l-4 border-l-blue-500 shadow-2xs">
            <div className="text-xs text-slate-500">Tổng Phiếu Nhập (GRN)</div>
            <div className="text-xl font-bold text-slate-800 mt-1">
              {formatNumber(summary?.totalGrn ?? total)}
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="border-l-4 border-l-emerald-500 shadow-2xs">
            <div className="text-xs text-slate-500">Đã hoàn tất (COMPLETED)</div>
            <div className="text-xl font-bold text-emerald-600 mt-1">
              {formatNumber(summary?.completedGrn ?? 0)}
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="border-l-4 border-l-teal-500 shadow-2xs">
            <div className="text-xs text-slate-500">Tổng SL thực nhận đạt</div>
            <div className="text-xl font-bold text-teal-700 mt-1">
              {formatNumber(summary?.totalAcceptedQuantity ?? 0)}
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="border-l-4 border-l-amber-500 shadow-2xs">
            <div className="text-xs text-slate-500">Tổng SL từ chối / hỏng</div>
            <div className="text-xl font-bold text-amber-700 mt-1">
              {formatNumber(summary?.totalRejectedQuantity ?? 0)}
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
            className="hover:border-green-600 hover:text-green-600"
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

          {canCreateGrn && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => onOpenCreateGrn()}
              className="bg-emerald-600 hover:bg-emerald-700 font-semibold"
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
        scroll={{ x: 1100 }}
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
