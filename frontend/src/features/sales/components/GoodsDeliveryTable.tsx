import {
  CheckCircleOutlined,
  ExportOutlined,
  EyeOutlined,
  FileExcelOutlined,
  PlusOutlined,
  PrinterOutlined,
  ReloadOutlined,
  RocketOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { useCan } from '../../auth/useCan'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../../services/api'
import { formatNumber } from '../../../utils/format'
import * as warehouseApi from '../../warehouses/warehouseApi'
import type { Warehouse } from '../../warehouses/types'
import * as gdnApi from '../gdnApi'
import type { GdnResponse, GdnSummaryResponse, GoodsDeliveryStatus } from '../gdnTypes'
import { isAwaitingDispatch, useGdnDispatch } from '../useGdnDispatch'
import { useOutboundLiveRefresh } from '../useOutboundLiveRefresh'
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
  // Lập phiếu xuất kho và xác nhận xuất kho: ADMIN, Quản lý kho, Nhân viên kho
  const can = useCan()
  const canCreateGdn = can('gdn.create')
  const { dispatchingId, dispatchGdn } = useGdnDispatch()

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
  // Phiếu vừa lập đã lưu ở PICKING nhưng chưa trừ tồn (máy chủ trả 503): tô màu dòng để bấm "Xác nhận xuất kho"
  const [pendingGdnNumber, setPendingGdnNumber] = useState<string | null>(null)

  const reloadList = useCallback(() => setRefreshKey((k) => k + 1), [])
  // Tin thời gian thực về đơn bán hoặc tồn kho qua kết nối chung: làm mới bảng (giãn cách, không hỏi định kỳ)
  useOutboundLiveRefresh(reloadList)

  // Xác nhận xuất kho phiếu DRAFT/PICKING; tải lại danh sách sau cả thành công lẫn thất bại
  const handleDispatch = async (gdn: GdnResponse) => {
    const updated = await dispatchGdn(gdn)
    if (updated) {
      if (pendingGdnNumber === updated.gdnNumber && updated.status === 'COMPLETED') setPendingGdnNumber(null)
      setSelectedGdnForDetail((current) => (current?.id === updated.id ? updated : current))
    }
    reloadList()
  }

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
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">#</span>,
      key: 'index',
      width: 44,
      align: 'center',
      render: (_, __, idx) => (
        <span className="text-xs font-mono text-slate-400">{page * pageSize + idx + 1}</span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Mã Phiếu GDN</span>,
      dataIndex: 'gdnNumber',
      key: 'gdnNumber',
      width: 160,
      render: (code, r) => (
        <button
          type="button"
          onClick={() => setSelectedGdnForDetail(r)}
          className="font-mono text-sm font-semibold text-blue-600 hover:text-blue-800 hover:underline cursor-pointer text-left transition-colors"
        >
          {code}
        </button>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Mã Đơn SO</span>,
      dataIndex: 'soNumber',
      key: 'soNumber',
      width: 140,
      render: (soCode) => (
        <span className="font-mono text-sm font-semibold text-slate-800">{soCode}</span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Khách hàng & Địa chỉ</span>,
      key: 'customer',
      width: 220,
      render: (_, r) => (
        <div className="py-0.5">
          <div className="font-semibold text-slate-900 text-sm leading-snug line-clamp-1">{r.customerName || 'N/A'}</div>
          {r.deliveryAddress && (
            <div className="text-xs text-slate-500 truncate max-w-xs mt-0.5">{r.deliveryAddress}</div>
          )}
        </div>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Kho xuất hàng</span>,
      dataIndex: 'warehouseName',
      key: 'warehouseName',
      width: 140,
      render: (name) => <span className="text-sm font-medium text-slate-700">{name || '-'}</span>,
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Mặt hàng & Tổng SL</span>,
      key: 'itemsSummary',
      width: 160,
      align: 'center',
      render: (_, r) => {
        const totalUnits = Number(r.totalQuantity ?? 0)
        return (
          <div className="py-0.5">
            <span className="font-semibold text-slate-900 text-sm">{r.items?.length || 0}</span> mặt hàng
            <div className="text-xs text-slate-600 font-mono font-semibold tabular-nums mt-0.5">
              = {formatNumber(totalUnits)} SP
            </div>
          </div>
        )
      },
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Thời gian xuất</span>,
      key: 'dispatchedAt',
      width: 130,
      render: (_, r) => (
        <span className="text-xs text-slate-600 font-medium">
          {r.dispatchedAt
            ? dayjs(r.dispatchedAt).format('DD/MM/YYYY HH:mm')
            : r.deliveryDate
              ? dayjs(r.deliveryDate).format('DD/MM/YYYY')
              : '-'}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Trạng thái</span>,
      dataIndex: 'status',
      key: 'status',
      width: 130,
      align: 'center',
      render: (status: GoodsDeliveryStatus) => {
        if (status === 'COMPLETED') {
          return (
            <Tag color="success" className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">
              <CheckCircleOutlined /> Đã xuất kho
            </Tag>
          )
        }
        if (status === 'CANCELLED') {
          return <Tag color="error" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Đã hủy</Tag>
        }
        if (status === 'PICKING') {
          return <Tag color="processing" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Đang lấy hàng</Tag>
        }
        return <Tag color="warning" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Nháp</Tag>
      },
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Thao tác</span>,
      key: 'actions',
      width: 120,
      align: 'center',
      render: (_, r) => (
        <Space size={2}>
          {isAwaitingDispatch(r) && can('gdn.create', r.warehouseId) && (
            <Tooltip title="Xác nhận xuất kho (trừ tồn theo phiếu)">
              <Popconfirm
                title="Xác nhận xuất kho?"
                description={`Trừ tồn kho theo phiếu "${r.gdnNumber}" và hoàn tất phiếu.`}
                onConfirm={() => handleDispatch(r)}
                okText="Xác nhận xuất kho"
                cancelText="Hủy"
                okButtonProps={{ type: 'primary' }}
              >
                <Button
                  data-write-action
                  size="small"
                  type="text"
                  icon={<ExportOutlined className="text-blue-600 hover:text-blue-700" />}
                  aria-label="Xác nhận xuất kho"
                  loading={dispatchingId === r.id}
                  disabled={dispatchingId !== null && dispatchingId !== r.id}
                />
              </Popconfirm>
            </Tooltip>
          )}

          <Tooltip title="Xem chi tiết phiếu GDN">
            <Button
              size="small"
              type="text"
              icon={<EyeOutlined className="text-slate-500 hover:text-blue-600" />}
              onClick={() => setSelectedGdnForDetail(r)}
            />
          </Tooltip>

          <Tooltip title="In Phiếu xuất kho kiêm giao hàng">
            <Button
              size="small"
              type="text"
              icon={<PrinterOutlined className="text-slate-500 hover:text-slate-800" />}
              onClick={() => setSelectedGdnForPrint(r)}
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
          <RocketOutlined className="text-slate-600 text-xl" />
          <div>
            <h3 className="font-bold text-slate-900 text-base m-0">
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
              className="font-semibold shadow-xs"
            >
              Lập Phiếu Xuất GDN
            </Button>
          )}

          <Button
            icon={<FileExcelOutlined />}
            onClick={handleExportExcel}
            className="font-medium"
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

      {/* 2. Thẻ thống kê KPI chuẩn Enterprise */}
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Tổng số phiếu xuất kho
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {summary?.totalGdns ?? total}
              </span>
              <span className="text-sm text-slate-500 font-normal">phiếu</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Toàn bộ phiếu GDN trong kỳ
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Đã xuất kho thành công
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {summary?.completedGdns ?? 0}
              </span>
              <span className="text-sm text-slate-500 font-normal">phiếu</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Đã trừ tồn kho hoàn tất
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Sản phẩm đã xuất
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatNumber(summary?.totalQuantityDispatched ?? 0)}
              </span>
              <span className="text-sm text-slate-500 font-normal">sản phẩm</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Tổng lượng hàng thực tế xuất xưởng
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Phiếu đang xử lý
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {(summary?.draftGdns ?? 0) + (summary?.pickingGdns ?? 0)}
              </span>
              <span className="text-sm text-slate-500 font-normal">phiếu</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Phiếu nháp hoặc đang lấy hàng
            </div>
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

      {pendingGdnNumber && (
        <Alert
          type="warning"
          showIcon
          closable
          onClose={() => setPendingGdnNumber(null)}
          message={`Phiếu ${pendingGdnNumber} đã được lưu ở trạng thái Đang lấy hàng nhưng chưa trừ tồn kho`}
          description='Bấm "Xác nhận xuất kho" trên dòng được tô màu (hoặc trong chi tiết phiếu) để trừ tồn và hoàn tất phiếu. Không lập phiếu mới cho cùng số lượng.'
        />
      )}

      {/* 4. Table */}
      <Card variant="borderless" className="shadow-2xs">
        <Table
          rowKey="id"
          columns={columns}
          dataSource={gdns}
          loading={loading}
          rowClassName={(r) => (pendingGdnNumber && r.gdnNumber === pendingGdnNumber ? '[&>td]:!bg-amber-100' : '')}
          scroll={{ x: 1044 }}
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
        onSavedPendingDispatch={(gdnNumber) => {
          setIsCreateModalOpen(false)
          setPendingGdnNumber(gdnNumber)
          setPage(0)
          reloadList()
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
        canDispatch={Boolean(selectedGdnForDetail && can('gdn.create', selectedGdnForDetail.warehouseId))}
        dispatching={selectedGdnForDetail != null && dispatchingId === selectedGdnForDetail.id}
        onDispatch={handleDispatch}
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
