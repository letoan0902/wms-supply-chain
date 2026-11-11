import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  EyeOutlined,
  FileExcelOutlined,
  InboxOutlined,
  PrinterOutlined,
  ReloadOutlined,
  RocketOutlined,
  SearchOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import {
  App,
  Badge,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Progress,
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
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../../services/api'
import { formatNumber } from '../../../utils/format'
import { addNotificationListener } from '../../realtime/realtimeConnection'
import * as warehouseApi from '../../warehouses/warehouseApi'
import type { Warehouse } from '../../warehouses/types'
import * as salesApi from '../salesApi'
import type { SalesOrder } from '../types'
import PrintPickingListModal from './PrintPickingListModal'
import SmartPickingModal from './SmartPickingModal'
import * as pickingApi from '../pickingApi'
import type { PickingItem } from '../pickingTypes'

const { RangePicker } = DatePicker

interface RealtimeOutboundBoardProps {
  onOpenOrderDetail?: (order: SalesOrder) => void
}

export default function RealtimeOutboundBoard({ onOpenOrderDetail }: RealtimeOutboundBoardProps) {
  const { message } = App.useApp()

  const [orders, setOrders] = useState<SalesOrder[]>([])
  const [loading, setLoading] = useState(false)
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  // Bộ lọc
  const [keyword, setKeyword] = useState('')
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | undefined>(undefined)
  const [selectedFilterStatus, setSelectedFilterStatus] = useState<string>('ALL')
  const [dateRange, setDateRange] = useState<[string | undefined, string | undefined]>([
    undefined,
    undefined,
  ])

  // Realtime & Refresh Key
  const [refreshKey, setRefreshKey] = useState(0)
  const reloadData = () => setRefreshKey((k) => k + 1)

  // Modal Lấy hàng & In phiếu
  const [selectedOrderForPicking, setSelectedOrderForPicking] = useState<SalesOrder | null>(null)
  const [isPickingModalOpen, setIsPickingModalOpen] = useState(false)

  const [selectedOrderForPrint, setSelectedOrderForPrint] = useState<SalesOrder | null>(null)
  const [printItems, setPrintItems] = useState<PickingItem[]>([])
  const [isPrintModalOpen, setIsPrintModalOpen] = useState(false)
  const [printLoadingId, setPrintLoadingId] = useState<number | null>(null)

  // 1. Tải danh mục kho cho bộ lọc
  useEffect(() => {
    let ignore = false
    void warehouseApi
      .fetchWarehouses({ active: true })
      .then((res) => {
        if (!ignore) setWarehouses(res || [])
      })
      .catch((err) => {
        console.error('Không thể tải kho cho bảng xuất hàng:', err)
      })
    return () => {
      ignore = true
    }
  }, [])

  // 2. Tải danh sách đơn xuất kho (CONFIRMED và COMPLETED)
  useEffect(() => {
    let ignore = false
    const loadOutboundData = async () => {
      setLoading(true)
      try {
        const [confirmedRes, completedRes] = await Promise.all([
          salesApi.fetchSalesOrders({
            status: 'CONFIRMED',
            warehouseId: selectedWarehouseId,
            keyword: keyword.trim() || undefined,
            fromOrderDate: dateRange[0],
            toOrderDate: dateRange[1],
            size: 100,
          }),
          salesApi.fetchSalesOrders({
            status: 'COMPLETED',
            warehouseId: selectedWarehouseId,
            keyword: keyword.trim() || undefined,
            fromOrderDate: dateRange[0],
            toOrderDate: dateRange[1],
            size: 50,
          }),
        ])

        if (!ignore) {
          const list = [...(confirmedRes.content || []), ...(completedRes.content || [])]
          setOrders(list)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải bảng xuất kho thời gian thực'))
        }
      } finally {
        if (!ignore) setLoading(false)
      }
    }

    void loadOutboundData()
    return () => {
      ignore = true
    }
  }, [selectedWarehouseId, keyword, dateRange, refreshKey, message])

  // 3. Lắng nghe WebSocket sự kiện realtime (ORDER_CREATED, ORDER_DISPATCHED)
  useEffect(() => {
    const unsubscribe = addNotificationListener((msg) => {
      if (
        msg.type === 'ORDER_CREATED' ||
        msg.type === 'ORDER_DISPATCHED'
      ) {
        // Tự động làm mới bảng xuất kho thời gian thực
        reloadData()
      }
    })
    return () => {
      unsubscribe()
    }
  }, [])

  // Lọc dữ liệu theo tab trạng thái
  const filteredOrders = orders.filter((order) => {
    if (selectedFilterStatus === 'ALL') return true
    if (selectedFilterStatus === 'WAITING') return order.status === 'CONFIRMED'
    if (selectedFilterStatus === 'COMPLETED') return order.status === 'COMPLETED'
    return true
  })

  // Thống kê nhanh
  const waitingOrdersCount = orders.filter((o) => o.status === 'CONFIRMED').length
  const completedOrdersCount = orders.filter((o) => o.status === 'COMPLETED').length
  const totalOutboundUnits = orders.reduce(
    (sum, o) => sum + (o.items || []).reduce((s, it) => s + (it.quantity || 0), 0),
    0
  )
  const totalOrdersCount = orders.length
  const completionRate =
    totalOrdersCount > 0 ? Math.round((completedOrdersCount / totalOrdersCount) * 100) : 0

  // Mở modal Lấy hàng thông minh
  const handleOpenPicking = (order: SalesOrder) => {
    setSelectedOrderForPicking(order)
    setIsPickingModalOpen(true)
  }

  // Mở modal In phiếu lấy hàng: lập lộ trình từ tồn thực tế của kho xuất, không dựng vị trí giả
  const handleOpenPrint = async (order: SalesOrder) => {
    if (!order.warehouseId) {
      message.warning(`Đơn ${order.soNumber} chưa gán kho xuất hàng nên chưa in được phiếu lấy hàng`)
      return
    }
    if (printLoadingId !== null) return
    setPrintLoadingId(order.id)
    try {
      const items = await pickingApi.loadPickingRoute(order)
      setSelectedOrderForPrint(order)
      setPrintItems(items)
      setIsPrintModalOpen(true)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tải tồn kho theo lô để lập phiếu lấy hàng'))
    } finally {
      setPrintLoadingId(null)
    }
  }

  // Xuất file Excel báo cáo xuất kho
  const handleExportExcel = () => {
    if (filteredOrders.length === 0) {
      message.warning('Không có dữ liệu xuất kho để xuất Excel!')
      return
    }

    const excelData = filteredOrders.map((o, idx) => ({
      STT: idx + 1,
      'Mã Đơn Bán (SO)': o.soNumber,
      'Khách Hàng': o.customerName || 'N/A',
      'Kho Xuất': o.warehouseName || 'Chưa gán',
      'Ngày Đặt': o.orderDate,
      'Số Lượng Mặt Hàng': o.items?.length || 0,
      'Tổng Số Lượng SP': (o.items || []).reduce((s, it) => s + (it.quantity || 0), 0),
      'Trạng Thái': o.status === 'COMPLETED' ? 'Đã xuất kho' : 'Chờ lấy hàng',
      'Địa Chỉ Giao': o.deliveryAddress || '',
      'Ghi Chú': o.note || '',
    }))

    const ws = XLSX.utils.json_to_sheet(excelData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, 'BangXuatKho')
    XLSX.writeFile(wb, `Bang_Xuat_Kho_Thoi_Gian_Thuc_${dayjs().format('YYYYMMDD_HHmm')}.xlsx`)
    message.success('Đã xuất file Excel bảng xuất kho thành công!')
  }

  const columns: ColumnsType<SalesOrder> = [
    {
      title: 'Mã SO',
      dataIndex: 'soNumber',
      key: 'soNumber',
      width: 140,
      render: (soNumber, r) => (
        <a
          onClick={() => handleOpenPicking(r)}
          className="font-mono font-bold text-blue-600 hover:text-blue-800"
        >
          {soNumber}
        </a>
      ),
    },
    {
      title: 'Khách hàng & Nơi giao',
      key: 'customer',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-slate-800">{r.customerName || 'N/A'}</div>
          {r.deliveryAddress && (
            <div className="text-xs text-slate-500 truncate max-w-xs">
              {r.deliveryAddress}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Kho xuất hàng',
      dataIndex: 'warehouseName',
      key: 'warehouseName',
      width: 140,
      render: (name) => <span className="text-xs font-medium text-slate-700">{name || '-'}</span>,
    },
    {
      title: 'Số mặt hàng / Tổng SP',
      key: 'itemsCount',
      width: 160,
      align: 'center',
      render: (_, r) => {
        const totalQty = (r.items || []).reduce((s, it) => s + (it.quantity || 0), 0)
        return (
          <div>
            <span className="font-semibold text-slate-800">{r.items?.length || 0}</span> mặt hàng
            <div className="text-[11px] text-slate-500 font-bold text-emerald-700">
              = {formatNumber(totalQty)} sản phẩm
            </div>
          </div>
        )
      },
    },
    {
      title: 'Tiến độ lấy hàng',
      key: 'progress',
      width: 170,
      render: (_, r) => {
        const isDone = r.status === 'COMPLETED'
        return (
          <div className="space-y-1">
            <Progress
              percent={isDone ? 100 : 0}
              size="small"
              status={isDone ? 'success' : 'active'}
              strokeColor={isDone ? '#10b981' : '#f97316'}
            />
            <div className="text-[11px] text-slate-500 flex justify-between">
              <span>{isDone ? 'Đã hoàn tất 100%' : 'Chờ lấy vào xe'}</span>
            </div>
          </div>
        )
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      align: 'center',
      render: (status) => {
        if (status === 'COMPLETED') {
          return (
            <Tag color="success" className="m-0 font-medium">
              <CheckCircleOutlined /> Đã xuất kho
            </Tag>
          )
        }
        return (
          <Tag color="warning" className="m-0 font-medium">
            <ClockCircleOutlined /> Chờ lấy hàng
          </Tag>
        )
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, r) => (
        <Space size="small">
          <Tooltip title="Lấy hàng thông minh (Smart Picking Route)">
            <Button
              size="small"
              type="primary"
              icon={<RocketOutlined />}
              onClick={() => handleOpenPicking(r)}
              className={
                r.status === 'COMPLETED'
                  ? 'bg-slate-500'
                  : 'bg-orange-500 hover:bg-orange-600'
              }
            >
              {r.status === 'COMPLETED' ? 'Xem lộ trình' : 'Lấy hàng'}
            </Button>
          </Tooltip>

          <Tooltip title="In Phiếu lấy hàng (Picking List)">
            <Button
              size="small"
              icon={<PrinterOutlined />}
              loading={printLoadingId === r.id}
              onClick={() => void handleOpenPrint(r)}
            />
          </Tooltip>

          {onOpenOrderDetail && (
            <Tooltip title="Xem chi tiết đơn bán hàng">
              <Button
                size="small"
                icon={<EyeOutlined />}
                onClick={() => onOpenOrderDetail(r)}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      {/* 1. Header Bảng xuất kho thời gian thực */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-3.5 rounded-lg border border-slate-200">
        <div className="flex items-center gap-2.5">
          <RocketOutlined className="text-orange-500 text-xl" />
          <div>
            <h3 className="font-bold text-slate-800 text-base m-0 flex items-center gap-2">
              <span>Bảng Xuất Kho Thời Gian Thực & Lấy Hàng (Outbound Board)</span>
              <Badge status="processing" text={<span className="text-xs text-emerald-600 font-normal">Realtime STOMP Active</span>} />
            </h3>
            <span className="text-xs text-slate-500">
              Lộ trình lấy hàng thông minh theo vị trí Zone → Rack → Bin, hỗ trợ nhặt hàng xe đẩy (FR17)
            </span>
          </div>
        </div>

        <Space wrap>
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
            loading={loading}
          >
            Làm mới
          </Button>
        </Space>
      </div>

      {/* 2. Thẻ thống kê thời gian thực */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-orange-500 shadow-2xs">
            <Statistic
              title={<span className="text-xs text-slate-500">Đơn chờ lấy hàng (Confirmed)</span>}
              value={waitingOrdersCount}
              valueStyle={{ color: '#ea580c', fontWeight: 'bold' }}
              prefix={<ClockCircleOutlined />}
              suffix="đơn"
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-emerald-500 shadow-2xs">
            <Statistic
              title={<span className="text-xs text-slate-500">Đã xuất kho hoàn tất</span>}
              value={completedOrdersCount}
              valueStyle={{ color: '#059669', fontWeight: 'bold' }}
              prefix={<CheckCircleOutlined />}
              suffix="đơn"
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-blue-500 shadow-2xs">
            <Statistic
              title={<span className="text-xs text-slate-500">Tổng sản phẩm cần xuất</span>}
              value={totalOutboundUnits}
              valueStyle={{ color: '#2563eb', fontWeight: 'bold' }}
              prefix={<InboxOutlined />}
              formatter={(v) => formatNumber(Number(v))}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card size="small" className="border-l-4 border-l-purple-500 shadow-2xs">
            <Statistic
              title={<span className="text-xs text-slate-500">Tỷ lệ hoàn thành xuất kho</span>}
              value={completionRate}
              valueStyle={{ color: '#7c3aed', fontWeight: 'bold' }}
              prefix={<SyncOutlined />}
              suffix="%"
            />
          </Card>
        </Col>
      </Row>

      {/* 3. Thanh công cụ tìm kiếm và lọc */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-white p-3 rounded-lg border border-slate-200">
        <div className="flex flex-wrap items-center gap-2">
          <Input
            placeholder="Tìm theo Mã SO, Tên khách hàng..."
            prefix={<SearchOutlined className="text-slate-400" />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-60"
            allowClear
          />

          <Select
            placeholder="Kho xuất hàng"
            value={selectedWarehouseId}
            onChange={(val) => setSelectedWarehouseId(val)}
            allowClear
            className="w-52"
            options={warehouses.map((w) => ({
              value: w.id,
              label: `[${w.code}] ${w.name}`,
            }))}
          />

          <Select
            value={selectedFilterStatus}
            onChange={(val) => setSelectedFilterStatus(val)}
            className="w-44"
            options={[
              { value: 'ALL', label: 'Tất cả trạng thái' },
              { value: 'WAITING', label: 'Chờ lấy hàng' },
              { value: 'COMPLETED', label: 'Đã xuất kho' },
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
            }}
            className="w-60"
          />

          {(keyword || selectedWarehouseId || selectedFilterStatus !== 'ALL' || dateRange[0]) && (
            <Button
              onClick={() => {
                setKeyword('')
                setSelectedWarehouseId(undefined)
                setSelectedFilterStatus('ALL')
                setDateRange([undefined, undefined])
              }}
            >
              Xóa lọc
            </Button>
          )}
        </div>
      </div>

      {/* 4. Bảng danh sách đơn hàng xuất kho */}
      <Card variant="borderless" className="shadow-2xs">
        <Table
          rowKey="id"
          columns={columns}
          dataSource={filteredOrders}
          loading={loading}
          scroll={{ x: 1000 }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (total) => `Tổng cộng ${total} đơn xuất kho`,
          }}
        />
      </Card>

      {/* 5. Modal Lộ trình lấy hàng thông minh (Smart Picking View) */}
      <SmartPickingModal
        open={isPickingModalOpen}
        order={selectedOrderForPicking}
        onClose={() => {
          setIsPickingModalOpen(false)
          setSelectedOrderForPicking(null)
        }}
      />

      {/* 6. Modal In phiếu lấy hàng (Picking List) */}
      <PrintPickingListModal
        open={isPrintModalOpen}
        order={selectedOrderForPrint}
        items={printItems}
        onClose={() => {
          setIsPrintModalOpen(false)
          setSelectedOrderForPrint(null)
        }}
      />
    </div>
  )
}
