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
  STOCK_ALERT: <WarningOutlined />,
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
      className="flex gap-3 rounded-md px-2 py-2"
      style={{ background: item.read ? undefined : unreadBackground }}
    >
      <span className="pt-0.5 text-base" style={{ color }}>
        {TYPE_ICONS[item.type]}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <Typography.Text strong={!item.read} className="min-w-0">
            {item.title}
          </Typography.Text>
          <Typography.Text type="secondary" className="shrink-0 text-xs">
            {formatNotificationTime(item.occurredAt)}
          </Typography.Text>
        </div>
        <Typography.Paragraph type="secondary" className="text-sm" style={{ margin: 0 }}>
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
    <div className="w-[360px] max-w-[calc(100vw-104px)]">
      <div className="flex items-center justify-between gap-2 pb-2">
        <Typography.Text strong className="whitespace-nowrap">
          Thông báo
        </Typography.Text>
        <div className="flex flex-wrap items-center justify-end">
          <Button type="link" size="small" disabled={unreadCount === 0} onClick={markAllAsRead}>
            Đánh dấu đã đọc
          </Button>
          <Button
            type="link"
            size="small"
            disabled={notifications.length === 0}
            onClick={clearNotifications}
          >
            Xóa tất cả
          </Button>
        </div>
      </div>

      {status === 'disconnected' && (
        <div className="mb-2 flex items-center justify-between gap-2">
          <Typography.Text type="secondary" className="text-xs">
            {statusConfig.label}
          </Typography.Text>
          <Button size="small" onClick={reconnectRealtime}>
            Kết nối lại
          </Button>
        </div>
      )}

      {notifications.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có thông báo" />
      ) : (
        <ul className="m-0 flex max-h-[400px] list-none flex-col gap-1 overflow-y-auto p-0">
          {notifications.map((item) => (
            <NotificationItem
              key={item.id}
              item={item}
              color={severityColors[item.severity]}
              unreadBackground={token.colorPrimaryBg}
            />
          ))}
        </ul>
      )}
    </div>
  )

  return (
    <div className="flex items-center gap-1">
      <Tooltip title={statusConfig.label}>
        <span className="inline-flex cursor-default px-1" aria-label={statusConfig.label}>
          <Badge status={statusConfig.badge} />
        </span>
      </Tooltip>
      <Popover content={content} trigger="click" placement="bottomRight" arrow={false}>
        <Badge count={unreadCount} size="small" overflowCount={99}>
          <Button
            type="text"
            shape="circle"
            icon={<BellOutlined style={{ fontSize: 18 }} />}
            aria-label={unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'}
          />
        </Badge>
      </Popover>
    </div>
  )
}
