import {
  CheckCircleOutlined,
  EyeOutlined,
  FileExcelOutlined,
  InboxOutlined,
  PlusOutlined,
  PrinterOutlined,
  ReloadOutlined,
  RocketOutlined,
  SearchOutlined,
  SyncOutlined,
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
  Statistic,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { useCan } from '../../auth/useCan'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../../services/api'
import { formatNumber } from '../../../utils/format'
import * as warehouseApi from '../../warehouses/warehouseApi'
import type { Warehouse } from '../../warehouses/types'
import * as gdnApi from '../gdnApi'
import type { GdnResponse, GdnSummaryResponse, GoodsDeliveryStatus } from '../gdnTypes'
import GoodsDeliveryDetailModal from './GoodsDeliveryDetailModal'
import PrintDeliveryNoteModal from './PrintDeliveryNoteModal'
import SmartGoodsDeliveryForm from './SmartGoodsDeliveryForm'

const { RangePicker } = DatePicker

const GDN_STATUS_LABEL: Record<GoodsDeliveryStatus, string> = {
  DRAFT: 'Bản nháp',
  PICKING: 'Đang lấy hàng',
  COMPLETED: 'Đã xuất kho',
  CANCELLED: 'Đã hủy',
}

export default function GoodsDeliveryTable() {
  const { message } = App.useApp()
  // Lập phiếu xuất kho: ADMIN, Quản lý kho, Nhân viên kho
  const canCreateGdn = useCan()('gdn.create')

  const [gdns, setGdns] = useState<GdnResponse[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [summary, setSummary] = useState<GdnSummaryResponse | null>(null)
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  // Bộ lọc
  const [keyword, setKeyword] = useState('')
  const [debouncedKeyword, setDebouncedKeyword] = useState('')
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | undefined>(undefined)
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL')
  const [dateRange, setDateRange] = useState<[string | undefined, string | undefined]>([
    undefined,
    undefined,
  ])
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [refreshKey, setRefreshKey] = useState(0)

  // Modals
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [selectedGdnForDetail, setSelectedGdnForDetail] = useState<GdnResponse | null>(null)
  const [selectedGdnForPrint, setSelectedGdnForPrint] = useState<GdnResponse | null>(null)

  // Debounce tìm kiếm
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedKeyword(keyword)
    }, 350)
    return () => clearTimeout(handler)
  }, [keyword])

  // Tải danh mục kho
  useEffect(() => {
    let ignore = false
    void warehouseApi
      .fetchWarehouses({ active: true })
      .then((res) => {
        if (!ignore) setWarehouses(res || [])
      })
      .catch((err) => {
        console.error('Không thể tải kho cho bảng GDN:', err)
      })
    return () => {
      ignore = true
    }
  }, [])

  // Tải dữ liệu phiếu xuất kho & báo cáo summary
  useEffect(() => {
    let ignore = false
    const loadGdnData = async () => {
      setLoading(true)
      try {
        const [pageRes, summaryRes] = await Promise.all([
          gdnApi.fetchGoodsDeliveries({
            keyword: debouncedKeyword.trim() || undefined,
            warehouseId: selectedWarehouseId,
            status: selectedStatus === 'ALL' ? undefined : (selectedStatus as GoodsDeliveryStatus),
            fromDeliveryDate: dateRange[0],
            toDeliveryDate: dateRange[1],
            page,
            size: pageSize,
          }),
          gdnApi.fetchGoodsDeliverySummary().catch(() => null),
        ])

        if (!ignore) {
          setGdns(pageRes.content || [])
          setTotal(pageRes.totalElements || 0)
          if (summaryRes) setSummary(summaryRes)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách phiếu xuất kho'))
        }
      } finally {
        if (!ignore) setLoading(false)
      }
    }

    void loadGdnData()
    return () => {
      ignore = true
    }
  }, [debouncedKeyword, selectedWarehouseId, selectedStatus, dateRange, page, pageSize, refreshKey, message])

  // Xuất file Excel
  const handleExportExcel = () => {
    if (gdns.length === 0) {
      message.warning('Không có dữ liệu phiếu xuất kho để xuất Excel!')
      return
    }

    const excelData = gdns.map((g, idx) => ({
      STT: idx + 1,
      'Số Phiếu GDN': g.gdnNumber,
      'Mã Đơn SO': g.soNumber || '',
      'Khách Hàng': g.customerName || 'N/A',
      'Kho Xuất': g.warehouseName || 'Chưa gán',
      'Ngày Xuất': g.deliveryDate ? dayjs(g.deliveryDate).format('DD/MM/YYYY') : '',
      'Số Mặt Hàng': g.items?.length || 0,
      'Tổng SL Xuất': Number(g.totalQuantity ?? 0),
      'Trạng Thái': GDN_STATUS_LABEL[g.status] || g.status,
      'Địa Chỉ Giao': g.deliveryAddress || '',
      'Ghi Chú': g.note || '',
    }))

    const ws = XLSX.utils.json_to_sheet(excelData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, 'SoPhieuXuatKho')
    XLSX.writeFile(wb, `So_Phieu_Xuat_Kho_GDN_${dayjs().format('YYYYMMDD_HHmm')}.xlsx`)
    message.success('Đã xuất file Excel sổ phiếu xuất kho thành công!')
  }

  const columns: ColumnsType<GdnResponse> = [
    {
      title: 'Mã Phiếu GDN',
      dataIndex: 'gdnNumber',
      key: 'gdnNumber',
      width: 170,
      render: (code, r) => (
        <a
          onClick={() => setSelectedGdnForDetail(r)}
          className="font-mono font-bold text-emerald-700 hover:text-emerald-900"
        >
          {code}
        </a>
      ),
    },
    {
      title: 'Mã Đơn SO',
      dataIndex: 'soNumber',
      key: 'soNumber',
      width: 160,
      render: (soCode) => (
        <span className="font-mono font-bold text-blue-700">{soCode}</span>
      ),
    },
    {
      title: 'Khách hàng & Địa chỉ',
      key: 'customer',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-slate-800">{r.customerName || 'N/A'}</div>
          {r.deliveryAddress && (
            <div className="text-xs text-slate-500 truncate max-w-xs">{r.deliveryAddress}</div>
          )}
        </div>
      ),
    },
    {
      title: 'Kho xuất hàng',
      dataIndex: 'warehouseName',
      key: 'warehouseName',
      width: 150,
      render: (name) => <span className="text-xs font-medium text-slate-700">{name || '-'}</span>,
    },
    {
      title: 'Số mặt hàng / Tổng SL',
      key: 'itemsSummary',
      width: 170,
      align: 'center',
      render: (_, r) => {
        const totalUnits = Number(r.totalQuantity ?? 0)
        return (
          <div>
            <span className="font-semibold text-slate-800">{r.items?.length || 0}</span> mặt hàng
            <div className="text-[11px] text-emerald-700 font-bold">
              = {formatNumber(totalUnits)} sản phẩm
            </div>
          </div>
        )
      },
    },
    {
      title: 'Thời gian xuất',
      key: 'dispatchedAt',
      width: 150,
      render: (_, r) => (
        <span className="text-xs text-slate-600">
          {r.dispatchedAt
            ? dayjs(r.dispatchedAt).format('DD/MM/YYYY HH:mm')
            : r.deliveryDate
              ? dayjs(r.deliveryDate).format('DD/MM/YYYY')
              : '-'}
        </span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      align: 'center',
      render: (status: GoodsDeliveryStatus) => {
        if (status === 'COMPLETED') {
          return (
            <Tag color="success" className="m-0 font-medium">
              <CheckCircleOutlined /> Đã xuất kho
            </Tag>
          )
        }
        if (status === 'CANCELLED') {
          return <Tag color="error">Đã hủy</Tag>
        }
        if (status === 'PICKING') {
          return <Tag color="processing">Đang lấy hàng</Tag>
        }
        return <Tag color="warning">Nháp</Tag>
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, r) => (
        <Space size="small">
          <Tooltip title="Xem chi tiết phiếu GDN">
            <Button
              size="small"
              icon={<EyeOutlined />}
              onClick={() => setSelectedGdnForDetail(r)}
            />
          </Tooltip>

          <Tooltip title="In Phiếu xuất kho kiêm giao hàng">
            <Button
              size="small"
              type="primary"
              icon={<PrinterOutlined />}
              onClick={() => setSelectedGdnForPrint(r)}
              className="bg-emerald-600 hover:bg-emerald-700"
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      {/* 1. Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-3.5 rounded-lg border border-slate-200">
        <div className="flex items-center gap-2.5">
          <RocketOutlined className="text-emerald-600 text-xl" />
          <div>
            <h3 className="font-bold text-slate-800 text-base m-0">
              Sổ Theo Dõi Phiếu Xuất Kho (Goods Delivery Notes - GDN)
            </h3>
            <span className="text-xs text-slate-500">
              Quản lý các đợt xuất kho, in phiếu giao hàng và theo dõi trừ tồn kho tự động
            </span>
          </div>
        </div>

        <Space wrap>
          {canCreateGdn && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setIsCreateModalOpen(true)}
              className="bg-emerald-600 hover:bg-emerald-700 font-semibold"
            >
              Lập Phiếu Xuất GDN
            </Button>
          )}

          <Button
            icon={<FileExcelOutlined />}
            onClick={handleExportExcel}
            className="text-emerald-700 hover:text-emerald-800"
          >
            Xuất Excel
          </Button>

          <Button
            icon={<ReloadOutlined />}
            onClick={() => setRefreshKey((k) => k + 1)}
            loading={loading}
          >
            Làm mới
          </Button>
        </Space>
      </div>

      {/* 2. Thẻ thống kê */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-emerald-600 shadow-2xs">
            <Statistic
              title={<span className="text-xs text-slate-500">Tổng số phiếu xuất kho</span>}
              value={summary?.totalGdns ?? total}
              valueStyle={{ color: '#059669', fontWeight: 'bold' }}
              prefix={<RocketOutlined />}
              suffix="phiếu"
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-blue-600 shadow-2xs">
            <Statistic
              title={<span className="text-xs text-slate-500">Đã xuất kho thành công</span>}
              value={summary?.completedGdns ?? 0}
              valueStyle={{ color: '#2563eb', fontWeight: 'bold' }}
              prefix={<CheckCircleOutlined />}
              suffix="phiếu"
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-orange-500 shadow-2xs">
            <Statistic
              title={<span className="text-xs text-slate-500">Tổng sản phẩm đã xuất xưởng</span>}
              value={summary?.totalQuantityDispatched ?? 0}
              valueStyle={{ color: '#ea580c', fontWeight: 'bold' }}
              prefix={<InboxOutlined />}
              formatter={(v) => formatNumber(Number(v))}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-purple-600 shadow-2xs">
            <Statistic
              title={<span className="text-xs text-slate-500">Phiếu nháp / đang lấy hàng</span>}
              value={(summary?.draftGdns ?? 0) + (summary?.pickingGdns ?? 0)}
              valueStyle={{ color: '#7c3aed', fontWeight: 'bold' }}
              prefix={<SyncOutlined />}
              suffix="phiếu"
            />
          </Card>
        </Col>
      </Row>

      {/* 3. Bộ lọc */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-white p-3 rounded-lg border border-slate-200">
        <div className="flex flex-wrap items-center gap-2">
          <Input
            placeholder="Tìm theo Mã GDN, Mã SO, Khách hàng..."
            prefix={<SearchOutlined className="text-slate-400" />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-64"
            allowClear
          />

          <Select
            placeholder="Kho xuất hàng"
            value={selectedWarehouseId}
            onChange={(val) => {
              setSelectedWarehouseId(val)
              setPage(0)
            }}
            allowClear
            className="w-52"
            options={warehouses.map((w) => ({
              value: w.id,
              label: `[${w.code}] ${w.name}`,
            }))}
          />

          <Select
            value={selectedStatus}
            onChange={(val) => {
              setSelectedStatus(val)
              setPage(0)
            }}
            className="w-44"
            options={[
              { value: 'ALL', label: 'Tất cả trạng thái' },
              { value: 'COMPLETED', label: GDN_STATUS_LABEL.COMPLETED },
              { value: 'PICKING', label: GDN_STATUS_LABEL.PICKING },
              { value: 'DRAFT', label: GDN_STATUS_LABEL.DRAFT },
              { value: 'CANCELLED', label: GDN_STATUS_LABEL.CANCELLED },
            ]}
          />

          <RangePicker
            format="DD/MM/YYYY"
            placeholder={['Từ ngày', 'Đến ngày']}
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

          {(keyword || selectedWarehouseId || selectedStatus !== 'ALL' || dateRange[0]) && (
            <Button
              onClick={() => {
                setKeyword('')
                setSelectedWarehouseId(undefined)
                setSelectedStatus('ALL')
                setDateRange([undefined, undefined])
                setPage(0)
              }}
            >
              Xóa lọc
            </Button>
          )}
        </div>
      </div>

      {/* 4. Table */}
      <Card variant="borderless" className="shadow-2xs">
        <Table
          rowKey="id"
          columns={columns}
          dataSource={gdns}
          loading={loading}
          scroll={{ x: 1050 }}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `Tổng cộng ${t} phiếu xuất kho`,
            onChange: (p, ps) => {
              setPage(p - 1)
              setPageSize(ps)
            },
          }}
        />
      </Card>

      {/* Modal Lập Phiếu Xuất GDN Thông Minh */}
      <SmartGoodsDeliveryForm
        open={isCreateModalOpen}
        order={null}
        onClose={() => setIsCreateModalOpen(false)}
        onSuccess={() => {
          setIsCreateModalOpen(false)
          setRefreshKey((k) => k + 1)
        }}
      />

      {/* Modal Chi tiết GDN */}
      <GoodsDeliveryDetailModal
        open={Boolean(selectedGdnForDetail)}
        gdn={selectedGdnForDetail}
        onClose={() => setSelectedGdnForDetail(null)}
        onPrint={(g) => {
          setSelectedGdnForPrint(g)
        }}
      />

      {/* Modal In Phiếu Giao Hàng */}
      <PrintDeliveryNoteModal
        open={Boolean(selectedGdnForPrint)}
        gdn={selectedGdnForPrint}
        onClose={() => setSelectedGdnForPrint(null)}
      />
    </div>
  )
}
