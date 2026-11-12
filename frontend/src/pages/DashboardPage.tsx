import {
  AlertOutlined,
  CalendarOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
  LockOutlined,
  ShopOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Empty,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ReactNode } from 'react'
import { useEffect, useMemo, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Label,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import * as XLSX from 'xlsx'
import { fetchWarehouses } from '../features/warehouses/warehouseApi'
import type { Warehouse } from '../features/warehouses/types'
import { formatCurrency, formatNumber } from '../utils/format'
import { getStoredUser } from '../utils/storage'
import { fetchDashboardKpiSummary } from '../features/dashboard/dashboardApi'
import type { KpiCardKey, KpiSummaryResponse } from '../features/dashboard/types'
import {
  monthlyTradeFinancial,
  monthlyTradeOperations,
  stockAlertSamples,
  trendingItems,
  type StockAlertItem,
} from './dashboardSampleData'

const { Title, Text } = Typography

const renderLegendText = (value: ReactNode) => <span className="text-neutral-600 text-xs">{value}</span>

function getKpiFilterLabel(cardKey: KpiCardKey): string {
  switch (cardKey) {
    case 'receivables':
      return 'Công nợ phải thu'
    case 'payables':
      return 'Công nợ phải trả'
    case 'inventoryValuation':
      return 'Giá trị kho'
    case 'monthlyRevenue':
      return 'Doanh số tháng'
    case 'availableStock':
      return 'Tồn kho khả dụng'
    case 'pendingSalesOrders':
      return 'Đơn bán (SO)'
    case 'pendingPurchaseOrders':
      return 'Đơn mua (PO)'
    case 'warehouseUtilization':
      return 'Tỷ lệ lấp đầy'
    default:
      return ''
  }
}

export default function DashboardPage() {
  const { message } = App.useApp()
  const currentUser = getStoredUser()
  const roles = currentUser?.roles || []

  // Phân quyền theo SRS Mục 2.5.1 & 2.5.2
  const isAdmin = roles.includes('ADMIN')
  const isAccountant = roles.includes('ACCOUNTANT')
  const isWarehouseManager = roles.includes('WAREHOUSE_MANAGER')
  const isWarehouseStaff = roles.includes('WAREHOUSE_STAFF') && !isAdmin && !isAccountant && !isWarehouseManager

  // Phân quyền nghiệp vụ
  const isGlobalScope = isAdmin || isAccountant
  const canViewFinancials = isAdmin || isAccountant
  const canExportExcel = isAdmin || isAccountant

  // State bộ lọc Kho và Thời gian
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | 'all'>('all')
  const [timeRange, setTimeRange] = useState<string>('month')
  const [loadingWarehouses, setLoadingWarehouses] = useState<boolean>(true)

  // State dữ liệu 8 thẻ KPI thời gian thực & tương tác Click-to-Filter (WMS-16.3)
  const [kpiData, setKpiData] = useState<KpiSummaryResponse | null>(null)
  const [loadingKpi, setLoadingKpi] = useState<boolean>(true)
  const [activeKpiCard, setActiveKpiCard] = useState<KpiCardKey | null>(null)

  // 1. Tải danh mục Kho từ backend
  useEffect(() => {
    let mounted = true
    async function load() {
      try {
        const list = await fetchWarehouses({ active: true })
        if (!mounted) return
        setWarehouses(list)

        // Phân quyền dữ liệu theo phạm vi kho (Warehouse Scope - SRS 2.5.2):
        if (!isGlobalScope) {
          const userWarehouseIds = currentUser?.warehouseIds || []
          if (userWarehouseIds.length > 0) {
            setSelectedWarehouseId(userWarehouseIds[0])
          } else if (list.length > 0) {
            setSelectedWarehouseId(list[0].id)
          }
        }
      } catch {
        // Fallback danh mục kho mặc định
      } finally {
        if (mounted) setLoadingWarehouses(false)
      }
    }
    void load()
    return () => {
      mounted = false
    }
  }, [isGlobalScope, currentUser?.warehouseIds])

  // 2. Tải số liệu 8 thẻ KPI điều hành thời gian thực từ backend (WMS-16.1 / WMS-16.3)
  useEffect(() => {
    let mounted = true
    if (!isAdmin && !isAccountant && !isWarehouseManager) {
      setLoadingKpi(false)
      return
    }

    async function loadKpi() {
      setLoadingKpi(true)
      try {
        const data = await fetchDashboardKpiSummary(selectedWarehouseId, timeRange)
        if (mounted) {
          setKpiData(data)
        }
      } catch (err) {
        console.warn('Không tải được số liệu KPI:', err)
      } finally {
        if (mounted) {
          setLoadingKpi(false)
        }
      }
    }

    void loadKpi()
    return () => {
      mounted = false
    }
  }, [selectedWarehouseId, timeRange, isAdmin, isAccountant, isWarehouseManager])

  // Lọc danh sách kho mà người dùng có quyền chọn
  const allowedWarehouses = useMemo(() => {
    if (isGlobalScope) return warehouses
    const userWhIds = currentUser?.warehouseIds || []
    if (userWhIds.length === 0) return warehouses.slice(0, 1)
    return warehouses.filter((w) => userWhIds.includes(w.id))
  }, [warehouses, isGlobalScope, currentUser?.warehouseIds])

  // Lọc danh sách cảnh báo theo kho và theo thẻ KPI đang được chọn (Click to Filter)
  const filteredAlerts = useMemo(() => {
    let list = stockAlertSamples
    if (selectedWarehouseId !== 'all') {
      list = list.filter((a) => a.warehouseId === selectedWarehouseId)
    }

    if (!activeKpiCard) return list

    switch (activeKpiCard) {
      case 'availableStock':
        return list.filter((a) => a.code === 'WARN-01' || a.code === 'WARN-02')
      case 'pendingPurchaseOrders':
        return list.filter((a) => a.code === 'WARN-02' || a.code === 'WARN-01')
      case 'pendingSalesOrders':
        return list.filter((a) => a.code === 'WARN-03')
      case 'warehouseUtilization':
        return list.filter((a) => a.code === 'ERR-04')
      default:
        return list
    }
  }, [selectedWarehouseId, activeKpiCard])

  const handleToggleCardFilter = (cardKey: KpiCardKey) => {
    setActiveKpiCard((prev) => (prev === cardKey ? null : cardKey))
  }

  // Xuất file Excel báo cáo tổng quan
  const handleExportExcel = () => {
    if (!canExportExcel) {
      message.warning('Chỉ Quản trị viên và Kế toán mới có quyền xuất báo cáo Excel.')
      return
    }

    const dataToExport = [
      {
        'Chỉ số / Danh mục': 'Công nợ phải thu',
        'Giá trị': '248,500,000 đ',
        'Phạm vi': selectedWarehouseId === 'all' ? 'Toàn hệ thống' : `Kho ID #${selectedWarehouseId}`,
        'Ghi chú': '12 khách hàng còn dư nợ đến hạn',
      },
      {
        'Chỉ số / Danh mục': 'Công nợ phải trả',
        'Giá trị': '132,800,000 đ',
        'Phạm vi': selectedWarehouseId === 'all' ? 'Toàn hệ thống' : `Kho ID #${selectedWarehouseId}`,
        'Ghi chú': '4 nhà cung cấp đến hạn trong tuần',
      },
      {
        'Chỉ số / Danh mục': 'Giá trị tồn kho',
        'Giá trị': '1,420,000,000 đ',
        'Phạm vi': selectedWarehouseId === 'all' ? 'Toàn hệ thống' : `Kho ID #${selectedWarehouseId}`,
        'Ghi chú': 'Giá vốn bình quân lưu kho',
      },
      {
        'Chỉ số / Danh mục': 'Doanh số bán tháng',
        'Giá trị': '655,000,000 đ',
        'Phạm vi': selectedWarehouseId === 'all' ? 'Toàn hệ thống' : `Kho ID #${selectedWarehouseId}`,
        'Ghi chú': 'Tổng giá trị hóa đơn xuất',
      },
    ]

    const worksheet = XLSX.utils.json_to_sheet(dataToExport)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'KPI_Dashboard')
    const fileName = `WMS_Bao_Cao_KPI_${new Date().toISOString().slice(0, 10)}.xlsx`
    XLSX.writeFile(workbook, fileName)
    message.success(`Đã xuất báo cáo KPI: ${fileName}`)
  }

  // Cột cho bảng cảnh báo kho
  const alertColumns: ColumnsType<StockAlertItem> = [
    {
      title: 'Mã cảnh báo',
      dataIndex: 'code',
      key: 'code',
      width: 110,
      render: (code: string) => {
        const color = code === 'WARN-02' ? 'error' : code === 'WARN-01' ? 'warning' : code === 'WARN-03' ? 'gold' : 'processing'
        return <Tag color={color} className="text-xs">{code}</Tag>
      },
    },
    {
      title: 'Hàng hóa / Vị trí',
      key: 'item',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-gray-900 text-xs">{r.productName}</div>
          <div className="text-[11px] text-gray-500">
            {r.sku} • {r.locationCode || 'Chưa định vị'}
          </div>
        </div>
      ),
    },
    {
      title: 'Kho bãi',
      dataIndex: 'warehouseName',
      key: 'warehouseName',
      render: (wh: string) => <span className="text-xs text-gray-600">{wh}</span>,
    },
    {
      title: 'Nội dung cảnh báo & Đề xuất xử lý',
      key: 'action',
      render: (_, r) => (
        <div>
          <div className="text-xs text-gray-800">{r.message}</div>
          <div className="text-[11px] text-amber-700 font-medium mt-0.5 flex items-center gap-1">
            <InfoCircleOutlined /> {r.actionNote}
          </div>
        </div>
      ),
    },
  ]

  const totalTrendingQuantity = trendingItems.reduce((sum, item) => sum + item.value, 0)

  return (
    <div className="flex flex-col gap-5 w-full">
      {/* 1. Header Toolbar & Phân quyền thông minh */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-4 rounded-lg border border-slate-200/80 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <Title level={4} className="!mb-0 font-semibold text-slate-900">
              Bảng điều khiển & Thống kê KPI
            </Title>
            <Tag color="blue" className="text-xs font-normal">
              {isAdmin ? 'Quản trị hệ thống' : isAccountant ? 'Kế toán' : isWarehouseManager ? 'Quản lý kho' : 'Nhân viên kho'}
            </Tag>
          </div>
          <Text type="secondary" className="text-xs">
            Theo dõi sức chứa, lưu chuyển hàng hóa và cảnh báo vận hành theo thời gian thực
          </Text>
        </div>

        {/* Bộ lọc Kho & Thời gian & Nút Xuất Excel */}
        <Space wrap size="middle">
          {/* Bộ lọc Nhà kho theo phạm vi phân quyền (SRS 2.5.2) */}
          <div className="flex items-center gap-1.5">
            <ShopOutlined className="text-gray-400 text-xs" />
            <Text className="text-xs text-gray-500">Kho:</Text>
            <Select
              size="middle"
              className="w-52"
              value={selectedWarehouseId}
              loading={loadingWarehouses}
              onChange={(val) => setSelectedWarehouseId(val)}
              options={[
                ...(isGlobalScope ? [{ label: 'Tất cả kho bãi', value: 'all' as const }] : []),
                ...allowedWarehouses.map((w) => ({
                  label: `${w.name} (${w.code})`,
                  value: w.id,
                })),
              ]}
            />
          </div>

          {/* Bộ lọc Thời gian */}
          <div className="flex items-center gap-1.5">
            <CalendarOutlined className="text-gray-400 text-xs" />
            <Select
              size="middle"
              className="w-32"
              value={timeRange}
              onChange={(val) => setTimeRange(val)}
              options={[
                { label: 'Hôm nay', value: 'today' },
                { label: '7 ngày qua', value: '7d' },
                { label: 'Tháng này', value: 'month' },
                { label: 'Quý này', value: 'quarter' },
              ]}
            />
          </div>

          {/* Nút Xuất Excel */}
          {canExportExcel ? (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={handleExportExcel}
              className="bg-emerald-600 hover:!bg-emerald-500 text-xs font-medium"
            >
              Xuất Excel
            </Button>
          ) : (
            <Tooltip title="Chỉ Quản trị viên và Kế toán mới có quyền xuất báo cáo Excel.">
              <Button icon={<LockOutlined />} disabled className="text-xs">
                Xuất Excel
              </Button>
            </Tooltip>
          )}
        </Space>
      </div>

      {/* 2. Thông báo phạm vi chi nhánh phụ trách */}
      {isWarehouseStaff && (
        <Alert
          type="info"
          showIcon
          icon={<WarningOutlined className="text-blue-600" />}
          message="Chế độ vận hành kho chi nhánh"
          description="Dữ liệu hiển thị các chỉ số xuất nhập và cảnh báo vật lý phù hợp với kho bạn phụ trách."
          className="text-xs border-blue-200/80 bg-blue-50/40 rounded-lg"
        />
      )}

      {!isGlobalScope && !isWarehouseStaff && (
        <Alert
          type="warning"
          showIcon
          message="Phạm vi chi nhánh phụ trách"
          description="Dữ liệu và số liệu thống kê được giới hạn theo các kho bạn được phân công quản lý."
          className="text-xs border-amber-200/80 bg-amber-50/40 rounded-lg"
        />
      )}

      {/* 3. Thẻ Chỉ số KPI Điều hành (WMS-16.1 & WMS-16.3) */}
      <Row gutter={[16, 16]}>
        {/* Nhóm thẻ tài chính (Chỉ hiện cho Admin & Accountant) */}
        {canViewFinancials && (
          <>
            {/* 1. Công nợ phải thu */}
            <Col xs={24} sm={12} xl={6}>
              <Card
                variant="borderless"
                loading={loadingKpi}
                onClick={() => handleToggleCardFilter('receivables')}
                className={`transition-all duration-150 cursor-pointer h-full border ${
                  activeKpiCard === 'receivables'
                    ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                    : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/30'
                }`}
              >
                <Statistic
                  title={<span className="text-xs font-medium text-slate-500">Công nợ phải thu</span>}
                  value={kpiData?.receivables?.amount ?? 248500000}
                  formatter={(val) =>
                    (kpiData?.receivables?.visible ?? true)
                      ? formatCurrency(Number(val))
                      : '*** đ'
                  }
                  valueStyle={{ fontSize: '1.35rem', fontWeight: 600, color: '#0f172a' }}
                />
                <span className="text-xs text-slate-400 mt-1 block truncate">
                  {kpiData?.receivables?.subText || '12 khách hàng còn dư nợ đến hạn'}
                </span>
              </Card>
            </Col>

            {/* 2. Công nợ phải trả */}
            <Col xs={24} sm={12} xl={6}>
              <Card
                variant="borderless"
                loading={loadingKpi}
                onClick={() => handleToggleCardFilter('payables')}
                className={`transition-all duration-150 cursor-pointer h-full border ${
                  activeKpiCard === 'payables'
                    ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                    : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/30'
                }`}
              >
                <Statistic
                  title={<span className="text-xs font-medium text-slate-500">Công nợ phải trả</span>}
                  value={kpiData?.payables?.amount ?? 132800000}
                  formatter={(val) =>
                    (kpiData?.payables?.visible ?? true)
                      ? formatCurrency(Number(val))
                      : '*** đ'
                  }
                  valueStyle={{ fontSize: '1.35rem', fontWeight: 600, color: '#0f172a' }}
                />
                <span className="text-xs text-slate-400 mt-1 block truncate">
                  {kpiData?.payables?.subText || '4 nhà cung cấp đến hạn trong tuần'}
                </span>
              </Card>
            </Col>

            {/* 3. Giá trị kho hàng */}
            <Col xs={24} sm={12} xl={6}>
              <Card
                variant="borderless"
                loading={loadingKpi}
                onClick={() => handleToggleCardFilter('inventoryValuation')}
                className={`transition-all duration-150 cursor-pointer h-full border ${
                  activeKpiCard === 'inventoryValuation'
                    ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                    : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/30'
                }`}
              >
                <Statistic
                  title={<span className="text-xs font-medium text-slate-500">Giá trị kho hàng</span>}
                  value={kpiData?.inventoryValuation?.amount ?? 1420000000}
                  formatter={(val) =>
                    (kpiData?.inventoryValuation?.visible ?? true)
                      ? formatCurrency(Number(val))
                      : '*** đ'
                  }
                  valueStyle={{ fontSize: '1.35rem', fontWeight: 600, color: '#0f172a' }}
                />
                <span className="text-xs text-slate-400 mt-1 block truncate">
                  {kpiData?.inventoryValuation?.subText || 'Tính theo giá vốn bình quân'}
                </span>
              </Card>
            </Col>

            {/* 4. Doanh số xuất bán tháng */}
            <Col xs={24} sm={12} xl={6}>
              <Card
                variant="borderless"
                loading={loadingKpi}
                onClick={() => handleToggleCardFilter('monthlyRevenue')}
                className={`transition-all duration-150 cursor-pointer h-full border ${
                  activeKpiCard === 'monthlyRevenue'
                    ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                    : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/30'
                }`}
              >
                <Statistic
                  title={<span className="text-xs font-medium text-slate-500">Doanh số xuất bán tháng</span>}
                  value={kpiData?.monthlyRevenue?.amount ?? 655000000}
                  formatter={(val) =>
                    (kpiData?.monthlyRevenue?.visible ?? true)
                      ? formatCurrency(Number(val))
                      : '*** đ'
                  }
                  valueStyle={{ fontSize: '1.35rem', fontWeight: 600, color: '#0f172a' }}
                />
                <span className="text-xs text-slate-400 mt-1 block truncate">
                  {kpiData?.monthlyRevenue?.subText || 'Tăng 11.2% so với tháng trước'}
                </span>
              </Card>
            </Col>
          </>
        )}

        {/* 5. Tồn kho khả dụng */}
        <Col xs={24} sm={12} xl={canViewFinancials ? 6 : 6}>
          <Card
            variant="borderless"
            loading={loadingKpi}
            onClick={() => handleToggleCardFilter('availableStock')}
            className={`transition-all duration-150 cursor-pointer h-full border ${
              activeKpiCard === 'availableStock'
                ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/30'
            }`}
          >
            <Statistic
              title={<span className="text-xs font-medium text-slate-500">Tồn kho khả dụng</span>}
              value={kpiData?.availableStock?.totalQuantity ?? 24850}
              formatter={(val) => formatNumber(Number(val))}
              valueStyle={{ fontSize: '1.35rem', fontWeight: 600, color: '#0f172a' }}
            />
            <span className="text-xs text-slate-400 mt-1 block truncate">
              {kpiData?.availableStock?.subText || 'Sản phẩm lưu tại các vị trí ô/kệ'}
            </span>
          </Card>
        </Col>

        {/* 6. Đơn bán chờ xuất (SO) */}
        <Col xs={24} sm={12} xl={canViewFinancials ? 6 : 6}>
          <Card
            variant="borderless"
            loading={loadingKpi}
            onClick={() => handleToggleCardFilter('pendingSalesOrders')}
            className={`transition-all duration-150 cursor-pointer h-full border ${
              activeKpiCard === 'pendingSalesOrders'
                ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/30'
            }`}
          >
            <Statistic
              title={<span className="text-xs font-medium text-slate-500">Đơn bán chờ xuất kho (SO)</span>}
              value={kpiData?.pendingSalesOrders?.totalOrders ?? 18}
              formatter={(val) => formatNumber(Number(val))}
              valueStyle={{ fontSize: '1.35rem', fontWeight: 600, color: '#0f172a' }}
            />
            <span className="text-xs text-slate-400 mt-1 block truncate">
              {kpiData?.pendingSalesOrders?.subText || '5 đơn cần điều phối lấy hàng gấp'}
            </span>
          </Card>
        </Col>

        {/* 7. Đơn mua chờ tiếp nhận (PO) */}
        <Col xs={24} sm={12} xl={canViewFinancials ? 6 : 6}>
          <Card
            variant="borderless"
            loading={loadingKpi}
            onClick={() => handleToggleCardFilter('pendingPurchaseOrders')}
            className={`transition-all duration-150 cursor-pointer h-full border ${
              activeKpiCard === 'pendingPurchaseOrders'
                ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/30'
            }`}
          >
            <Statistic
              title={<span className="text-xs font-medium text-slate-500">Đơn mua chờ tiếp nhận (PO)</span>}
              value={kpiData?.pendingPurchaseOrders?.totalOrders ?? 7}
              formatter={(val) => formatNumber(Number(val))}
              valueStyle={{ fontSize: '1.35rem', fontWeight: 600, color: '#0f172a' }}
            />
            <span className="text-xs text-slate-400 mt-1 block truncate">
              {kpiData?.pendingPurchaseOrders?.subText || '3 lô hàng dự kiến đến trong hôm nay'}
            </span>
          </Card>
        </Col>

        {/* 8. Tỷ lệ lấp đầy kho */}
        <Col xs={24} sm={12} xl={canViewFinancials ? 6 : 6}>
          <Card
            variant="borderless"
            loading={loadingKpi}
            onClick={() => handleToggleCardFilter('warehouseUtilization')}
            className={`transition-all duration-150 cursor-pointer h-full border ${
              activeKpiCard === 'warehouseUtilization'
                ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/30'
            }`}
          >
            <Statistic
              title={<span className="text-xs font-medium text-slate-500">Tỷ lệ lấp đầy kho</span>}
              value={kpiData?.warehouseUtilization?.occupancyRatePercentage ?? 78.5}
              formatter={(val) => `${Number(val)}%`}
              valueStyle={{ fontSize: '1.35rem', fontWeight: 600, color: '#0f172a' }}
            />
            <span className="text-xs text-slate-400 mt-1 block truncate">
              {kpiData?.warehouseUtilization?.subText || '184 / 234 vị trí ô kệ đang sử dụng'}
            </span>
          </Card>
        </Col>
      </Row>

      {/* 4. Trung tâm Cảnh báo kho */}
      <Card
        variant="borderless"
        className="shadow-none border border-slate-200/80 bg-white"
        title={
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <AlertOutlined className="text-amber-500 text-sm" />
              <span className="font-semibold text-slate-900 text-sm">
                Cảnh báo Tồn kho & Hạn dùng
              </span>
              <span className="text-[11px] font-medium text-rose-700 bg-rose-50 border border-rose-200/70 rounded-full px-2 py-0.5">
                {filteredAlerts.length} cần lưu ý
              </span>
            </div>
            {activeKpiCard && (
              <div className="flex items-center gap-2">
                <Tag
                  closable
                  onClose={() => setActiveKpiCard(null)}
                  className="text-xs text-slate-600 bg-slate-100 border-slate-300"
                >
                  Lọc theo: {getKpiFilterLabel(activeKpiCard)}
                </Tag>
                <Button
                  type="link"
                  size="small"
                  onClick={() => setActiveKpiCard(null)}
                  className="text-xs !p-0 text-slate-500 hover:text-slate-800"
                >
                  Xem tất cả
                </Button>
              </div>
            )}
          </div>
        }
      >
        <Table<StockAlertItem>
          columns={alertColumns}
          dataSource={filteredAlerts}
          rowKey="id"
          pagination={false}
          size="middle"
          locale={{ emptyText: <Empty description="Hiện không có cảnh báo tồn kho bất thường" /> }}
        />
      </Card>

      {/* 5. Biểu đồ Phân tích (Cột đôi & Tròn) */}
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card
            variant="borderless"
            className="shadow-none border border-slate-200/80 bg-white"
            title={
              <span className="font-semibold text-slate-900 text-sm">
                {canViewFinancials
                  ? 'Giá trị Mua vào và Bán ra theo tháng (triệu đồng)'
                  : 'Sản lượng Nhập kho & Xuất kho theo tháng (sản phẩm)'}
              </span>
            }
          >
            <ResponsiveContainer width="100%" height={300}>
              <BarChart
                data={canViewFinancials ? monthlyTradeFinancial : monthlyTradeOperations}
                margin={{ top: 10, right: 10, left: 0, bottom: 0 }}
              >
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="month" stroke="#64748b" fontSize={12} tickLine={false} />
                <YAxis stroke="#64748b" fontSize={12} tickLine={false} />
                <RechartsTooltip
                  formatter={(value) =>
                    canViewFinancials
                      ? `${formatNumber(Number(value))} triệu đồng`
                      : `${formatNumber(Number(value))} sản phẩm`
                  }
                />
                <Legend itemSorter="dataKey" formatter={renderLegendText} />
                <Bar
                  dataKey="purchase"
                  name={canViewFinancials ? 'Mua vào (Tiền)' : 'Nhập kho (SL)'}
                  fill="#0284c7"
                  radius={[3, 3, 0, 0]}
                />
                <Bar
                  dataKey="sales"
                  name={canViewFinancials ? 'Bán ra (Tiền)' : 'Xuất kho (SL)'}
                  fill="#10b981"
                  radius={[3, 3, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        <Col xs={24} xl={10}>
          <Card
            variant="borderless"
            className="shadow-none border border-slate-200/80 bg-white"
            title={
              <span className="font-semibold text-slate-900 text-sm">
                Mặt hàng xuất bán chạy nhất
              </span>
            }
          >
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={trendingItems}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={65}
                  outerRadius={100}
                  paddingAngle={2}
                >
                  <Label
                    value={`${formatNumber(totalTrendingQuantity)} sp`}
                    position="center"
                    fill="#0f172a"
                    fontSize={14}
                    fontWeight={600}
                  />
                </Pie>
                <RechartsTooltip formatter={(value) => `${formatNumber(Number(value))} sản phẩm`} />
                <Legend verticalAlign="bottom" itemSorter={null} formatter={renderLegendText} />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
