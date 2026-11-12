import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  FileDoneOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  RocketOutlined,
  SearchOutlined,
  ShoppingOutlined,
  StopOutlined,
  WarningOutlined,
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
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../services/api'
import { formatCurrency } from '../../utils/format'
import { getStoredUser } from '../../utils/storage'
import * as customerApi from '../customers/customerApi'
import type { Customer } from '../customers/types'
import * as warehouseApi from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import CreateSalesInvoiceModal from './components/CreateSalesInvoiceModal'
import CustomerReceivablesTab from './components/CustomerReceivablesTab'
import RealtimeOutboundBoard from './components/RealtimeOutboundBoard'
import SalesOrderDetailModal from './components/SalesOrderDetailModal'
import SalesOrderForm from './components/SalesOrderForm'
import SmartPickingModal from './components/SmartPickingModal'
import * as salesApi from './salesApi'
import { SALES_STATUS_CONFIG } from './statusConfig'
import type { OrderStatus, SalesOrder, SalesOrderSummary } from './types'

const { Title, Text } = Typography
const { RangePicker } = DatePicker

export default function SalesPage() {
  const { message } = App.useApp()

  // Phân quyền theo RBAC với fallback an toàn ?? false
  const currentUser = getStoredUser()
  const canConfirm =
    currentUser?.roles?.some((r) =>
      ['ADMIN', 'SALES', 'WAREHOUSE_MANAGER'].includes(r)
    ) ?? false

  const canCancel =
    currentUser?.roles?.some((r) =>
      ['ADMIN', 'SALES'].includes(r)
    ) ?? false

  const canDelete =
    currentUser?.roles?.some((r) =>
      ['ADMIN', 'SALES'].includes(r)
    ) ?? false

  const canManageInvoice =
    currentUser?.roles?.some((r) =>
      ['ADMIN', 'SALES', 'ACCOUNTANT'].includes(r)
    ) ?? false

  // Tab chính: 'list' | 'create' | 'receivables'
  const [activeTab, setActiveTab] = useState<string>('list')
  const [formResetKey, setFormResetKey] = useState<number>(1)
  const [refreshKey, setRefreshKey] = useState<number>(0)

  // Sub-filter tab theo trạng thái đơn hàng
  const [selectedStatusTab, setSelectedStatusTab] = useState<string>('ALL')

  // Danh sách đơn bán hàng
  const [orders, setOrders] = useState<SalesOrder[]>([])
  const [totalOrders, setTotalOrders] = useState<number>(0)
  const [loading, setLoading] = useState<boolean>(false)
  const [summary, setSummary] = useState<SalesOrderSummary | null>(null)
  const [exporting, setExporting] = useState<boolean>(false)

  // Bộ lọc
  const [keyword, setKeyword] = useState<string>('')
  const [customerFilter, setCustomerFilter] = useState<number | undefined>(undefined)
  const [warehouseFilter, setWarehouseFilter] = useState<number | undefined>(undefined)
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(null)
  const [page, setPage] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(10)

  // Metadata lọc
  const [customers, setCustomers] = useState<Customer[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  // State các Modal
  const [selectedOrder, setSelectedOrder] = useState<SalesOrder | null>(null)
  const [detailModalOpen, setDetailModalOpen] = useState<boolean>(false)

  const [orderForInvoice, setOrderForInvoice] = useState<SalesOrder | null>(null)
  const [invoiceModalOpen, setInvoiceModalOpen] = useState<boolean>(false)

  const [orderForPicking, setOrderForPicking] = useState<SalesOrder | null>(null)
  const [isPickingModalOpen, setIsPickingModalOpen] = useState<boolean>(false)

  // Tải danh mục khách hàng và kho cho bộ lọc
  useEffect(() => {
    let ignore = false
    const loadFilters = async () => {
      try {
        const [cRes, wRes] = await Promise.all([
          customerApi.fetchCustomers({ active: true, size: 100 }),
          warehouseApi.fetchWarehouses({ active: true }),
        ])
        if (!ignore) {
          setCustomers(cRes.content || [])
          setWarehouses(wRes || [])
        }
      } catch {
        // Bỏ qua lỗi tải danh mục lọc
      }
    }
    void loadFilters()
    return () => {
      ignore = true
    }
  }, [])

  // Tải danh sách đơn bán và báo cáo summary
  useEffect(() => {
    let ignore = false
    const fetchOrders = async () => {
      setLoading(true)
      try {
        const currentStatus =
          selectedStatusTab !== 'ALL' ? (selectedStatusTab as OrderStatus) : undefined

        const [orderRes, sumRes] = await Promise.all([
          salesApi.fetchSalesOrders({
            keyword: keyword.trim() || undefined,
            customerId: customerFilter,
            warehouseId: warehouseFilter,
            status: currentStatus,
            fromOrderDate: dateRange?.[0] ? dateRange[0].format('YYYY-MM-DD') : undefined,
            toOrderDate: dateRange?.[1] ? dateRange[1].format('YYYY-MM-DD') : undefined,
            page,
            size: pageSize,
          }),
          salesApi.fetchSalesOrderSummary(),
        ])

        if (!ignore) {
          setOrders(orderRes.content || [])
          setTotalOrders(orderRes.totalElements || 0)
          setSummary(sumRes)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Lỗi khi tải danh sách đơn bán hàng'))
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
    selectedStatusTab,
    customerFilter,
    warehouseFilter,
    dateRange,
    page,
    pageSize,
    refreshKey,
    message,
  ])

  // Xuất file Excel toàn bộ danh sách đơn bán hàng khớp bộ lọc (lặp qua các trang size: 100)
  const handleExportExcel = async () => {
    try {
      setExporting(true)
      const currentStatus =
        selectedStatusTab !== 'ALL' ? (selectedStatusTab as OrderStatus) : undefined

      let currentPage = 0
      let isLast = false
      let exportList: SalesOrder[] = []

      while (!isLast) {
        const data = await salesApi.fetchSalesOrders({
          keyword: keyword.trim() || undefined,
          customerId: customerFilter,
          warehouseId: warehouseFilter,
          status: currentStatus,
          fromOrderDate: dateRange?.[0] ? dateRange[0].format('YYYY-MM-DD') : undefined,
          toOrderDate: dateRange?.[1] ? dateRange[1].format('YYYY-MM-DD') : undefined,
          page: currentPage,
          size: 100, // giới hạn max-page-size của Spring
        })
        const items = data.content || []
        exportList = exportList.concat(items)
        isLast = Boolean(data.last || items.length === 0 || currentPage >= (data.totalPages - 1))
        currentPage++
      }

      if (exportList.length === 0) {
        message.warning('Không có dữ liệu đơn bán hàng để xuất Excel')
        return
      }

      const exportData = exportList.map((so, index) => {
        const conf = SALES_STATUS_CONFIG[so.status]
        return {
          STT: index + 1,
          'Mã SO': so.soNumber,
          'Khách hàng': so.customerName || `ID: ${so.customerId}`,
          'Mã KH': so.customerCode || '',
          'Kho xuất hàng': so.warehouseName || (so.warehouseId ? `Kho #${so.warehouseId}` : ''),
          'Ngày đặt đơn': so.orderDate ? dayjs(so.orderDate).format('DD/MM/YYYY') : '',
          'Ngày hẹn giao': so.expectedDate ? dayjs(so.expectedDate).format('DD/MM/YYYY') : '',
          'Số mặt hàng': so.items?.length || 0,
          'Tiền hàng (VNĐ)': so.subtotalAmount || 0,
          'Chiết khấu (VNĐ)': so.discountAmount || 0,
          'Thuế VAT (VNĐ)': so.taxAmount || 0,
          'Tổng thanh toán (VNĐ)': so.totalAmount || 0,
          'Trạng thái': conf?.label || so.status,
          'Lý do Backorder': so.backorderReason || '',
          'Người duyệt': so.confirmedBy || '',
          'Thời gian tạo': so.createdAt ? dayjs(so.createdAt).format('DD/MM/YYYY HH:mm') : '',
        }
      })

      const worksheet = XLSX.utils.json_to_sheet(exportData)
      const workbook = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(workbook, worksheet, 'Don_Ban_Hang_SO')
      XLSX.writeFile(
        workbook,
        `WMS_Danh_sach_Don_Ban_Hang_SO_${dayjs().format('YYYYMMDD')}.xlsx`
      )
      message.success(`Đã xuất file Excel ${exportList.length} đơn bán hàng thành công!`)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi xuất danh sách Excel'))
    } finally {
      setExporting(false)
    }
  }

  // Phê duyệt nhanh đơn bán
  const handleQuickConfirm = async (order: SalesOrder) => {
    try {
      await salesApi.confirmSalesOrder(order.id)
      message.success(`Đã xác nhận đơn bán "${order.soNumber}"! Tồn kho đã được giữ chỗ (Soft Reservation).`)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi xác nhận đơn bán hàng'))
    }
  }

  // Xóa đơn bán nháp / đã hủy
  const handleDeleteOrder = async (order: SalesOrder) => {
    try {
      await salesApi.deleteSalesOrder(order.id)
      message.success(`Đã xóa đơn bán "${order.soNumber}"!`)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi xóa đơn bán hàng'))
    }
  }

  // Hủy nhanh đơn bán
  const handleQuickCancel = async (order: SalesOrder) => {
    try {
      await salesApi.cancelSalesOrder(order.id)
      message.success(`Đã hủy đơn bán "${order.soNumber}"! Lượng hàng giữ đã được giải phóng.`)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi hủy đơn bán hàng'))
    }
  }

  // Cột hiển thị bảng
  const columns: ColumnsType<SalesOrder> = [
    {
      title: 'Mã SO',
      dataIndex: 'soNumber',
      key: 'soNumber',
      width: 160,
      render: (val: string, record) => (
        <div>
          <span
            className="font-semibold text-blue-600 cursor-pointer hover:underline"
            onClick={() => {
              setSelectedOrder(record)
              setDetailModalOpen(true)
            }}
          >
            {val}
          </span>
          {record.warehouseName && (
            <div className="text-xs text-gray-500 truncate" title={record.warehouseName}>
              Kho: {record.warehouseName}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Khách hàng',
      dataIndex: 'customerName',
      key: 'customerName',
      width: 220,
      render: (val: string, record) => (
        <div>
          <div className="font-medium text-slate-800">{val || 'Khách vãng lai'}</div>
          {record.customerCode && (
            <Tag color="geekblue" className="text-xs m-0">
              {record.customerCode}
            </Tag>
          )}
        </div>
      ),
    },
    {
      title: 'Ngày đặt',
      dataIndex: 'orderDate',
      key: 'orderDate',
      width: 110,
      render: (val: string) => (val ? dayjs(val).format('DD/MM/YYYY') : '—'),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 170,
      render: (st: OrderStatus, record) => {
        const cfg = SALES_STATUS_CONFIG[st] || { label: st, color: 'default', icon: null }
        return (
          <div>
            <Tag color={cfg.color} icon={cfg.icon}>
              {cfg.label}
            </Tag>
            {st === 'BACKORDER' && record.backorderReason && (
              <div
                className="text-[11px] text-amber-700 mt-1 line-clamp-1"
                title={record.backorderReason}
              >
                {record.backorderReason}
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Tổng tiền hàng',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 150,
      align: 'right',
      render: (val: number) => (
        <span className="font-semibold text-slate-900 tabular-nums">{formatCurrency(val)}</span>
      ),
    },
    {
      title: 'Số mặt hàng',
      key: 'itemCount',
      width: 100,
      align: 'center',
      render: (_val, record) => (
        <Badge
          count={record.items?.length || 0}
          overflowCount={999}
          style={{ backgroundColor: '#108ee9' }}
        />
      ),
    },
    {
      title: 'Thời gian tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (val?: string) => (val ? dayjs(val).format('DD/MM/YYYY HH:mm') : '—'),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 130,
      align: 'center',
      fixed: 'right',
      render: (_val, r) => {
        const isPendingOrDraft = r.status === 'DRAFT' || r.status === 'BACKORDER'
        const isCancellable = ['DRAFT', 'CONFIRMED', 'BACKORDER'].includes(r.status)
        const canCreateInvoice = ['CONFIRMED', 'COMPLETED'].includes(r.status)
        const isDeletable = ['DRAFT', 'CANCELLED'].includes(r.status)

        return (
          <Space size="small">
            {/* Xem chi tiết */}
            <Tooltip title="Xem chi tiết đơn bán">
              <Button
                size="small"
                type="text"
                icon={<EyeOutlined className="text-blue-600" />}
                onClick={() => {
                  setSelectedOrder(r)
                  setDetailModalOpen(true)
                }}
              />
            </Tooltip>

            {/* Xác nhận nhanh */}
            {isPendingOrDraft && canConfirm && (
              <Tooltip title="Xác nhận đơn (Giữ hàng ảo)">
                <Popconfirm
                  title="Xác nhận đơn bán?"
                  description={`Xác nhận duyệt đơn "${r.soNumber}" và thực hiện Soft Reservation?`}
                  icon={<ExclamationCircleOutlined className="text-green-500" />}
                  onConfirm={() => handleQuickConfirm(r)}
                  okText="Xác nhận"
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
            )}

            {/* Lấy hàng thông minh (Smart Picking - WMS-12.3) */}
            {r.status === 'CONFIRMED' && (
              <Tooltip title="Lấy hàng thông minh (Smart Picking Route)">
                <Button
                  size="small"
                  type="text"
                  icon={<RocketOutlined className="text-orange-500 hover:text-orange-600" />}
                  onClick={() => {
                    setOrderForPicking(r)
                    setIsPickingModalOpen(true)
                  }}
                />
              </Tooltip>
            )}

            {/* Tạo hóa đơn bán */}
            {canCreateInvoice && canManageInvoice && (
              <Tooltip title="Lập hóa đơn bán">
                <Button
                  size="small"
                  type="text"
                  icon={<FileTextOutlined className="text-purple-600" />}
                  onClick={() => {
                    setOrderForInvoice(r)
                    setInvoiceModalOpen(true)
                  }}
                />
              </Tooltip>
            )}

            {/* Hủy đơn nhanh */}
            {isCancellable && canCancel && (
              <Tooltip title="Hủy đơn bán">
                <Popconfirm
                  title="Hủy đơn bán hàng?"
                  description={`Bạn có chắc muốn hủy đơn "${r.soNumber}"?`}
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

            {/* Xóa đơn (chỉ DRAFT hoặc CANCELLED) */}
            {isDeletable && canDelete && (
              <Tooltip title="Xóa đơn bán">
                <Popconfirm
                  title="Xóa đơn bán hàng?"
                  description={`Bạn có chắc muốn xóa hẳn đơn "${r.soNumber}"?`}
                  onConfirm={() => handleDeleteOrder(r)}
                  okText="Xóa"
                  cancelText="Bỏ qua"
                  okButtonProps={{ danger: true }}
                >
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<DeleteOutlined className="text-red-500 hover:text-red-700" />}
                  />
                </Popconfirm>
              </Tooltip>
            )}
          </Space>
        )
      },
    },
  ]

  // Danh sách các tab trạng thái (sub-filters)
  const statusFilterTabs = [
    { key: 'ALL', label: `Tất cả (${summary?.totalOrders ?? totalOrders})` },
    {
      key: 'CONFIRMED',
      label: (
        <span className="flex items-center gap-1.5">
          <span>Đã xác nhận</span>
          {(summary?.confirmedOrders ?? 0) > 0 && (
            <Badge
              count={summary?.confirmedOrders}
              style={{ backgroundColor: '#52c41a' }}
            />
          )}
        </span>
      ),
    },
    {
      key: 'BACKORDER',
      label: (
        <span className="flex items-center gap-1.5">
          <span>Thiếu hàng (Backorder)</span>
          {(summary?.backorders ?? 0) > 0 && (
            <Badge
              count={summary?.backorders}
              style={{ backgroundColor: '#faad14' }}
            />
          )}
        </span>
      ),
    },
    { key: 'DRAFT', label: `Bản nháp (${summary?.draftOrders ?? 0})` },
    { key: 'COMPLETED', label: `Hoàn thành (${summary?.completedOrders ?? 0})` },
    { key: 'CANCELLED', label: `Đã hủy (${summary?.cancelledOrders ?? 0})` },
  ]

  return (
    <div className="p-4 space-y-4">
      {/* Header trang */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <Title level={3} className="!mb-1 flex items-center gap-2 text-slate-800">
            <ShoppingOutlined className="text-blue-600" />
            <span>Quản lý Đơn Bán Hàng & Công Nợ Phải Thu (SO)</span>
          </Title>
          <Text type="secondary" className="text-sm">
            Quy trình bán hàng, kiểm tra ATP, giữ hàng ảo (Soft Reservation), tạo hóa đơn và theo dõi công nợ phải thu (FR14, FR15, FR16, FR18)
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
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setFormResetKey((k) => k + 1)
              setActiveTab('create')
            }}
            className="bg-blue-600 hover:bg-blue-700"
          >
            Lập Đơn Bán Mới
          </Button>
        </Space>
      </div>

      {/* Metrics Summary Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6} lg={4}>
          <Card size="small" className="shadow-2xs border-slate-200">
            <Statistic
              title="Tổng số đơn bán"
              value={summary?.totalOrders ?? totalOrders}
              prefix={<ShoppingOutlined className="text-blue-500 mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6} lg={5}>
          <Card size="small" className="shadow-2xs border-slate-200">
            <Statistic
              title="Đơn bán chờ xử lý"
              value={summary?.pendingSaleOrders ?? 0}
              valueStyle={{ color: '#1677ff' }}
              prefix={<ClockCircleOutlined className="mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6} lg={5}>
          <Card size="small" className="shadow-2xs border-slate-200">
            <Statistic
              title="Thiếu hàng (Backorder)"
              value={summary?.backorders ?? 0}
              valueStyle={{ color: '#faad14' }}
              prefix={<WarningOutlined className="mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6} lg={5}>
          <Card size="small" className="shadow-2xs border-slate-200">
            <Statistic
              title="Đã xác nhận (Giữ hàng)"
              value={summary?.confirmedOrders ?? 0}
              valueStyle={{ color: '#52c41a' }}
              prefix={<CheckCircleOutlined className="mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6} lg={5}>
          <Card size="small" className="shadow-2xs border-slate-200">
            <Statistic
              title="Doanh thu xác nhận"
              value={summary?.totalRevenue ?? 0}
              formatter={(v) => formatCurrency(Number(v))}
              valueStyle={{ color: '#0958d9', fontSize: '1.15rem' }}
              prefix={<DollarOutlined className="mr-1" />}
            />
          </Card>
        </Col>
      </Row>

      {/* Tabs Layout Chính */}
      <Card className="shadow-2xs border-slate-200">
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key)}
          destroyInactiveTabPane
          items={[
            {
              key: 'list',
              label: (
                <Space>
                  <ShoppingOutlined />
                  <span>Danh sách Đơn bán hàng (SO List)</span>
                  <Badge count={totalOrders} overflowCount={999} style={{ backgroundColor: '#1677ff' }} />
                </Space>
              ),
              children: (
                <div className="space-y-4 pt-1">
                  {/* Thanh Tabs phân loại theo trạng thái */}
                  <Tabs
                    activeKey={selectedStatusTab}
                    onChange={(key) => {
                      setSelectedStatusTab(key)
                      setPage(0)
                    }}
                    items={statusFilterTabs}
                    className="mb-2"
                  />

                  {/* Filters Row */}
                  <Row gutter={[12, 12]} align="middle">
                    <Col xs={24} sm={12} md={7}>
                      <Input
                        placeholder="Tìm theo mã SO, tên khách, ghi chú..."
                        prefix={<SearchOutlined className="text-gray-400" />}
                        value={keyword}
                        onChange={(e) => {
                          setKeyword(e.target.value)
                          setPage(0)
                        }}
                        allowClear
                      />
                    </Col>
                    <Col xs={12} sm={6} md={6}>
                      <Select
                        placeholder="Chọn Khách hàng"
                        allowClear
                        showSearch
                        value={customerFilter}
                        onChange={(val) => {
                          setCustomerFilter(val)
                          setPage(0)
                        }}
                        filterOption={(input, option) =>
                          (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
                        }
                        className="w-full"
                        options={customers.map((c) => ({
                          value: c.id,
                          label: `${c.code} - ${c.name}`,
                        }))}
                      />
                    </Col>
                    <Col xs={12} sm={6} md={5}>
                      <Select
                        placeholder="Chọn Kho xuất"
                        allowClear
                        value={warehouseFilter}
                        onChange={(val) => {
                          setWarehouseFilter(val)
                          setPage(0)
                        }}
                        className="w-full"
                        options={warehouses.map((w) => ({
                          value: w.id,
                          label: w.name,
                        }))}
                      />
                    </Col>
                    <Col xs={24} sm={12} md={6}>
                      <RangePicker
                        className="w-full"
                        format="DD/MM/YYYY"
                        value={dateRange}
                        onChange={(dates) => {
                          setDateRange(dates)
                          setPage(0)
                        }}
                      />
                    </Col>
                  </Row>

                  <div className="flex justify-between items-center pt-1">
                    <Button
                      icon={<ReloadOutlined />}
                      onClick={() => setRefreshKey((k) => k + 1)}
                    >
                      Làm mới dữ liệu
                    </Button>
                    <Text type="secondary" className="text-xs">
                      Hiển thị {orders.length} / tổng số {totalOrders} đơn hàng
                    </Text>
                  </div>

                  {/* Table danh sách đơn SO */}
                  <Table
                    rowKey="id"
                    columns={columns}
                    dataSource={orders}
                    loading={loading}
                    pagination={{
                      current: page + 1,
                      pageSize,
                      total: totalOrders,
                      showSizeChanger: true,
                      pageSizeOptions: ['10', '20', '50'],
                      onChange: (p, ps) => {
                        setPage(p - 1)
                        setPageSize(ps)
                      },
                    }}
                    scroll={{ x: 1100 }}
                  />
                </div>
              ),
            },
            {
              key: 'create',
              label: (
                <Space>
                  <FileDoneOutlined />
                  <span>Lập Đơn Bán Hàng (Form SO)</span>
                </Space>
              ),
              children: (
                <div className="pt-2">
                  <SalesOrderForm
                    key={formResetKey}
                    onSuccess={() => {
                      setFormResetKey((prev) => prev + 1)
                      setActiveTab('list')
                      setRefreshKey((k) => k + 1)
                    }}
                    onCancel={() => {
                      setActiveTab('list')
                    }}
                  />
                </div>
              ),
            },
            {
              key: 'outbound',
              label: (
                <Space>
                  <RocketOutlined className="text-orange-500" />
                  <span>Xuất kho & Lộ trình lấy hàng (FR17)</span>
                </Space>
              ),
              children: (
                <RealtimeOutboundBoard
                  onOpenOrderDetail={(order) => {
                    setSelectedOrder(order)
                    setDetailModalOpen(true)
                  }}
                />
              ),
            },
            {
              key: 'receivables',
              label: (
                <Space>
                  <DollarOutlined />
                  <span>Hóa đơn bán & Công nợ phải thu</span>
                </Space>
              ),
              children: <CustomerReceivablesTab />,
            },
          ]}
        />
      </Card>

      {/* Modal Xem chi tiết đơn bán hàng */}
      <SalesOrderDetailModal
        open={detailModalOpen}
        order={selectedOrder}
        onClose={() => {
          setDetailModalOpen(false)
          setSelectedOrder(null)
        }}
        onOrderUpdated={(updated) => {
          setSelectedOrder(updated)
          setRefreshKey((k) => k + 1)
        }}
        onOpenInvoiceModal={(order) => {
          setOrderForInvoice(order)
          setInvoiceModalOpen(true)
        }}
        onOpenPicking={(order) => {
          setOrderForPicking(order)
          setIsPickingModalOpen(true)
        }}
        canConfirm={canConfirm}
        canCancel={canCancel}
        canManageInvoice={canManageInvoice}
      />

      {/* Modal Lộ trình lấy hàng thông minh (Smart Picking View - WMS-12.3) */}
      <SmartPickingModal
        open={isPickingModalOpen}
        order={orderForPicking}
        onClose={() => {
          setIsPickingModalOpen(false)
          setOrderForPicking(null)
        }}
      />

      {/* Modal Lập Hóa đơn bán hàng (FR18) */}
      <CreateSalesInvoiceModal
        open={invoiceModalOpen}
        order={orderForInvoice}
        onClose={() => {
          setInvoiceModalOpen(false)
          setOrderForInvoice(null)
        }}
        onSuccess={() => {
          setRefreshKey((k) => k + 1)
        }}
      />
    </div>
  )
}
