import {
  EyeOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  ShoppingCartOutlined,
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
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../services/api'
import { formatCurrency, formatNumber } from '../../utils/format'
import PurchaseOrderDetailModal from './components/PurchaseOrderDetailModal'
import PurchaseOrderForm from './components/PurchaseOrderForm'
import * as purchasesApi from './purchasesApi'
import { STATUS_CONFIG } from './statusConfig'
import type {
  OrderStatus,
  PurchaseOrder,
  PurchaseOrderSummary,
} from './types'

const { Title, Text } = Typography
const { RangePicker } = DatePicker
const { Search } = Input

export default function PurchasesPage() {
  const { message } = App.useApp()

  // Tab active: 'list' | 'create'
  const [activeTab, setActiveTab] = useState<'list' | 'create'>('list')
  const [formKey, setFormKey] = useState(0)

  // State danh sách PO
  const [orders, setOrders] = useState<PurchaseOrder[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  // Filters
  const [keyword, setKeyword] = useState<string | undefined>(undefined)
  const [statusFilter, setStatusFilter] = useState<OrderStatus | undefined>(undefined)
  const [dateRange, setDateRange] = useState<[string | undefined, string | undefined]>([
    undefined,
    undefined,
  ])

  // Summary Metrics
  const [summary, setSummary] = useState<PurchaseOrderSummary | null>(null)
  const [loadingSummary, setLoadingSummary] = useState(false)

  // Modal Chi tiết PO
  const [selectedOrder, setSelectedOrder] = useState<PurchaseOrder | null>(null)
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false)

  // Key làm mới danh sách
  const [refreshKey, setRefreshKey] = useState(0)
  const reloadData = () => setRefreshKey((k) => k + 1)

  // Tải danh sách đơn PO
  useEffect(() => {
    let ignore = false
    const fetchOrders = async () => {
      setLoading(true)
      try {
        const data = await purchasesApi.fetchPurchaseOrders({
          keyword,
          status: statusFilter,
          fromOrderDate: dateRange[0],
          toOrderDate: dateRange[1],
          page,
          size: pageSize,
        })
        if (!ignore) {
          setOrders(data.content || [])
          setTotal(data.totalElements || 0)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách đơn mua hàng'))
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }

    void fetchOrders()
    return () => {
      ignore = true
    }
  }, [keyword, statusFilter, dateRange, page, pageSize, refreshKey, message])

  // Tải tổng quan thống kê
  useEffect(() => {
    let ignore = false
    const fetchSummary = async () => {
      setLoadingSummary(true)
      try {
        const data = await purchasesApi.fetchPurchaseOrderSummary()
        if (!ignore) {
          setSummary(data)
        }
      } catch (err) {
        console.error('Lỗi khi tải tổng quan đơn mua hàng', err)
      } finally {
        if (!ignore) {
          setLoadingSummary(false)
        }
      }
    }

    void fetchSummary()
    return () => {
      ignore = true
    }
  }, [refreshKey])

  // Khi lập đơn mua hàng thành công từ Tab tạo mới
  const handleCreateSuccess = (created: PurchaseOrder) => {
    reloadData()
    setFormKey((k) => k + 1)
    setActiveTab('list')
    setSelectedOrder(created)
    setIsDetailModalOpen(true)
  }

  const handleOpenCreateTab = () => {
    setFormKey((k) => k + 1)
    setActiveTab('create')
  }

  const columns: ColumnsType<PurchaseOrder> = [
    {
      title: 'Mã PO',
      dataIndex: 'poNumber',
      key: 'poNumber',
      width: 160,
      render: (val: string) => (
        <span className="font-mono font-bold text-blue-700 bg-blue-50 px-2 py-0.5 rounded text-xs">
          {val}
        </span>
      ),
    },
    {
      title: 'Nhà cung cấp',
      key: 'supplier',
      width: 240,
      render: (_, r) => (
        <div>
          <div className="font-semibold text-slate-800 text-xs sm:text-sm">{r.supplierName || 'NCC'}</div>
          <div className="text-[11px] text-slate-400">ID: {r.supplierId}</div>
        </div>
      ),
    },
    {
      title: 'Kho nhận',
      dataIndex: 'warehouseName',
      key: 'warehouseName',
      width: 170,
      render: (wh: string | null, r) => (
        <span className="text-xs text-slate-600">
          {wh || (r.warehouseId ? `Kho #${r.warehouseId}` : 'Chưa chỉ định')}
        </span>
      ),
    },
    {
      title: 'Ngày đặt / Nhận dự kiến',
      key: 'dates',
      width: 180,
      render: (_, r) => (
        <div className="text-xs space-y-0.5">
          <div className="text-slate-700">
            Đặt: {r.orderDate ? new Date(r.orderDate).toLocaleDateString('vi-VN') : '-'}
          </div>
          <div className="text-slate-400 text-[11px]">
            Dự kiến: {r.expectedDate ? new Date(r.expectedDate).toLocaleDateString('vi-VN') : 'Chưa có'}
          </div>
        </div>
      ),
    },
    {
      title: 'Mặt hàng',
      key: 'itemsCount',
      width: 100,
      align: 'center',
      render: (_, r) => (
        <span className="text-xs font-semibold text-slate-700 bg-slate-100 px-2 py-0.5 rounded-full">
          {r.items?.length || 0} dòng
        </span>
      ),
    },
    {
      title: 'Tổng tiền (VNĐ)',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 160,
      align: 'right',
      render: (val: number) => (
        <span className="font-bold text-slate-800 text-sm">{formatCurrency(val || 0)}</span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (st: OrderStatus) => {
        const conf = STATUS_CONFIG[st] ?? { label: st, color: 'default', icon: null }
        return (
          <Tag color={conf.color} className="flex items-center gap-1 w-fit text-xs px-2 py-0.5">
            {conf.icon}
            <span>{conf.label}</span>
          </Tag>
        )
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 110,
      align: 'center',
      render: (_, r) => (
        <Button
          size="small"
          icon={<EyeOutlined />}
          onClick={() => {
            setSelectedOrder(r)
            setIsDetailModalOpen(true)
          }}
        >
          Chi tiết
        </Button>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      {/* Header trang */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <Title level={3} className="!mb-1 flex items-center gap-2 text-slate-800">
            <ShoppingCartOutlined className="text-blue-600" />
            <span>Quản lý Đơn Mua Hàng (Purchase Orders - PO)</span>
          </Title>
          <Text type="secondary" className="text-sm">
            Quy trình lập PO, chọn NCC, tính chiết khấu & VAT, gửi phê duyệt đơn mua (FR10 - WMS-09)
          </Text>
        </div>

        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={reloadData}
            loading={loading || loadingSummary}
          >
            Làm mới
          </Button>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleOpenCreateTab}
            className="bg-blue-600 font-semibold"
          >
            Lập đơn mua hàng mới
          </Button>
        </Space>
      </div>

      {/* 4 Cards Thống kê Tình hình đơn mua */}
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={6}>
          <Card size="small" className="border-l-4 border-l-blue-500 shadow-2xs">
            <div className="text-xs text-slate-500">Tổng số đơn PO</div>
            <div className="text-xl font-bold text-slate-800 mt-1">
              {formatNumber(summary?.totalOrders ?? total)}
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="border-l-4 border-l-amber-500 shadow-2xs">
            <div className="text-xs text-slate-500">Đơn chờ duyệt (PENDING)</div>
            <div className="text-xl font-bold text-amber-600 mt-1">
              {formatNumber(summary?.pendingOrders ?? 0)}
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="border-l-4 border-l-green-500 shadow-2xs">
            <div className="text-xs text-slate-500">Đã phê duyệt (APPROVED)</div>
            <div className="text-xl font-bold text-green-600 mt-1">
              {formatNumber(summary?.approvedOrders ?? 0)}
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="border-l-4 border-l-purple-500 shadow-2xs">
            <div className="text-xs text-slate-500">Tổng ngân sách mua hàng</div>
            <div className="text-xl font-bold text-purple-700 mt-1">
              {formatCurrency(summary?.totalApprovedAmount ?? 0)}
            </div>
          </Card>
        </Col>
      </Row>

      {/* Tabs điều hướng: Lập đơn mới / Danh sách PO */}
      <Tabs
        activeKey={activeTab}
        destroyInactiveTabPane
        onChange={(k) => setActiveTab(k as 'list' | 'create')}
        items={[
          {
            key: 'list',
            label: (
              <span className="flex items-center gap-1.5 px-1">
                <FileTextOutlined />
                <span>Danh sách đơn mua hàng</span>
              </span>
            ),
            children: (
              <Card variant="borderless" className="shadow-2xs space-y-4">
                {/* Bộ lọc & Tìm kiếm (dùng Search để tránh gọi API mỗi lần gõ phím) */}
                <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                  <div className="flex flex-wrap items-center gap-2 flex-1">
                    <Search
                      placeholder="Tìm theo Mã PO, Tên NCC..."
                      onSearch={(val) => {
                        setKeyword(val.trim() || undefined)
                        setPage(0)
                      }}
                      className="w-full sm:w-72"
                      allowClear
                    />

                    <Select
                      placeholder="Trạng thái đơn"
                      value={statusFilter}
                      onChange={(val) => {
                        setStatusFilter(val)
                        setPage(0)
                      }}
                      allowClear
                      className="w-40"
                      options={[
                        { value: 'PENDING', label: 'Chờ duyệt' },
                        { value: 'APPROVED', label: 'Đã phê duyệt' },
                        { value: 'DRAFT', label: 'Bản nháp' },
                        { value: 'CONFIRMED', label: 'Đã xác nhận' },
                        { value: 'REJECTED', label: 'Đã từ chối' },
                        { value: 'CANCELLED', label: 'Đã hủy' },
                      ]}
                    />

                    <RangePicker
                      format="DD/MM/YYYY"
                      placeholder={['Từ ngày đặt', 'Đến ngày đặt']}
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

                    {(keyword || statusFilter || dateRange[0]) && (
                      <Button
                        onClick={() => {
                          setKeyword(undefined)
                          setStatusFilter(undefined)
                          setDateRange([undefined, undefined])
                          setPage(0)
                        }}
                      >
                        Xóa lọc
                      </Button>
                    )}
                  </div>
                </div>

                {/* Bảng danh sách đơn PO */}
                <Table
                  rowKey="id"
                  columns={columns}
                  dataSource={orders}
                  loading={loading}
                  scroll={{ x: 1100 }}
                  pagination={{
                    current: page + 1,
                    pageSize,
                    total,
                    showSizeChanger: true,
                    pageSizeOptions: ['10', '20', '50'],
                    showTotal: (totalCount) => `Tổng cộng ${totalCount} đơn mua hàng`,
                    onChange: (p, ps) => {
                      setPage(p - 1)
                      setPageSize(ps)
                    },
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'create',
            label: (
              <span className="flex items-center gap-1.5 px-1 text-blue-600 font-semibold">
                <PlusOutlined />
                <span>Lập đơn mua hàng (PO Form - WMS-09.3)</span>
              </span>
            ),
            children: (
              <Card variant="borderless" className="shadow-2xs">
                <PurchaseOrderForm
                  key={formKey}
                  onSuccess={handleCreateSuccess}
                  onCancel={() => setActiveTab('list')}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* Modal Xem chi tiết Đơn PO */}
      <PurchaseOrderDetailModal
        open={isDetailModalOpen}
        order={selectedOrder}
        onClose={() => {
          setIsDetailModalOpen(false)
          setSelectedOrder(null)
        }}
      />
    </div>
  )
}
