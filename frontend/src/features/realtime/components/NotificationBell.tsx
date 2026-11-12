import {
  BellOutlined,
  CarOutlined,
  ShoppingCartOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Badge, Button, Empty, Popover, Tooltip, Typography, theme } from 'antd'
import type { BadgeProps } from 'antd'
import type { ReactNode } from 'react'
import { formatNotificationTime } from '../notificationTime'
import { reconnectRealtime } from '../realtimeConnection'
import { useRealtimeStore } from '../realtimeStore'
import type { ConnectionStatus, RealtimeNotification, RealtimeSeverity } from '../types'

const STATUS_CONFIG: Record<ConnectionStatus, { badge: BadgeProps['status']; label: string }> = {
  idle: { badge: 'default', label: 'Chưa kết nối thông báo thời gian thực' },
  connecting: { badge: 'processing', label: 'Đang kết nối máy chủ thông báo...' },
  connected: { badge: 'success', label: 'Đang nhận thông báo thời gian thực' },
  reconnecting: { badge: 'warning', label: 'Mất kết nối, đang thử kết nối lại...' },
  disconnected: { badge: 'error', label: 'Đã ngắt kết nối thông báo thời gian thực' },
}

const TYPE_ICONS: Record<RealtimeNotification['type'], ReactNode> = {
  LOW_STOCK: <WarningOutlined />,
  ORDER_CREATED: <ShoppingCartOutlined />,
  ORDER_DISPATCHED: <CarOutlined />,
}

interface NotificationItemProps {
  item: RealtimeNotification
  color: string
  unreadBackground: string
}

function NotificationItem({ item, color, unreadBackground }: NotificationItemProps) {
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
          <Typography.Text type="secondary" className="shrink-0 text-[11px] text-slate-400 font-mono">
            {formatNotificationTime(item.occurredAt)}
          </Typography.Text>
        </div>
        <Typography.Paragraph type="secondary" className="text-xs text-slate-500 mt-0.5" style={{ margin: 0 }}>
          {item.message}
        </Typography.Paragraph>
      </div>
    </li>
  )
}

/** Chuông thông báo trên Header: số chưa đọc, danh sách gần đây và trạng thái kết nối thời gian thực. */
export default function NotificationBell() {
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
  const statusConfig = STATUS_CONFIG[status]

  const content = (
    <div className="w-[340px] max-w-[calc(100vw-48px)]">
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
            {statusConfig.label}
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
            />
          ))}
        </ul>
      )}
    </div>
  )

  return (
    <div className="flex items-center gap-1.5">
      <Tooltip title={statusConfig.label}>
        <span className="inline-flex cursor-default p-0.5" aria-label={statusConfig.label}>
          <Badge status={statusConfig.badge} />
        </span>
      </Tooltip>
      <Popover content={content} trigger="click" placement="bottomRight" arrow={false}>
        <Badge count={unreadCount} size="small" overflowCount={99}>
          <Button
            type="text"
            shape="circle"
            className="h-8 w-8 flex items-center justify-center text-slate-500 hover:text-slate-800 hover:bg-slate-100"
            icon={<BellOutlined className="text-base" />}
            aria-label={unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'}
          />
        </Badge>
      </Popover>
    </div>
  )
}
