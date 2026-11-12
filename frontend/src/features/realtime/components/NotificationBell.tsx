import {
  BellOutlined,
  CarOutlined,
  ShoppingCartOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Badge, Button, Empty, Popover, Tabs, Tooltip, Typography, theme } from 'antd'
import type { BadgeProps } from 'antd'
import { useEffect, useMemo, useState, type KeyboardEvent, type ReactNode } from 'react'
import { useCan } from '../../auth/useCan'
import { formatNotificationTime, formatRelativeTime } from '../notificationTime'
import { reconnectRealtime } from '../realtimeConnection'
import { useRealtimeStore } from '../realtimeStore'
import type { ConnectionStatus, RealtimeNotification, RealtimeSeverity } from '../types'
import { urgentCount, useStockAlertFeed, type AlertFilter } from '../useStockAlertFeed'
import StockAlertPanel from './StockAlertPanel'

const STATUS_CONFIG: Record<ConnectionStatus, { badge: BadgeProps['status']; label: string }> = {
  idle: { badge: 'default', label: 'Chưa kết nối thông báo thời gian thực' },
  connecting: { badge: 'processing', label: 'Đang kết nối máy chủ thông báo...' },
  connected: { badge: 'success', label: 'Đang nhận thông báo thời gian thực' },
  reconnecting: { badge: 'warning', label: 'Mất kết nối, đang thử kết nối lại...' },
  disconnected: { badge: 'error', label: 'Đã ngắt kết nối thông báo thời gian thực' },
}

const TYPE_ICONS: Record<RealtimeNotification['type'], ReactNode> = {
  LOW_STOCK: <WarningOutlined />,
  STOCK_ALERT: <WarningOutlined />,
  ORDER_CREATED: <ShoppingCartOutlined />,
  ORDER_DISPATCHED: <CarOutlined />,
}

/** Số hiển thị trên huy hiệu tối đa; lớn hơn thì hiện 99+. */
const BADGE_OVERFLOW = 99
/** Chu kỳ cập nhật chữ "x phút trước" khi popover đang mở. */
const CLOCK_TICK_MS = 30000

type BellTab = 'alerts' | 'activity'

interface NotificationItemProps {
  item: RealtimeNotification
  color: string
  unreadBackground: string
  now: number
}

function NotificationItem({ item, color, unreadBackground, now }: NotificationItemProps) {
  return (
    <li
      className="flex gap-2.5 rounded px-2.5 py-2 transition-colors border-b border-slate-100 last:border-b-0"
      style={{ background: item.read ? undefined : unreadBackground }}
    >
      <span className="pt-0.5 text-sm shrink-0" style={{ color }}>
        {TYPE_ICONS[item.type]}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <Typography.Text strong={!item.read} className="min-w-0 text-xs text-slate-800">
            {item.title}
          </Typography.Text>
          <Typography.Text
            type="secondary"
            className="shrink-0 text-[11px] text-slate-400"
            title={formatNotificationTime(item.occurredAt)}
          >
            {formatRelativeTime(item.occurredAt, now)}
          </Typography.Text>
        </div>
        <Typography.Paragraph type="secondary" className="text-xs text-slate-500 mt-0.5" style={{ margin: 0 }}>
          {item.message}
        </Typography.Paragraph>
      </div>
    </li>
  )
}

