import { ReloadOutlined, ShoppingCartOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Spin, Tag } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { alertReason, alertTagLabel, KIND_LABELS, SEVERITY_COLORS } from '../../inventory/inventoryUtils'
import type { StockAlert } from '../../inventory/types'
import { purchasePrefillState } from '../../purchases/purchaseDraft'
import { formatRelativeTime } from '../notificationTime'
import { URGENT_KINDS, urgentCount, type AlertFilter, type StockAlertFeed } from '../useStockAlertFeed'

const FILTERS: readonly AlertFilter[] = ['ALL', ...URGENT_KINDS]

function filterLabel(filter: AlertFilter): string {
  return filter === 'ALL' ? 'Tất cả' : KIND_LABELS[filter]
}

function productLabel(alert: StockAlert): string {
  return alert.productName ?? `Sản phẩm mã ${alert.productId}`
}

function warehouseLabel(alert: StockAlert): string {
  return alert.warehouseName ?? alert.warehouseCode ?? `Kho mã ${alert.warehouseId}`
}

/** Màn hình Tồn kho lọc đúng loại cảnh báo và kho (InventoryPage đọc `kind`, `warehouseId` trên URL). */
function inventoryHref(alert: StockAlert): string {
  return `/inventory?kind=${alert.kind}&warehouseId=${alert.warehouseId}`
}

interface AlertRowProps {
  alert: StockAlert
  canCreatePurchaseOrder: boolean
  onNavigate: () => void
}

function AlertRow({ alert, canCreatePurchaseOrder, onNavigate }: AlertRowProps) {
  const navigate = useNavigate()
  const product = productLabel(alert)
  const warehouse = warehouseLabel(alert)
  const reason = alertReason(alert)

  // Chỉ mở form lập đơn mua đã điền sẵn sản phẩm và kho; chưa lưu gì cho tới khi người dùng bấm lưu trong form
  const openDraftPurchaseOrder = () => {
    onNavigate()
    navigate('/purchases', {
      state: purchasePrefillState({
        productId: alert.productId,
        skuCode: alert.skuCode,
        productName: alert.productName,
        warehouseId: alert.warehouseId,
        sourceDescription: `${alert.skuCode ? `[${alert.skuCode}] ` : ''}${product} tại ${warehouse}: ${reason}.`,
      }),
    })
  }

  return (
    <li className="border-b border-slate-100 last:border-b-0">
      <Link
        to={inventoryHref(alert)}
        onClick={onNavigate}
        className="block rounded px-2 py-2 !text-inherit transition-colors hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500"
        title="Xem trên màn hình Tồn kho"
      >
        <div className="flex items-start justify-between gap-2">
          <div className="flex min-w-0 items-center gap-1.5">
            <Tag color={SEVERITY_COLORS[alert.severity]} className="!m-0 shrink-0 !text-[10px] font-semibold !leading-4">
              {alertTagLabel(alert.kind, alert.severity)}
            </Tag>
            {alert.skuCode && <span className="truncate font-mono text-[11px] text-slate-500">{alert.skuCode}</span>}
          </div>
          <span className="max-w-[45%] shrink-0 truncate text-[11px] text-slate-500" title={warehouse}>
            {warehouse}
          </span>
        </div>
        <div className="mt-0.5 truncate text-xs font-medium text-slate-800" title={product}>
          {product}
        </div>
        <div className="mt-0.5 text-[11px] leading-snug text-slate-500">{reason}</div>
      </Link>
      {alert.kind === 'LOW_STOCK' && canCreatePurchaseOrder && (
        <div className="px-2 pb-2">
          <Button
            size="small"
            icon={<ShoppingCartOutlined />}
            onClick={openDraftPurchaseOrder}
            className="h-6 text-[11px]"
            aria-label={`Tạo đơn mua nháp cho ${product} tại ${warehouse}`}
          >
            Tạo đơn mua nháp
          </Button>
        </div>
      )}
    </li>
  )
}

interface StockAlertPanelProps {
  feed: StockAlertFeed
  filter: AlertFilter
  onFilterChange: (filter: AlertFilter) => void
  canCreatePurchaseOrder: boolean
  /** Thời điểm hiện tại (mili giây) để tính "x phút trước" */
  now: number
  /** Đóng popover trước khi chuyển trang */
  onNavigate: () => void
}

