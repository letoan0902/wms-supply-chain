import {
  BarcodeOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  FileDoneOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
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
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../services/api'
import { formatCurrency, formatNumber } from '../../utils/format'
import ForbiddenResult from '../../components/ForbiddenResult'
import { useCan } from '../auth/useCan'
import * as supplierApi from '../suppliers/supplierApi'
import type { Supplier } from '../suppliers/types'
import * as warehouseApi from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import PurchaseOrderDetailModal from './components/PurchaseOrderDetailModal'
import PurchaseOrderForm from './components/PurchaseOrderForm'
import RejectPurchaseOrderModal from './components/RejectPurchaseOrderModal'
import GoodsReceiptTable from './components/GoodsReceiptTable'
import SmartGoodsReceiptForm from './components/SmartGoodsReceiptForm'
import PurchaseBillsTable from './components/PurchaseBillsTable'
import { readPurchasePrefill, type PurchaseOrderPrefill } from './purchaseDraft'
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
  // Phân quyền theo bảng quyền dùng chung (features/auth/permissions.ts, khớp backend)
  const can = useCan()
  const canRead = can('purchaseOrder.read')
  const canApprove = can('purchaseOrder.approve')
  // Lập, sửa, hủy đơn mua: ADMIN, Mua hàng
  const canCancel = can('purchaseOrder.write')
  const canCreatePo = can('purchaseOrder.write')
  // Lập phiếu nhập kho: ADMIN, Quản lý kho, Nhân viên kho
  const canCreateGrn = can('goodsReceipt.create')
  const canViewBills = can('purchaseBill.read')
  // Vai trò không phải ADMIN, ACCOUNTANT chưa được gán kho thì không đọc được danh sách kho (403)
  const canReadWarehouses = can('warehouse.read')

  // Tab active: 'list' | 'create' | 'grn' | 'create-grn' | 'bills'
  const [activeTab, setActiveTab] = useState<'list' | 'create' | 'grn' | 'create-grn' | 'bills'>('list')
  const [formKey, setFormKey] = useState(0)
  const [grnFormKey, setGrnFormKey] = useState(0)
  const [selectedPoIdForGrn, setSelectedPoIdForGrn] = useState<number | undefined>(undefined)

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

  // Mở form lập đơn đã điền sẵn khi được điều hướng tới kèm `state` (ví dụ "Tạo đơn mua nháp" từ cảnh báo tồn thấp).
  // Mỗi lần điều hướng (location.key) chỉ áp một lần; sau đó xóa `state` khỏi lịch sử để tải lại trang không mở lại form.
  const location = useLocation()
  const navigate = useNavigate()
  const incomingPrefill = readPurchasePrefill(location.state)
  const [prefill, setPrefill] = useState<PurchaseOrderPrefill | null>(null)
  const [appliedPrefillKey, setAppliedPrefillKey] = useState<string | null>(null)
  if (incomingPrefill && canCreatePo && appliedPrefillKey !== location.key) {
    setAppliedPrefillKey(location.key)
    setPrefill(incomingPrefill)
    setFormKey((k) => k + 1)
    setActiveTab('create')
  }
  const hasIncomingPrefill = incomingPrefill !== null
  useEffect(() => {
    if (hasIncomingPrefill) {
      navigate(`${location.pathname}${location.search}`, { replace: true, state: null })
    }
  }, [hasIncomingPrefill, navigate, location.pathname, location.search])

  // Tải danh mục NCC và Kho cho bộ lọc
  useEffect(() => {
    if (!canRead) return
    let ignore = false
    const loadFilterData = async () => {
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
        console.error('Không thể tải danh mục bộ lọc', err)
      }
    }
    void loadFilterData()
    return () => {
      ignore = true
    }
  }, [canRead, canReadWarehouses])

  // Tải danh sách đơn PO
  useEffect(() => {
    if (!canRead) return
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
    canRead,
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
    if (!canRead) return
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
  }, [canRead, refreshKey])

  // Khi lập đơn mua hàng thành công từ Tab tạo mới
  const handleCreateSuccess = (created: PurchaseOrder) => {
    reloadData()
    setPrefill(null)
    setFormKey((k) => k + 1)
    setActiveTab('list')
    setSelectedOrder(created)
    setIsDetailModalOpen(true)
  }

  const handleOpenCreateTab = () => {
    setPrefill(null)
    setFormKey((k) => k + 1)
    setActiveTab('create')
  }

  // Rời tab lập đơn thì bỏ giá trị điền sẵn: lần sau mở tab là một đơn trống
  const handleTabChange = (key: string) => {
    if (key !== 'create') setPrefill(null)
    setActiveTab(key as typeof activeTab)
  }

  const handleOpenCreateGrnTab = (poId?: number) => {
    setSelectedPoIdForGrn(poId)
    setGrnFormKey((k) => k + 1)
    setActiveTab('create-grn')
  }

  // Phê duyệt nhanh đơn mua từ bảng
  const handleQuickApprove = async (order: PurchaseOrder) => {
    try {
      await purchasesApi.approvePurchaseOrder(order.id)
      message.success(`Đã phê duyệt đơn mua "${order.poNumber}"! Đã gửi sự kiện sang kho.`)
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

      let currentPage = 0
      let isLast = false
      let exportList: PurchaseOrder[] = []

      while (!isLast) {
        const data = await purchasesApi.fetchPurchaseOrders({
          keyword,
          supplierId: selectedSupplierId,
          warehouseId: selectedWarehouseId,
          status: currentStatus,
          fromOrderDate: dateRange[0],
          toOrderDate: dateRange[1],
          page: currentPage,
          size: 100,
        })
        const items = data.content || []
        exportList = exportList.concat(items)
        isLast = Boolean(data.last || items.length === 0 || currentPage >= (data.totalPages - 1))
        currentPage++
      }
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
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">#</span>,
      key: 'index',
      width: 44,
      align: 'center',
      render: (_, __, idx) => (
        <span className="text-xs font-mono text-slate-400">{page * pageSize + idx + 1}</span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Mã PO</span>,
      dataIndex: 'poNumber',
      key: 'poNumber',
      width: 135,
      render: (val: string, r) => (
        <button
          type="button"
          onClick={() => {
            setSelectedOrder(r)
            setIsDetailModalOpen(true)
          }}
          className="font-mono text-sm font-semibold text-blue-600 hover:text-blue-800 hover:underline cursor-pointer text-left transition-colors"
        >
          {val}
        </button>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Nhà cung cấp</span>,
      key: 'supplier',
      width: 200,
      render: (_, r) => (
        <div className="py-0.5">
          <div className="font-semibold text-slate-900 text-sm leading-snug line-clamp-1" title={r.supplierName || 'NCC'}>
            {r.supplierName || 'NCC'}
          </div>
          <div className="text-xs text-slate-500 font-mono mt-0.5">ID: {r.supplierId}</div>
        </div>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Kho nhận hàng</span>,
      dataIndex: 'warehouseName',
      key: 'warehouseName',
      width: 145,
      render: (wh: string | null, r) => (
        <div className="py-0.5 flex items-center gap-1.5 text-sm text-slate-700 font-medium">
          <ShopOutlined className="text-slate-400 text-sm shrink-0" />
          <span className="leading-snug truncate" title={wh || (r.warehouseId ? `Kho #${r.warehouseId}` : 'Chưa chỉ định')}>
            {wh || (r.warehouseId ? `Kho #${r.warehouseId}` : 'Chưa chỉ định')}
          </span>
        </div>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Ngày đặt / Nhận</span>,
      key: 'dates',
      width: 140,
      render: (_, r) => (
        <div className="text-xs space-y-0.5 py-0.5">
          <div className="text-slate-800 font-medium">
            Đặt: {r.orderDate ? new Date(r.orderDate).toLocaleDateString('vi-VN') : '-'}
          </div>
          <div className="text-slate-500">
            Dự kiến: {r.expectedDate ? new Date(r.expectedDate).toLocaleDateString('vi-VN') : 'Chưa có'}
          </div>
        </div>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Mặt hàng</span>,
      key: 'itemsCount',
      width: 85,
      align: 'center',
      render: (_, r) => (
        <span className="font-mono text-xs font-semibold text-slate-700 bg-slate-100 px-2 py-0.5 rounded border border-slate-200/80">
          {r.items?.length || 0} dòng
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Tổng tiền (VNĐ)</span>,
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 135,
      align: 'right',
      render: (val: number) => (
        <span className="font-mono font-bold text-slate-900 text-sm tabular-nums">
          {formatCurrency(val || 0)}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Trạng thái</span>,
      dataIndex: 'status',
      key: 'status',
      width: 130,
      align: 'center',
      render: (st: OrderStatus) => {
        const conf = STATUS_CONFIG[st] ?? { label: st, color: 'default', icon: null }
        return (
          <Tag color={conf.color} className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">
            {conf.icon}
            <span>{conf.label}</span>
          </Tag>
        )
      },
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Thao tác</span>,
      key: 'actions',
      width: 100,
      align: 'center',
      render: (_, r) => {
        const isPending = r.status === 'PENDING'
        const isCancellable = r.status === 'DRAFT' || r.status === 'PENDING'

        return (
          <Space size={2}>
            <Tooltip title="Xem chi tiết đơn mua">
              <Button
                size="small"
                type="text"
                icon={<EyeOutlined className="text-slate-500 hover:text-blue-600" />}
                onClick={() => {
                  setSelectedOrder(r)
                  setIsDetailModalOpen(true)
                }}
              />
            </Tooltip>

            {canCreateGrn && (r.status === 'APPROVED' || r.status === 'PARTIALLY_RECEIVED') && (
              <Tooltip title="Nhập kho">
                <Button
                  data-write-action
                  size="small"
                  type="text"
                  icon={<BarcodeOutlined className="text-emerald-600 hover:text-emerald-700" />}
                  onClick={() => handleOpenCreateGrnTab(r.id)}
                />
              </Tooltip>
            )}

            {isPending && canApprove && (
              <>
                <Tooltip title="Phê duyệt">
                  <Popconfirm
                    title="Phê duyệt đơn mua?"
                    description={`Xác nhận duyệt đơn "${r.poNumber}"?`}
                    icon={<ExclamationCircleOutlined className="text-emerald-500" />}
                    onConfirm={() => handleQuickApprove(r)}
                    okText="Duyệt"
                    cancelText="Hủy"
                    okButtonProps={{ className: 'bg-emerald-600 hover:bg-emerald-700 text-white' }}
                  >
                    <Button
                      size="small"
                      type="text"
                      icon={<CheckCircleOutlined className="text-emerald-600 hover:text-emerald-700" />}
                    />
                  </Popconfirm>
                </Tooltip>

                <Tooltip title="Từ chối">
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

            {isCancellable && canCancel && (
              <Tooltip title="Hủy đơn">
                <Popconfirm
                  title="Hủy đơn mua?"
                  description={`Hủy đơn "${r.poNumber}"?`}
                  onConfirm={() => handleQuickCancel(r)}
                  okText="Hủy đơn"
                  cancelText="Bỏ qua"
                  okButtonProps={{ danger: true }}
                >
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<StopOutlined className="text-slate-400 hover:text-rose-600" />}
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
      label: `Chờ duyệt${(summary?.pendingOrders ?? 0) > 0 ? ` (${summary?.pendingOrders})` : ''}`,
    },
    { key: 'APPROVED', label: `Đã duyệt (${summary?.approvedOrders ?? 0})` },
    { key: 'PARTIALLY_RECEIVED', label: 'Nhập dở dang' },
    { key: 'CONFIRMED', label: 'Đã xác nhận' },
    { key: 'DRAFT', label: `Bản nháp (${summary?.draftOrders ?? 0})` },
    { key: 'REJECTED', label: `Đã từ chối (${summary?.rejectedOrders ?? 0})` },
    { key: 'CANCELLED', label: `Đã hủy (${summary?.cancelledOrders ?? 0})` },
  ]

  if (!canRead) {
    return (
      <ForbiddenResult description="Đơn mua hàng và phiếu nhập kho dành cho Quản trị viên, Quản lý kho, Nhân viên kho, Mua hàng và Kế toán." />
    )
  }

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
            Quy trình lập đơn mua, phê duyệt và theo dõi hàng đang về kho.
          </Text>
        </div>

        <Space wrap>
          <Button
            icon={<FileExcelOutlined />}
            onClick={handleExportExcel}
            loading={exporting}
            className="rounded-md text-xs font-medium bg-white hover:bg-slate-50 text-slate-700 border-slate-300"
          >
            Xuất Excel
          </Button>

          <Button
            icon={<ReloadOutlined />}
            onClick={reloadData}
            loading={loading || loadingSummary}
            className="rounded-md text-xs font-medium bg-white hover:bg-slate-50 text-slate-700 border-slate-300"
          >
            Làm mới
          </Button>

          {canCreateGrn && (
            <Button
              data-write-action
              icon={<BarcodeOutlined />}
              onClick={() => handleOpenCreateGrnTab()}
              className="rounded-md text-xs font-medium bg-white hover:bg-slate-50 text-slate-700 border-slate-300"
            >
              Nhập kho quét mã
            </Button>
          )}

          {canCreatePo && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleOpenCreateTab}
              className="!bg-blue-600 hover:!bg-blue-700 !border-blue-600 text-white font-medium rounded-md text-xs shadow-xs"
            >
              Lập đơn mua hàng mới
            </Button>
          )}
        </Space>
      </div>

      {/* 4 Cards Thống kê Tình hình đơn mua */}
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Tổng số đơn PO
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatNumber(summary?.totalOrders ?? total)}
              </span>
              <span className="text-sm text-slate-500 font-normal">đơn</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Toàn bộ đơn mua trong hệ thống
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
              <span>Đơn chờ duyệt</span>
              {(summary?.pendingOrders ?? 0) > 0 && (
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200/80">
                  Cần xử lý
                </span>
              )}
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatNumber(summary?.pendingOrders ?? 0)}
              </span>
              <span className="text-sm text-slate-500 font-normal">đơn</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Đơn đang chờ Quản lý duyệt
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Đã phê duyệt
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatNumber(summary?.approvedOrders ?? 0)}
              </span>
              <span className="text-sm text-slate-500 font-normal">đơn</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Đã duyệt, sẵn sàng nhận hàng
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Ngân sách đã duyệt
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums truncate">
                {formatCurrency(summary?.totalApprovedAmount ?? 0)}
              </span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Tổng giá trị các đơn mua đã duyệt
            </div>
          </Card>
        </Col>
      </Row>

      {/* Tabs chính: Danh sách đơn / Lập đơn mới */}
      <Tabs
        activeKey={activeTab}
        destroyInactiveTabPane
        onChange={handleTabChange}
        className="purchases-main-tabs"
        items={[
          {
            key: 'list',
            label: (
              <span className="flex items-center gap-1.5 px-1 font-medium">
                <FileTextOutlined />
                <span>Đơn mua hàng (PO)</span>
              </span>
            ),
            children: (
              <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
                {/* Thanh lọc trạng thái dạng Pills hiện đại */}
                <div className="flex flex-wrap items-center gap-1.5 p-1 bg-slate-100/90 rounded-lg w-fit border border-slate-200/60 mb-4">
                  {statusFilterTabs.map((tab) => {
                    const isActive = selectedStatusTab === tab.key
                    return (
                      <button
                        key={tab.key}
                        type="button"
                        onClick={() => {
                          setSelectedStatusTab(tab.key)
                          setPage(0)
                        }}
                        className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all cursor-pointer ${
                          isActive
                            ? 'bg-white text-slate-900 shadow-xs font-semibold'
                            : 'text-slate-600 hover:text-slate-900 hover:bg-slate-200/60'
                        }`}
                      >
                        {tab.label}
                      </button>
                    )
                  })}
                </div>

                {/* Bộ lọc đa tiêu chí */}
                <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                  <div className="flex flex-wrap items-center gap-2 flex-1">
                    <Search
                      placeholder="Tìm theo Mã PO, Tên NCC..."
                      onSearch={(val) => {
                        setKeyword(val.trim() || undefined)
                        setPage(0)
                      }}
                      className="w-full sm:w-60"
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
                      className="w-44"
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
                        className="rounded-md text-xs font-medium"
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
            key: 'grn',
            label: (
              <span className="flex items-center gap-1.5 px-1 font-medium">
                <FileDoneOutlined />
                <span>Phiếu nhập kho (GRN)</span>
              </span>
            ),
            children: (
              <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
                <GoodsReceiptTable onOpenCreateGrn={handleOpenCreateGrnTab} />
              </Card>
            ),
          },
          ...(canViewBills
            ? [
                {
                  key: 'bills',
                  label: (
                    <span className="flex items-center gap-1.5 px-1 font-medium">
                      <DollarOutlined />
                      <span>Hóa đơn & Công nợ</span>
                    </span>
                  ),
                  children: (
                    <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
                      <PurchaseBillsTable />
                    </Card>
                  ),
                },
              ]
            : []),
          ...(canCreatePo ? [{
            key: 'create',
            label: (
              <span className="flex items-center gap-1.5 px-1 font-medium">
                <PlusOutlined />
                <span>Lập đơn mua mới</span>
              </span>
            ),
            children: (
              <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
                <PurchaseOrderForm
                  key={formKey}
                  prefill={prefill}
                  onSuccess={handleCreateSuccess}
                  onCancel={() => handleTabChange('list')}
                />
              </Card>
            ),
          }] : []),
          ...(canCreateGrn ? [{
            key: 'create-grn',
            label: (
              <span className="flex items-center gap-1.5 px-1 font-medium">
                <BarcodeOutlined />
                <span>Nhập kho quét mã</span>
              </span>
            ),
            children: (
              <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
                <SmartGoodsReceiptForm
                  key={grnFormKey}
                  initialPoId={selectedPoIdForGrn}
                  onSuccess={() => {
                    reloadData()
                    setActiveTab('grn')
                  }}
                  onCancel={() => setActiveTab('grn')}
                />
              </Card>
            ),
          }] : []),
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
        onOpenCreateGrn={canCreateGrn ? handleOpenCreateGrnTab : undefined}
        canApprove={canApprove}
        canCancel={canCancel}
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
