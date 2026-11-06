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
import {
  financialKpiSamples,
  monthlyTradeFinancial,
  monthlyTradeOperations,
  operationsKpiSamples,
  stockAlertSamples,
  trendingItems,
  type StockAlertItem,
} from './dashboardSampleData'

const { Title, Text } = Typography

const renderLegendText = (value: ReactNode) => <span className="text-neutral-600 text-xs">{value}</span>

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

  // Lọc danh sách kho mà người dùng có quyền chọn
  const allowedWarehouses = useMemo(() => {
    if (isGlobalScope) return warehouses
    const userWhIds = currentUser?.warehouseIds || []
    if (userWhIds.length === 0) return warehouses.slice(0, 1)
    return warehouses.filter((w) => userWhIds.includes(w.id))
  }, [warehouses, isGlobalScope, currentUser?.warehouseIds])

  // Lọc danh sách cảnh báo theo kho được chọn
  const filteredAlerts = useMemo(() => {
    if (selectedWarehouseId === 'all') return stockAlertSamples
    return stockAlertSamples.filter((a) => a.warehouseId === selectedWarehouseId)
  }, [selectedWarehouseId])

  // Xuất file Excel báo cáo tổng quan (Dành cho Quản trị viên & Kế toán - SRS 2.5.1)
  const handleExportExcel = () => {
    if (!canExportExcel) {
      message.warning('Theo Mục 2.5.1 SRS, chỉ Quản trị viên và Kế toán có quyền xuất báo cáo Excel.')
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
    message.success(`Đã xuất báo cáo KPI thành công: ${fileName}`)
  }

  // Cột cho bảng cảnh báo kho thông minh (WARN-01, WARN-02, WARN-03, ERR-04)
  const alertColumns: ColumnsType<StockAlertItem> = [
    {
      title: 'Mã cảnh báo',
      dataIndex: 'code',
      key: 'code',
      width: 110,
      render: (code: string) => {
        const color = code === 'WARN-02' ? 'error' : code === 'WARN-01' ? 'warning' : code === 'WARN-03' ? 'gold' : 'processing'
        return <Tag color={color} className="font-mono text-xs">{code}</Tag>
      },
    },
    {
      title: 'Hàng hóa / Vị trí',
      key: 'item',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-gray-900 text-xs">{r.productName}</div>
          <div className="text-[11px] text-gray-500 font-mono">
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
            <Title level={4} className="!mb-0 font-bold text-gray-900">
              Bảng điều khiển & Thống kê KPI
            </Title>
            <Tag color="blue" className="text-xs font-normal">
              {isAdmin ? 'Quản trị viên (Toàn quyền)' : isAccountant ? 'Kế toán (Tài chính & Excel)' : isWarehouseManager ? 'Quản lý kho' : 'Nhân viên kho (Xem cảnh báo)'}
            </Tag>
          </div>
          <Text type="secondary" className="text-xs">
            Theo dõi sức chứa, dòng hàng hóa và phát hiện cảnh báo rủi ro chuỗi cung ứng theo thời gian thực
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
                ...(isGlobalScope ? [{ label: '🏢 Tất cả các kho (Toàn cục)', value: 'all' as const }] : []),
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

          {/* Nút Xuất Excel (Mục 2.5.1: Chỉ Admin & Accountant) */}
          {canExportExcel ? (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={handleExportExcel}
              className="bg-emerald-600 hover:!bg-emerald-500 text-xs font-medium"
            >
              Xuất Excel KPI
            </Button>
          ) : (
            <Tooltip title="Theo Mục 2.5.1 SRS: Chỉ Quản trị viên và Kế toán mới có quyền Xuất báo cáo Excel.">
              <Button icon={<LockOutlined />} disabled className="text-xs">
                Xuất Excel
              </Button>
            </Tooltip>
          )}
        </Space>
      </div>

      {/* 2. Banner Thông báo Phân quyền theo vai trò (SRS 2.5.1 & 2.5.2) */}
      {isWarehouseStaff && (
        <Alert
          type="info"
          showIcon
          icon={<WarningOutlined className="text-blue-600" />}
          message="Phân quyền Nhân viên kho (Warehouse Scope)"
          description="Theo Mục 2.5.1 & 2.5.2 SRS, vai trò Nhân viên kho tập trung vào nghiệp vụ vật lý và xem cảnh báo. Các thông tin tài chính nhạy cảm (doanh thu, lợi nhuận, công nợ) được tự động ẩn để bảo mật."
          className="text-xs border-blue-200 bg-blue-50/50"
        />
      )}

      {!isGlobalScope && !isWarehouseStaff && (
        <Alert
          type="warning"
          showIcon
          message="Phân quyền Cấp Chi nhánh (Warehouse Scope)"
          description="Dữ liệu và số liệu thống kê được tự động giới hạn theo các kho bạn được phân công phụ trách (Mục 2.5.2 SRS)."
          className="text-xs border-amber-200 bg-amber-50/50"
        />
      )}

      {/* 3. Thẻ Chỉ số KPI phân cấp theo vai trò */}
      <Row gutter={[16, 16]}>
        {/* Nhóm thẻ tài chính (Chỉ hiện cho Admin & Accountant) */}
        {canViewFinancials &&
          financialKpiSamples.map((kpi) => (
            <Col key={kpi.key} xs={24} sm={12} xl={6}>
              <Card variant="borderless" className="shadow-xs border border-slate-200/80 hover:border-blue-400 transition-colors">
                <Statistic
                  title={<span className="text-xs font-medium text-gray-500">{kpi.title}</span>}
                  value={kpi.value}
                  formatter={() => formatCurrency(kpi.value)}
                  valueStyle={{ fontSize: '1.4rem', fontWeight: 700, color: '#0f172a' }}
                />
                <Text type="secondary" className="text-xs mt-1 block">
                  {kpi.note}
                </Text>
              </Card>
            </Col>
          ))}

        {/* Nhóm thẻ vận hành vật lý (Hiện cho Quản lý kho, Nhân viên kho & Toàn hệ thống) */}
        {operationsKpiSamples.map((kpi) => (
          <Col key={kpi.key} xs={24} sm={12} xl={canViewFinancials ? 6 : 6}>
            <Card variant="borderless" className="shadow-xs border border-slate-200/80 hover:border-blue-400 transition-colors">
              <Statistic
                title={<span className="text-xs font-medium text-gray-500">{kpi.title}</span>}
                value={kpi.value}
                formatter={() =>
                  kpi.kind === 'percent' ? `${kpi.value}%` : formatNumber(kpi.value)
                }
                valueStyle={{ fontSize: '1.4rem', fontWeight: 700, color: '#0f172a' }}
              />
              <Text type="secondary" className="text-xs mt-1 block">
                {kpi.note}
              </Text>
            </Card>
          </Col>
        ))}
      </Row>

      {/* 4. Trung tâm Cảnh báo kho (WARN-01, WARN-02, WARN-03, ERR-04 - Mục 4.2 SRS) */}
      <Card
        variant="borderless"
        className="shadow-xs border border-slate-200/80"
        title={
          <div className="flex items-center gap-2">
            <AlertOutlined className="text-amber-500" />
            <span className="font-bold text-gray-900 text-sm">
              Trung tâm Cảnh báo Tồn kho & Hạn dùng (Mục 4.2 SRS)
            </span>
            <Tag color="red" className="text-xs rounded-full px-2">
              {filteredAlerts.length} cảnh báo cần xử lý
            </Tag>
          </div>
        }
      >
        <Table<StockAlertItem>
          columns={alertColumns}
          dataSource={filteredAlerts}
          rowKey="id"
          pagination={false}
          size="small"
          locale={{ emptyText: <Empty description="Hiện không có cảnh báo tồn kho bất thường" /> }}
        />
      </Card>

      {/* 5. Biểu đồ Phân tích (Phân quyền: Tài chính vs Vận hành vật lý) */}
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card
            variant="borderless"
            className="shadow-xs border border-slate-200/80"
            title={
              <span className="font-bold text-gray-900 text-sm">
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
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                <XAxis dataKey="month" stroke="#64748b" fontSize={12} />
                <YAxis stroke="#64748b" fontSize={12} />
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
                  radius={[4, 4, 0, 0]}
                />
                <Bar
                  dataKey="sales"
                  name={canViewFinancials ? 'Bán ra (Tiền)' : 'Xuất kho (SL)'}
                  fill="#10b981"
                  radius={[4, 4, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        <Col xs={24} xl={10}>
          <Card
            variant="borderless"
            className="shadow-xs border border-slate-200/80"
            title={
              <span className="font-bold text-gray-900 text-sm">
                Trending Items - Mặt hàng xuất bán chạy
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
                    fill="#1e293b"
                    fontSize={14}
                    fontWeight={700}
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