/** Nội dung mục "Cảnh báo tồn kho" trong popover của chuông: lọc theo loại, danh sách, trạng thái tải, lỗi, rỗng. */
export default function StockAlertPanel({
  feed,
  filter,
  onFilterChange,
  canCreatePurchaseOrder,
  now,
  onNavigate,
}: StockAlertPanelProps) {
  const { summary, summaryError, items, listReady, listLoading, listError, loadedAt, refresh } = feed

  const countOf = (value: AlertFilter): number | null => {
    if (!summary) return null
    return value === 'ALL' ? urgentCount(summary) : summary.countsByKind?.[value] ?? 0
  }
  const total = countOf(filter)
  const lowStockUnavailable = summary !== null && !summary.lowStockAvailable

  let body
  if (listError) {
    body = (
      <Alert
        type="error"
        showIcon
        className="my-2 text-xs"
        message={listError}
        action={
          <Button size="small" onClick={refresh} loading={listLoading}>
            Thử lại
          </Button>
        }
      />
    )
  } else if (!listReady || (listLoading && items.length === 0)) {
    body = (
      <div className="flex justify-center py-8">
        <Spin size="small" tip="Đang tải cảnh báo...">
          <div className="h-8 w-24" />
        </Spin>
      </div>
    )
  } else if (items.length === 0) {
    body = (
      <div className="py-6">
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={<span className="text-xs text-slate-400">Không có cảnh báo cần xử lý</span>}
        />
      </div>
    )
  } else {
    body = (
      <Spin spinning={listLoading} size="small">
        <ul className="m-0 mt-1 flex max-h-[360px] list-none flex-col overflow-y-auto p-0" aria-label="Danh sách cảnh báo tồn kho">
          {items.map((alert) => (
            <AlertRow
              key={alert.id}
              alert={alert}
              canCreatePurchaseOrder={canCreatePurchaseOrder}
              onNavigate={onNavigate}
            />
          ))}
        </ul>
      </Spin>
    )
  }

  return (
    <div>
      <div className="flex flex-wrap gap-1 pb-2" role="group" aria-label="Lọc theo loại cảnh báo">
        {FILTERS.map((value) => {
          const active = value === filter
          const count = countOf(value)
          return (
            <Button
              key={value}
              size="small"
              type={active ? 'primary' : 'default'}
              aria-pressed={active}
              onClick={() => onFilterChange(value)}
              className="h-6 px-2 text-[11px]"
            >
              {filterLabel(value)}
              {count !== null && <span className="ml-1 tabular-nums">{count > 99 ? '99+' : count}</span>}
            </Button>
          )
        })}
      </div>

      {lowStockUnavailable && (
        <Alert
          type="warning"
          showIcon
          className="mb-2 text-xs"
          message={summary.note ?? 'Chưa tính được cảnh báo tồn thấp, số đếm chưa gồm loại này.'}
        />
      )}
      {summaryError && (
        <Alert type="warning" showIcon className="mb-2 text-xs" message={`Số đếm chưa cập nhật: ${summaryError}`} />
      )}

      {body}

      <div className="mt-1 flex items-center justify-between gap-2 border-t border-slate-100 pt-2">
        <span className="min-w-0 truncate text-[11px] text-slate-400">
          {loadedAt ? `Cập nhật ${formatRelativeTime(loadedAt, now)}` : ''}
          {total !== null && items.length > 0 && items.length < total ? `, hiện ${items.length}/${total}` : ''}
        </span>
        <div className="flex shrink-0 items-center gap-1">
          <Button
            type="text"
            size="small"
            icon={<ReloadOutlined />}
            onClick={refresh}
            loading={listLoading}
            aria-label="Làm mới cảnh báo tồn kho"
            className="h-6 w-6 text-slate-500"
          />
          <Link
            to={filter === 'ALL' ? '/inventory' : `/inventory?kind=${filter}`}
            onClick={onNavigate}
            className="text-[11px] font-medium"
          >
            Xem tất cả
          </Link>
        </div>
      </div>
    </div>
  )
}
