import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  ShoppingCartOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
  App,
  Badge,
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
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../services/api'
import { formatCurrency, formatNumber } from '../../utils/format'
import { getStoredUser } from '../../utils/storage'
import * as supplierApi from '../suppliers/supplierApi'
import type { Supplier } from '../suppliers/types'
import * as warehouseApi from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import PurchaseOrderDetailModal from './components/PurchaseOrderDetailModal'
import PurchaseOrderForm from './components/PurchaseOrderForm'
import RejectPurchaseOrderModal from './components/RejectPurchaseOrderModal'
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
  const currentUser = getStoredUser()
  const canManage =
    currentUser?.roles?.some((r) =>
      ['ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING'].includes(r)
    ) ?? true

  // Tab active: 'list' | 'create'
  const [activeTab, setActiveTab] = useState<'list' | 'create'>('list')
  const [formKey, setFormKey] = useState(0)

  // Sub-filter tab theo trạng thái đơn hàng: 'ALL' | OrderStatus
  const [selectedStatusTab, setSelectedStatusTab] = useState<string>('ALL')

  // State danh sách PO
  const [orders, setOrders] = useState<PurchaseOrder[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  // Filters
  const [keyword, setKeyword] = useState<string | undefined>(undefined)
  const [selectedSupplierId, setSelectedSupplierId] = useState<number | undefined>(undefined)
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | undefined>(undefined)
  const [dateRange, setDateRange] = useState<[string | undefined, string | undefined]>([
    undefined,
    undefined,
  ])

  // Danh mục NCC & Kho phục vụ bộ lọc
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  // Summary Metrics
  const [summary, setSummary] = useState<PurchaseOrderSummary | null>(null)
  const [loadingSummary, setLoadingSummary] = useState(false)
  const [exporting, setExporting] = useState(false)

  // Modals state
  const [selectedOrder, setSelectedOrder] = useState<PurchaseOrder | null>(null)
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false)

  const [orderToReject, setOrderToReject] = useState<PurchaseOrder | null>(null)
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false)

  // Key làm mới danh sách
  const [refreshKey, setRefreshKey] = useState(0)
  const reloadData = () => setRefreshKey((k) => k + 1)

  // Tải danh mục NCC và Kho cho bộ lọc
  useEffect(() => {
    let ignore = false
    const loadFilterData = async () => {
      try {
        const [supRes, whRes] = await Promise.all([
          supplierApi.fetchSuppliers({ active: true, size: 100 }),
          warehouseApi.fetchWarehouses({ active: true }),
        ])
        if (!ignore) {
          setSuppliers(supRes.content || [])
          setWarehouses(whRes || [])
        }
      } catch (err) {
        console.error('Không thể tải danh mục bộ lọc', err)
      }
    }
    void loadFilterData()
    return () => {
      ignore = true
    }
  }, [])

  // Tải danh sách đơn PO
  useEffect(() => {
    let ignore = false
    const fetchOrders = async () => {
      setLoading(true)
      try {
        const currentStatus =
          selectedStatusTab !== 'ALL' ? (selectedStatusTab as OrderStatus) : undefined

        const data = await purchasesApi.fetchPurchaseOrders({
          keyword,
          supplierId: selectedSupplierId,
          warehouseId: selectedWarehouseId,
          status: currentStatus,
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
  }, [
    keyword,
    selectedSupplierId,
    selectedWarehouseId,
    selectedStatusTab,
    dateRange,
    page,
    pageSize,
    refreshKey,
    message,
  ])

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

  // Phê duyệt nhanh đơn mua từ bảng
  const handleQuickApprove = async (order: PurchaseOrder) => {
    try {
      await purchasesApi.approvePurchaseOrder(order.id)
      message.success(`Đã phê duyệt đơn mua "${order.poNumber}"! Hàng đang về đã được cập nhật.`)
      reloadData()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi phê duyệt đơn mua hàng'))
    }
  }

  // Hủy nhanh đơn mua từ bảng
  const handleQuickCancel = async (order: PurchaseOrder) => {
    try {
      await purchasesApi.cancelPurchaseOrder(order.id)
      message.success(`Đã hủy đơn mua hàng "${order.poNumber}"!`)
      reloadData()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi hủy đơn mua hàng'))
    }
  }

  // Xuất file Excel toàn bộ danh sách đơn mua hàng khớp bộ lọc
  const handleExportExcel = async () => {
    try {
      setExporting(true)
      const currentStatus =
        selectedStatusTab !== 'ALL' ? (selectedStatusTab as OrderStatus) : undefined

      const data = await purchasesApi.fetchPurchaseOrders({
        keyword,
        supplierId: selectedSupplierId,
        warehouseId: selectedWarehouseId,
        status: currentStatus,
        fromOrderDate: dateRange[0],
        toOrderDate: dateRange[1],
        page: 0,
        size: 1000,
      })
      const exportList = data.content || []
      if (exportList.length === 0) {
        message.warning('Không có dữ liệu đơn mua hàng để xuất Excel')
        return
      }

      const exportData = exportList.map((po, index) => {
        const conf = STATUS_CONFIG[po.status]
        return {
          STT: index + 1,
          'Mã PO': po.poNumber,
          'Nhà cung cấp': po.supplierName || `ID: ${po.supplierId}`,
          'Kho nhận hàng': po.warehouseName || (po.warehouseId ? `Kho #${po.warehouseId}` : ''),
          'Ngày lập đơn': po.orderDate || '',
          'Ngày nhận dự kiến': po.expectedDate || '',
          'Số dòng SKU': po.items?.length || 0,
          'Tiền hàng (VNĐ)': po.subtotalAmount || 0,
          'Chiết khấu (VNĐ)': po.discountAmount || 0,
          'Thuế VAT (VNĐ)': po.taxAmount || 0,
          'Tổng thanh toán (VNĐ)': po.totalAmount || 0,
          'Trạng thái': conf?.label || po.status,
          'Người phê duyệt': po.approvedBy || '',
          'Lý do từ chối': po.rejectedReason || '',
          'Ghi chú': po.note || '',
        }
      })

      const worksheet = XLSX.utils.json_to_sheet(exportData)
      const workbook = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(workbook, worksheet, 'Don_Mua_Hang_PO')
      XLSX.writeFile(
        workbook,
        `WMS_Danh_sach_Don_Mua_Hang_PO_${new Date().toISOString().slice(0, 10)}.xlsx`
      )
      message.success(`Đã xuất file Excel ${exportList.length} đơn mua hàng thành công!`)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi xuất danh sách Excel'))
    } finally {
      setExporting(false)
    }
  }

  const columns: ColumnsType<PurchaseOrder> = [
    {
      title: 'Mã PO',
      dataIndex: 'poNumber',
      key: 'poNumber',
      width: 155,
      render: (val: string, r) => (
        <button
          type="button"
          onClick={() => {
            setSelectedOrder(r)
            setIsDetailModalOpen(true)
          }}
          className="font-mono font-bold text-blue-700 bg-blue-50 hover:bg-blue-100 px-2 py-0.5 rounded text-xs cursor-pointer text-left transition-colors"
        >
          {val}
        </button>
      ),
    },
    {
      title: 'Nhà cung cấp',
      key: 'supplier',
      width: 220,
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
      width: 160,
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
      width: 95,
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
      width: 150,
      fixed: 'right',
      render: (_, r) => {
        const isPending = r.status === 'PENDING'
        const isCancellable = r.status === 'DRAFT' || r.status === 'PENDING'

        return (
          <Space size="small">
            <Tooltip title="Xem chi tiết đơn mua hàng">
              <Button
                size="small"
                type="text"
                icon={<EyeOutlined className="text-blue-600" />}
                onClick={() => {
                  setSelectedOrder(r)
                  setIsDetailModalOpen(true)
                }}
              />
            </Tooltip>

            {isPending && canManage && (
              <>
                <Tooltip title="Phê duyệt đơn mua ngay">
                  <Popconfirm
                    title="Phê duyệt đơn mua?"
                    description={`Xác nhận phê duyệt đơn "${r.poNumber}"?`}
                    icon={<ExclamationCircleOutlined className="text-green-500" />}
                    onConfirm={() => handleQuickApprove(r)}
                    okText="Duyệt"
                    cancelText="Hủy"
                    okButtonProps={{ className: 'bg-green-600 hover:bg-green-700' }}
                  >
                    <Button
                      size="small"
                      type="text"
                      icon={<CheckCircleOutlined className="text-green-600" />}
                    />
                  </Popconfirm>
                </Tooltip>

                <Tooltip title="Từ chối phê duyệt đơn này">
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<CloseCircleOutlined />}
                    onClick={() => {
                      setOrderToReject(r)
                      setIsRejectModalOpen(true)
                    }}
                  />
                </Tooltip>
              </>
            )}

            {isCancellable && canManage && (
              <Tooltip title="Hủy đơn mua">
                <Popconfirm
                  title="Hủy đơn mua hàng?"
                  description={`Bạn có chắc muốn hủy đơn "${r.poNumber}"?`}
                  onConfirm={() => handleQuickCancel(r)}
                  okText="Hủy đơn"
                  cancelText="Bỏ qua"
                  okButtonProps={{ danger: true }}
                >
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<StopOutlined className="text-slate-400 hover:text-red-600" />}
                  />
                </Popconfirm>
              </Tooltip>
            )}
          </Space>
        )
      },
    },
  ]

  // Danh sách các tab trạng thái
  const statusFilterTabs = [
    { key: 'ALL', label: `Tất cả (${summary?.totalOrders ?? total})` },
    {
      key: 'PENDING',
      label: (
        <span className="flex items-center gap-1.5">
          <span>Chờ duyệt</span>
          {(summary?.pendingOrders ?? 0) > 0 && (
            <Badge
              count={summary?.pendingOrders}
              className="site-badge-count-4"
              style={{ backgroundColor: '#fa8c16' }}
            />
          )}
        </span>
      ),
    },
    { key: 'APPROVED', label: `Đã duyệt (${summary?.approvedOrders ?? 0})` },
    { key: 'DRAFT', label: `Bản nháp (${summary?.draftOrders ?? 0})` },
    { key: 'REJECTED', label: `Đã từ chối (${summary?.rejectedOrders ?? 0})` },
    { key: 'CANCELLED', label: `Đã hủy (${summary?.cancelledOrders ?? 0})` },
  ]

  return (
    <div className="space-y-6">
      {/* Header trang */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <Title level={3} className="!mb-1 flex items-center gap-2 text-slate-800">
            <ShoppingCartOutlined className="text-blue-600" />
            <span>Quản lý & Phê duyệt Đơn Mua Hàng (PO)</span>
          </Title>
          <Text type="secondary" className="text-sm">
            Quy trình lập PO, lọc đơn chờ duyệt, phê duyệt / từ chối và đồng bộ Incoming Stock (FR10 - WMS-09)
          </Text>
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
            <div className="text-xl font-bold text-amber-600 mt-1 flex items-center gap-2">
              <span>{formatNumber(summary?.pendingOrders ?? 0)}</span>
              {(summary?.pendingOrders ?? 0) > 0 && (
                <Tag color="warning" className="text-[10px] m-0">Cần xử lý</Tag>
              )}
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
            <div className="text-xs text-slate-500">Ngân sách đã duyệt</div>
            <div className="text-xl font-bold text-purple-700 mt-1">
              {formatCurrency(summary?.totalApprovedAmount ?? 0)}
            </div>
          </Card>
        </Col>
      </Row>

      {/* Tabs chính: Danh sách đơn / Lập đơn mới */}
      <Tabs
        activeKey={activeTab}
        destroyInactiveTabPane
        onChange={(k) => setActiveTab(k as 'list' | 'create')}
        items={[
          {
            key: 'list',
            label: (
              <span className="flex items-center gap-1.5 px-1 font-medium">
                <FileTextOutlined />
                <span>Danh sách đơn mua hàng (PO List)</span>
              </span>
            ),
            children: (
              <Card variant="borderless" className="shadow-2xs space-y-4">
                {/* Thanh Tabs phân loại theo trạng thái */}
                <Tabs
                  activeKey={selectedStatusTab}
                  onChange={(key) => {
                    setSelectedStatusTab(key)
                    setPage(0)
                  }}
                  items={statusFilterTabs}
                  className="mb-3"
                />

                {/* Bộ lọc đa tiêu chí */}
                <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                  <div className="flex flex-wrap items-center gap-2 flex-1">
                    <Search
                      placeholder="Tìm theo Mã PO, Tên NCC..."
                      onSearch={(val) => {
                        setKeyword(val.trim() || undefined)
                        setPage(0)
                      }}
                      className="w-full sm:w-64"
                      allowClear
                    />

                    <Select
                      placeholder="Chọn Nhà cung cấp"
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

                    <Select
                      placeholder="Kho tiếp nhận"
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
                      className="w-48"
                      options={warehouses.map((w) => ({
                        value: w.id,
                        label: `[${w.code}] ${w.name}`,
                      }))}
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

                    {(keyword ||
                      selectedSupplierId !== undefined ||
                      selectedWarehouseId !== undefined ||
                      selectedStatusTab !== 'ALL' ||
                      dateRange[0]) && (
                      <Button
                        onClick={() => {
                          setKeyword(undefined)
                          setSelectedSupplierId(undefined)
                          setSelectedWarehouseId(undefined)
                          setSelectedStatusTab('ALL')
                          setDateRange([undefined, undefined])
                          setPage(0)
                        }}
                      >
                        Xóa bộ lọc
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
                  scroll={{ x: 1200 }}
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

      {/* Modal Xem chi tiết Đơn PO & Phê duyệt/Từ chối */}
      <PurchaseOrderDetailModal
        open={isDetailModalOpen}
        order={selectedOrder}
        onClose={() => {
          setIsDetailModalOpen(false)
          setSelectedOrder(null)
        }}
        onOrderUpdated={(updated) => {
          setSelectedOrder(updated)
          reloadData()
        }}
        canManage={canManage}
      />

      {/* Modal Từ chối phê duyệt nhanh */}
      <RejectPurchaseOrderModal
        open={isRejectModalOpen}
        order={orderToReject}
        onClose={() => {
          setIsRejectModalOpen(false)
          setOrderToReject(null)
        }}
        onSuccess={() => {
          reloadData()
        }}
      />
    </div>
  )
}
