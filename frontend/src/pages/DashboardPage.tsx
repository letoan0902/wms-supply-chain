import {
  AlertOutlined,
  CalendarOutlined,
  DownloadOutlined,
  LockOutlined,
  ReloadOutlined,
  ShopOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Card, Col, Empty, Row, Select, Space, Table, Tag, Tooltip, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Label, Legend, Pie, PieChart, ResponsiveContainer, Tooltip as RechartsTooltip } from 'recharts'
import * as XLSX from 'xlsx'
import AiSummaryCard from '../features/ai/components/AiSummaryCard'
import { fetchDashboardKpiSummary, fetchTrendingItems } from '../features/dashboard/dashboardApi'
import type { DashboardTimeRange, KpiCardKey, KpiSummaryResponse } from '../features/dashboard/types'
import { useDashboardRequest } from '../features/dashboard/useDashboardRequest'
import { fetchAlerts } from '../features/inventory/inventoryApi'
import { alertTagLabel, KIND_LABELS, SEVERITY_COLORS } from '../features/inventory/inventoryUtils'
import type { StockAlert, StockAlertKind } from '../features/inventory/types'
import { ROLE_CONFIG } from '../features/users/types'
import { fetchWarehouses } from '../features/warehouses/warehouseApi'
import type { Warehouse } from '../features/warehouses/types'
import { formatCurrency, formatNumber } from '../utils/format'
import { getStoredUser } from '../utils/storage'

const { Title, Text } = Typography

/** Số cảnh báo hiển thị trên dashboard; danh sách đầy đủ ở màn hình Tồn kho. */
const ALERT_PAGE_SIZE = 8

const TIME_RANGE_OPTIONS: { label: string; value: DashboardTimeRange }[] = [
  { label: 'Hôm nay', value: 'today' },
  { label: '7 ngày qua', value: '7d' },
  { label: 'Tháng này', value: 'month' },
  { label: 'Quý này', value: 'quarter' },
]

const renderLegendText = (value: ReactNode) => <span className="text-neutral-600 text-xs">{value}</span>

/** Một thẻ KPI đã định dạng để hiển thị. */
interface KpiCardView {
  key: KpiCardKey
  title: string
  value: string
  subText: string
  /** Loại cảnh báo tồn kho mà thẻ lọc khi bấm (Click to Filter); không có thì thẻ không bấm được */
  alertKind?: StockAlertKind
}

/** Chưa tính được (backend trả null) thì hiện gạch ngang, lời giải thích nằm ở subText. */
const NOT_AVAILABLE = '—'

function money(amount: number | null | undefined): string {
  return amount === null || amount === undefined ? NOT_AVAILABLE : formatCurrency(Number(amount))
}

function quantity(value: number | null | undefined): string {
  return value === null || value === undefined ? NOT_AVAILABLE : formatNumber(Number(value))
}

/**
 * Lắp 8 thẻ theo thứ tự của SRS từ dữ liệu backend, bỏ các thẻ vai trò hiện tại không được xem (`visible = false`).
 * Không có số mặc định: số nào backend không tính được thì hiện gạch ngang kèm lời giải thích của backend.
 */
function buildKpiCards(data: KpiSummaryResponse): KpiCardView[] {
  const cards: (KpiCardView & { visible: boolean })[] = [
    {
      key: 'receivables',
      title: data.receivables.title,
      value: money(data.receivables.amount),
      subText: data.receivables.subText ?? '',
      visible: data.receivables.visible,
    },
    {
      key: 'payables',
      title: data.payables.title,
      value: money(data.payables.amount),
      subText: data.payables.subText ?? '',
      visible: data.payables.visible,
    },
    {
      key: 'inventoryValuation',
      title: data.inventoryValuation.title,
      value: money(data.inventoryValuation.amount),
      subText: data.inventoryValuation.subText ?? '',
      visible: data.inventoryValuation.visible,
      alertKind: 'EXPIRED',
    },
    {
      key: 'monthlyRevenue',
      title: data.monthlyRevenue.title,
      value: money(data.monthlyRevenue.amount),
      // Tăng trưởng null (tháng trước không có doanh số): backend đã ghi lý do trong subText
      subText: data.monthlyRevenue.subText ?? '',
      visible: data.monthlyRevenue.visible,
    },
    {
      key: 'availableStock',
      title: data.availableStock.title,
      value: quantity(data.availableStock.totalQuantity),
      subText: data.availableStock.subText ?? '',
      visible: data.availableStock.visible,
      alertKind: 'LOW_STOCK',
    },
    {
      key: 'pendingSalesOrders',
      title: data.pendingSalesOrders.title,
      value: quantity(data.pendingSalesOrders.totalOrders),
      subText: data.pendingSalesOrders.subText ?? '',
      visible: data.pendingSalesOrders.visible,
      alertKind: 'NEAR_EXPIRY',
    },
    {
      key: 'pendingPurchaseOrders',
      title: data.pendingPurchaseOrders.title,
      value: quantity(data.pendingPurchaseOrders.totalOrders),
      subText: data.pendingPurchaseOrders.subText ?? '',
      visible: data.pendingPurchaseOrders.visible,
      alertKind: 'LOW_STOCK',
    },
    {
      key: 'warehouseUtilization',
      title: data.warehouseUtilization.title,
      value:
        data.warehouseUtilization.occupancyRatePercentage === null ||
        data.warehouseUtilization.occupancyRatePercentage === undefined
          ? NOT_AVAILABLE
          : `${data.warehouseUtilization.occupancyRatePercentage}%`,
      subText: data.warehouseUtilization.subText ?? '',
      visible: data.warehouseUtilization.visible,
      alertKind: 'DEAD_STOCK',
    },
  ]
  return cards.filter((card) => card.visible)
}