/** Danh sách tin thời gian thực gần đây (đơn hàng, cảnh báo), giữ trong bộ nhớ của phiên. */
function ActivityPanel({ now }: { now: number }) {
  const { token } = theme.useToken()
  const status = useRealtimeStore((state) => state.status)
  const notifications = useRealtimeStore((state) => state.notifications)
  const unreadCount = useRealtimeStore((state) => state.unreadCount)
  const markAllAsRead = useRealtimeStore((state) => state.markAllAsRead)
  const clearNotifications = useRealtimeStore((state) => state.clearNotifications)

  const severityColors: Record<RealtimeSeverity, string> = {
    info: token.colorInfo,
    success: token.colorSuccess,
    warning: token.colorWarning,
    error: token.colorError,
  }

  return (
    <div>
      <div className="flex items-center justify-between gap-2 pb-2.5 border-b border-slate-100">
        <span className="text-xs font-semibold text-slate-800">
          Thông báo
        </span>
        <div className="flex items-center gap-1">
          <Button
            type="text"
            size="small"
            disabled={unreadCount === 0}
            onClick={markAllAsRead}
            className="text-[11px] text-slate-500 hover:text-slate-800 px-1.5 h-6"
          >
            Đã đọc
          </Button>
          <Button
            type="text"
            size="small"
            disabled={notifications.length === 0}
            onClick={clearNotifications}
            className="text-[11px] text-slate-400 hover:text-rose-600 px-1.5 h-6"
          >
            Xóa
          </Button>
        </div>
      </div>

      {status === 'disconnected' && (
        <div className="my-2 flex items-center justify-between gap-2 bg-slate-50 p-2 rounded border border-slate-200/80">
          <span className="text-[11px] text-slate-500">
            {STATUS_CONFIG[status].label}
          </span>
          <Button size="small" onClick={reconnectRealtime} className="text-xs h-6">
            Kết nối lại
          </Button>
        </div>
      )}

      {notifications.length === 0 ? (
        <div className="py-6">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<span className="text-xs text-slate-400">Không có thông báo mới</span>} />
        </div>
      ) : (
        <ul className="m-0 flex max-h-[380px] list-none flex-col overflow-y-auto p-0 mt-1">
          {notifications.map((item) => (
            <NotificationItem
              key={item.id}
              item={item}
              color={severityColors[item.severity]}
              unreadBackground="rgba(37, 99, 235, 0.04)"
              now={now}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

function countText(count: number): string {
  return count > BADGE_OVERFLOW ? `${BADGE_OVERFLOW}+` : String(count)
}

/**
 * Chuông thông báo trên Header.
 * - Vai trò xem được cảnh báo tồn kho: huy hiệu là số cảnh báo cần xử lý (tồn thấp, cận hạn, hết hạn) trong phạm vi kho
 *   của người dùng, lấy từ GET /api/inventory/alerts/summary; popover có mục "Cảnh báo tồn kho" (danh sách từ
 *   GET /api/inventory/alerts, nút tạo đơn mua nháp cho tồn thấp) và mục "Hoạt động" (tin thời gian thực gần đây).
 * - Vai trò khác: huy hiệu là số tin thời gian thực chưa đọc, popover chỉ có danh sách tin.
 * Dùng kết nối STOMP chung của ứng dụng; toast do useWebSocket hiển thị, chuông không hiện thêm.
 */
export default function NotificationBell() {
  const { token } = theme.useToken()
  const can = useCan()
  const canSeeAlerts = can('alert.read')
  const canCreatePurchaseOrder = can('purchaseOrder.write')
  const globalAlertScope = can.global('alert.read')
  const assignedWarehouseIds = can.warehouseIds
  const scopeWarehouseIds = useMemo(
    () => (globalAlertScope ? null : assignedWarehouseIds),
    [globalAlertScope, assignedWarehouseIds],
  )

  const status = useRealtimeStore((state) => state.status)
  const unreadCount = useRealtimeStore((state) => state.unreadCount)

  const [open, setOpen] = useState(false)
  const [tab, setTab] = useState<BellTab>('alerts')
  const [filter, setFilter] = useState<AlertFilter>('ALL')
  const [now, setNow] = useState(0)

  const feed = useStockAlertFeed({
    enabled: canSeeAlerts,
    listOpen: open && canSeeAlerts && tab === 'alerts',
    filter,
    scopeWarehouseIds,
  })
  const { refresh } = feed
  const alertCount = urgentCount(feed.summary)
  const criticalCount = feed.summary?.countsBySeverity?.CRITICAL ?? 0

  // Cập nhật chữ "x phút trước" khi popover đang mở
  useEffect(() => {
    if (!open) return
    const timer = setInterval(() => setNow(Date.now()), CLOCK_TICK_MS)
    return () => clearInterval(timer)
  }, [open])

  const handleOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      setNow(Date.now())
      // Mở popover thì lấy lại số đếm và danh sách mới nhất (cảnh báo tính từ tồn hiện tại, có thể đổi theo ngày)
      if (canSeeAlerts) refresh()
    }
  }

  const close = () => setOpen(false)
  // Phím Esc trong popover thì đóng popover (người dùng bàn phím)
  const handleContentKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') close()
  }
  const statusConfig = STATUS_CONFIG[status]

  let ariaLabel: string
  if (canSeeAlerts) {
    const alertPart = feed.summary
      ? alertCount > 0
        ? `${alertCount} cảnh báo tồn kho cần xử lý${criticalCount > 0 ? `, ${criticalCount} khẩn cấp` : ''}`
        : 'không có cảnh báo tồn kho cần xử lý'
      : 'chưa tải được số cảnh báo tồn kho'
    ariaLabel = `Thông báo: ${alertPart}${unreadCount > 0 ? `, ${unreadCount} tin chưa đọc` : ''}`
  } else {
    ariaLabel = unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'
  }

  const content = canSeeAlerts ? (
    <div className="w-[380px] max-w-[calc(100vw-32px)]" onKeyDown={handleContentKeyDown}>
      <Tabs
        size="small"
        activeKey={tab}
        onChange={(key) => setTab(key as BellTab)}
        tabBarStyle={{ marginBottom: 8 }}
        items={[
          {
            key: 'alerts',
            label: (
              <span className="text-xs">
                Cảnh báo tồn kho
                {feed.summary && alertCount > 0 && (
                  <span className="ml-1 tabular-nums text-rose-600">{countText(alertCount)}</span>
                )}
              </span>
            ),
            children: (
              <StockAlertPanel
                feed={feed}
                filter={filter}
                onFilterChange={setFilter}
                canCreatePurchaseOrder={canCreatePurchaseOrder}
                now={now}
                onNavigate={close}
              />
            ),
          },
          {
            key: 'activity',
            label: (
              <span className="text-xs">
                Hoạt động
                {unreadCount > 0 && <span className="ml-1 tabular-nums text-blue-600">{countText(unreadCount)}</span>}
              </span>
            ),
            children: <ActivityPanel now={now} />,
          },
        ]}
      />
    </div>
  ) : (
    <div className="w-[340px] max-w-[calc(100vw-32px)]" onKeyDown={handleContentKeyDown}>
      <ActivityPanel now={now} />
    </div>
  )

  const badgeCount = canSeeAlerts ? alertCount : unreadCount
  const badgeColor = canSeeAlerts && criticalCount === 0 ? token.colorWarning : undefined

  return (
    <div className="flex items-center gap-1.5">
      <Tooltip title={statusConfig.label}>
        <span className="inline-flex cursor-default p-0.5" aria-label={statusConfig.label}>
          <Badge status={statusConfig.badge} />
        </span>
      </Tooltip>
      <Popover
        content={content}
        trigger="click"
        placement="bottomRight"
        arrow={false}
        open={open}
        onOpenChange={handleOpenChange}
      >
        <Badge count={badgeCount} size="small" overflowCount={BADGE_OVERFLOW} color={badgeColor}>
          <Button
            type="text"
            shape="circle"
            className="h-8 w-8 flex items-center justify-center text-slate-500 hover:text-slate-800 hover:bg-slate-100"
            icon={<BellOutlined className="text-base" />}
            aria-label={ariaLabel}
            aria-haspopup="dialog"
            aria-expanded={open}
          />
        </Badge>
      </Popover>
    </div>
  )
}