export default function DashboardPage() {
  const { message } = App.useApp()
  // Đọc phiên một lần: getStoredUser() tạo mảng warehouseIds mới mỗi lần gọi, đưa vào dependency sẽ gọi lại API liên tục
  const [currentUser] = useState(() => getStoredUser())
  const roles = useMemo(() => currentUser?.roles ?? [], [currentUser])
  const userWarehouseIds = useMemo(() => currentUser?.warehouseIds ?? [], [currentUser])

  // Phân quyền theo SRS Mục 2.5.1 & 2.5.2 (backend vẫn là nơi quyết định: che số tiền, lọc theo kho)
  const isAdmin = roles.includes('ADMIN')
  const isAccountant = roles.includes('ACCOUNTANT')
  const isWarehouseManager = roles.includes('WAREHOUSE_MANAGER')
  const isWarehouseStaff = roles.includes('WAREHOUSE_STAFF') && !isAdmin && !isAccountant && !isWarehouseManager
  const isGlobalScope = isAdmin || isAccountant
  const canExportExcel = isAdmin || isAccountant
  const roleLabel = roles.map((r) => ROLE_CONFIG[r]?.label ?? r).join(', ')

  // State bộ lọc Kho và Thời gian
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | 'all'>('all')
  const [timeRange, setTimeRange] = useState<DashboardTimeRange>('month')
  const [loadingWarehouses, setLoadingWarehouses] = useState<boolean>(true)
  const [activeKpiCard, setActiveKpiCard] = useState<KpiCardKey | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  // 1. Tải danh mục Kho; vai trò theo kho mặc định chọn kho đầu tiên được gán
  useEffect(() => {
    let mounted = true
    fetchWarehouses({ active: true })
      .then((list) => {
        if (!mounted) return
        setWarehouses(list)
        if (!isGlobalScope && userWarehouseIds.length > 0) {
          setSelectedWarehouseId(userWarehouseIds[0])
        }
      })
      .catch(() => {
        // Không tải được danh mục kho: vẫn hiển thị số liệu theo phạm vi backend tự xác định
      })
      .finally(() => {
        if (mounted) setLoadingWarehouses(false)
      })
    return () => {
      mounted = false
    }
  }, [isGlobalScope, userWarehouseIds])

  // Danh sách kho người dùng được chọn: ADMIN, ACCOUNTANT mọi kho; vai trò khác chỉ kho được gán
  const allowedWarehouses = useMemo(() => {
    if (isGlobalScope) return warehouses
    return warehouses.filter((w) => userWarehouseIds.includes(w.id))
  }, [warehouses, isGlobalScope, userWarehouseIds])

  // Chỉ gọi số liệu khi đã biết kho mặc định, tránh gọi hai lần lúc mở trang
  const scopeKey = loadingWarehouses ? null : `${selectedWarehouseId}:${timeRange}:${reloadToken}`

  // 2. Tám thẻ KPI (WMS-16.1, GET /api/notifications/dashboard/kpi-summary)
  const loadKpi = useCallback(
    () => fetchDashboardKpiSummary(selectedWarehouseId, timeRange),
    [selectedWarehouseId, timeRange],
  )
  const kpi = useDashboardRequest(scopeKey, loadKpi, 'Không tải được số liệu thẻ KPI')
  const kpiCards = useMemo(() => (kpi.data ? buildKpiCards(kpi.data) : []), [kpi.data])

  // 3. Biểu đồ mặt hàng xuất bán chạy (WMS-16.2, GET /api/orders/reports/trending-items)
  const loadTrending = useCallback(
    () => fetchTrendingItems(selectedWarehouseId, timeRange),
    [selectedWarehouseId, timeRange],
  )
  const trending = useDashboardRequest(scopeKey, loadTrending, 'Không tải được thống kê mặt hàng bán chạy')

  // 4. Cảnh báo tồn kho thật (GET /api/inventory/alerts), lọc theo thẻ KPI đang chọn (Click to Filter)
  const activeCard = kpiCards.find((card) => card.key === activeKpiCard)
  const alertKind = activeCard?.alertKind
  const alertKey = loadingWarehouses ? null : `${selectedWarehouseId}:${alertKind ?? 'ALL'}:${reloadToken}`
  const loadAlerts = useCallback(
    () =>
      fetchAlerts({
        kind: alertKind,
        warehouseId: selectedWarehouseId === 'all' ? undefined : selectedWarehouseId,
        page: 0,
        size: ALERT_PAGE_SIZE,
      }),
    [alertKind, selectedWarehouseId],
  )
  const alerts = useDashboardRequest(alertKey, loadAlerts, 'Không tải được cảnh báo tồn kho')

  const handleToggleCardFilter = (card: KpiCardView) => {
    if (!card.alertKind) return
    setActiveKpiCard((prev) => (prev === card.key ? null : card.key))
  }

  const reloadAll = () => setReloadToken((token) => token + 1)

  // Xuất Excel số liệu đang hiển thị (thẻ KPI và mặt hàng bán chạy), không có số mẫu
  const handleExportExcel = () => {
    if (!canExportExcel) {
      message.warning('Chỉ Quản trị viên và Kế toán mới có quyền xuất báo cáo Excel.')
      return
    }
    if (!kpi.data) {
      message.warning('Chưa có số liệu KPI để xuất.')
      return
    }
    const scope =
      selectedWarehouseId === 'all'
        ? 'Toàn hệ thống'
        : (allowedWarehouses.find((w) => w.id === selectedWarehouseId)?.name ?? `Kho #${selectedWarehouseId}`)

    const kpiRows = kpiCards.map((card) => ({
      'Chỉ số': card.title,
      'Giá trị': card.value,
      'Phạm vi': scope,
      'Ghi chú': card.subText,
    }))
    const trendingRows = (trending.data?.slices ?? []).map((slice) => ({
      'Mã hàng': slice.productId === null ? '' : slice.sku,
      'Tên hàng': slice.name,
      'Số lượng': Number(slice.quantity),
      'Đơn vị': slice.uom,
      'Tỷ trọng (%)': slice.percentage,
    }))

    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(kpiRows), 'KPI')
    if (trendingRows.length > 0) {
      XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(trendingRows), 'Mat_hang_ban_chay')
    }
    const fileName = `WMS_Bao_Cao_KPI_${new Date().toISOString().slice(0, 10)}.xlsx`
    XLSX.writeFile(workbook, fileName)
    message.success(`Đã xuất báo cáo KPI: ${fileName}`)
  }

  const alertColumns: ColumnsType<StockAlert> = [
    {
      title: 'Loại',
      key: 'kind',
      width: 110,
      render: (_, r) => (
        <Tag color={SEVERITY_COLORS[r.severity]} className="text-xs m-0">
          {alertTagLabel(r.kind, r.severity)}
        </Tag>
      ),
    },
    {
      title: 'Hàng hóa / Vị trí',
      key: 'item',
      render: (_, r) => (
        <div className="min-w-0">
          <div className="font-semibold text-slate-900 text-xs">{r.productName ?? `Sản phẩm #${r.productId}`}</div>
          <div className="text-[11px] text-slate-500">
            {[r.skuCode, r.lotNo ? `Lô ${r.lotNo}` : null, r.binCode].filter(Boolean).join(' • ') || 'Chưa định vị'}
          </div>
        </div>
      ),
    },
    {
      title: 'Kho',
      key: 'warehouse',
      responsive: ['md'],
      render: (_, r) => (
        <span className="text-xs text-slate-600">{r.warehouseName ?? r.warehouseCode ?? `Kho #${r.warehouseId}`}</span>
      ),
    },
    {
      title: 'Nội dung cảnh báo',
      key: 'message',
      render: (_, r) => <span className="text-xs text-slate-800">{r.message}</span>,
    },
  ]

  // `fill` là màu từng lát cắt do backend gán (Recharts đọc trường này của từng phần tử)
  const trendingSlices = (trending.data?.slices ?? []).map((slice) => ({
    ...slice,
    quantity: Number(slice.quantity),
    fill: slice.color,
  }))

  return (
    <div className="flex flex-col gap-5 w-full">
      {/* 1. Thanh tiêu đề và bộ lọc */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-4 rounded-lg border border-slate-200/80 shadow-xs">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Title level={4} className="!mb-0 font-semibold text-slate-900">
              Bảng điều khiển & Thống kê KPI
            </Title>
            {roleLabel && (
              <Tag color="blue" className="text-xs font-normal">
                {roleLabel}
              </Tag>
            )}
          </div>
          <Text type="secondary" className="text-xs">
            Theo dõi sức chứa, lưu chuyển hàng hóa và cảnh báo vận hành theo thời gian thực
          </Text>
        </div>

        <Space wrap size="middle">
          {/* Bộ lọc Kho theo phạm vi phân quyền (SRS 2.5.2) */}
          <div className="flex items-center gap-1.5">
            <ShopOutlined className="text-slate-400 text-xs" />
            <Text className="text-xs text-slate-500">Kho:</Text>
            <Select
              size="middle"
              className="w-44 sm:w-52"
              value={selectedWarehouseId === 'all' && !isGlobalScope ? undefined : selectedWarehouseId}
              placeholder="Kho được phân công"
              loading={loadingWarehouses}
              onChange={(val) => setSelectedWarehouseId(val)}
              options={[
                ...(isGlobalScope ? [{ label: 'Tất cả kho', value: 'all' as const }] : []),
                ...allowedWarehouses.map((w) => ({
                  label: `${w.name} (${w.code})`,
                  value: w.id,
                })),
              ]}
            />
          </div>

          {/* Bộ lọc Thời gian */}
          <div className="flex items-center gap-1.5">
            <CalendarOutlined className="text-slate-400 text-xs" />
            <Select
              size="middle"
              className="w-32"
              value={timeRange}
              onChange={(val) => setTimeRange(val)}
              options={TIME_RANGE_OPTIONS}
            />
          </div>

          <Button icon={<ReloadOutlined />} onClick={reloadAll} className="text-xs">
            Làm mới
          </Button>

          {canExportExcel ? (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={handleExportExcel}
              disabled={!kpi.data}
              className="text-xs font-medium"
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

      {/* 2. Tóm tắt bằng AI (WMS-18.3) */}
      <AiSummaryCard warehouseId={selectedWarehouseId === 'all' ? null : selectedWarehouseId} ready={!loadingWarehouses} />

      {/* 3. Thông báo phạm vi kho phụ trách */}
      {isWarehouseStaff && (
        <Alert
          type="info"
          showIcon
          icon={<WarningOutlined className="text-blue-600" />}
          message="Chế độ vận hành kho chi nhánh"
          description="Dữ liệu hiển thị các chỉ số xuất nhập và cảnh báo phù hợp với kho bạn phụ trách. Số liệu tài chính được ẩn theo vai trò."
          className="text-xs border-blue-200/80 bg-blue-50/40 rounded-lg"
        />
      )}

      {!isGlobalScope && !isWarehouseStaff && (
        <Alert
          type="warning"
          showIcon
          message="Phạm vi kho phụ trách"
          description="Dữ liệu và số liệu thống kê được giới hạn theo các kho bạn được phân công."
          className="text-xs border-amber-200/80 bg-amber-50/40 rounded-lg"
        />
      )}

      {/* 4. Hàng thẻ KPI (WMS-16.1 dữ liệu, WMS-16.3 giao diện và lọc khi bấm) */}
      {kpi.error ? (
        <Alert
          type="error"
          showIcon
          message="Không tải được số liệu thẻ KPI"
          description={kpi.error}
          action={
            <Button size="small" onClick={reloadAll}>
              Thử lại
            </Button>
          }
          className="rounded-lg"
        />
      ) : (
        <Row gutter={[16, 16]}>
          {kpi.loading
            ? Array.from({ length: 4 }, (_, i) => (
                <Col key={i} xs={24} sm={12} xl={6}>
                  <Card variant="borderless" loading className="h-full border border-slate-200/80" />
                </Col>
              ))
            : kpiCards.map((card) => {
                const isActive = activeKpiCard === card.key
                const clickable = Boolean(card.alertKind)
                return (
                  <Col key={card.key} xs={24} sm={12} xl={6}>
                    <Card
                      variant="borderless"
                      onClick={clickable ? () => handleToggleCardFilter(card) : undefined}
                      role={clickable ? 'button' : undefined}
                      tabIndex={clickable ? 0 : undefined}
                      aria-pressed={clickable ? isActive : undefined}
                      onKeyDown={
                        clickable
                          ? (e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault()
                                handleToggleCardFilter(card)
                              }
                            }
                          : undefined
                      }
                      className={`transition-all duration-150 h-full border ${clickable ? 'cursor-pointer' : ''} ${
                        isActive
                          ? 'border-slate-800 bg-slate-50/70 shadow-2xs ring-1 ring-slate-800/10'
                          : 'border-slate-200/80 bg-white hover:border-slate-300'
                      }`}
                    >
                      <div className="text-xs font-medium text-slate-500">{card.title}</div>
                      <div className="mt-1 text-[1.35rem] font-semibold text-slate-900 tabular-nums break-words">
                        {card.value}
                      </div>
                      <span className="text-xs text-slate-500 mt-1 block" title={card.subText}>
                        {card.subText}
                      </span>
                      {clickable && (
                        <span className="text-[11px] text-slate-400 mt-1 block">
                          Bấm để lọc cảnh báo {KIND_LABELS[card.alertKind as StockAlertKind].toLowerCase()}
                        </span>
                      )}
                    </Card>
                  </Col>
                )
              })}
        </Row>
      )}

      {/* 5. Cảnh báo tồn kho và biểu đồ mặt hàng bán chạy */}
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card
            variant="borderless"
            className="shadow-none border border-slate-200/80 bg-white h-full"
            title={
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 py-1">
                <div className="flex flex-wrap items-center gap-2">
                  <AlertOutlined className="text-amber-500 text-sm" />
                  <span className="font-semibold text-slate-900 text-sm">Cảnh báo Tồn kho & Hạn dùng</span>
                  {alerts.data && (
                    <span className="text-[11px] font-medium text-rose-700 bg-rose-50 border border-rose-200/70 rounded-full px-2 py-0.5">
                      {formatNumber(alerts.data.totalElements)} cần lưu ý
                    </span>
                  )}
                </div>
                {activeCard?.alertKind && (
                  <Tag
                    closable
                    onClose={() => setActiveKpiCard(null)}
                    className="text-xs text-slate-600 bg-slate-100 border-slate-300 m-0 w-fit"
                  >
                    Lọc: {KIND_LABELS[activeCard.alertKind]}
                  </Tag>
                )}
              </div>
            }
          >
            {alerts.error ? (
              <Alert type="error" showIcon message={alerts.error} />
            ) : (
              <Table<StockAlert>
                columns={alertColumns}
                dataSource={alerts.data?.content ?? []}
                loading={alerts.loading}
                rowKey="id"
                pagination={false}
                size="middle"
                locale={{ emptyText: <Empty description="Hiện không có cảnh báo tồn kho" /> }}
              />
            )}
            {alerts.data && alerts.data.totalElements > ALERT_PAGE_SIZE && (
              <div className="mt-3 text-xs text-slate-500">
                Đang hiện {ALERT_PAGE_SIZE} cảnh báo nghiêm trọng nhất; xem đầy đủ ở màn hình Tồn kho.
              </div>
            )}
          </Card>
        </Col>

        <Col xs={24} xl={10}>
          <Card
            variant="borderless"
            className="shadow-none border border-slate-200/80 bg-white h-full"
            loading={trending.loading}
            title={<span className="font-semibold text-slate-900 text-sm">Mặt hàng xuất bán chạy nhất</span>}
          >
            {trending.error ? (
              <Alert type="error" showIcon message={trending.error} />
            ) : trendingSlices.length === 0 ? (
              <Empty description="Chưa có đơn bán đã xác nhận trong khoảng thời gian này" />
            ) : (
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={trendingSlices}
                    dataKey="quantity"
                    nameKey="name"
                    innerRadius={65}
                    outerRadius={100}
                    paddingAngle={2}
                    isAnimationActive={false}
                  >
                    <Label
                      value={formatNumber(Number(trending.data?.totalVolume ?? 0))}
                      position="center"
                      fill="#0f172a"
                      fontSize={14}
                      fontWeight={600}
                    />
                  </Pie>
                  <RechartsTooltip
                    formatter={(value, _name, item) => {
                      const slice = item?.payload as (typeof trendingSlices)[number] | undefined
                      return `${formatNumber(Number(value))} ${slice?.uom ?? ''} (${slice?.percentage ?? 0}%)`
                    }}
                  />
                  <Legend verticalAlign="bottom" itemSorter={null} formatter={renderLegendText} />
                </PieChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  )
}
