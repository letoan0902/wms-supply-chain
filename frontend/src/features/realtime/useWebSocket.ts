import { App } from 'antd'
import { useEffect } from 'react'
import { acquireRealtimeConnection, addNotificationListener } from './realtimeConnection'
import { useRealtimeStore } from './realtimeStore'
import type { ConnectionStatus } from './types'

/** Thời gian hiện toast (giây): cảnh báo tồn kho thấp hiện lâu hơn để người dùng kịp đọc. */
const LOW_STOCK_TOAST_SECONDS = 10
const DEFAULT_TOAST_SECONDS = 5

/**
 * Nối WebSocket (SockJS + STOMP) nhận thông báo thời gian thực và hiện toast cho
 * LOW_STOCK, STOCK_ALERT, ORDER_CREATED, ORDER_DISPATCHED. INVENTORY_UPDATED và KPI_UPDATED chỉ cập nhật store.
 *
 * Dùng một lần ở MainLayout (chỉ hiển thị sau khi đăng nhập). Kết nối được ngắt khi component
 * unmount hoặc khi đăng xuất. Trả về trạng thái kết nối hiện tại.
 */
export function useWebSocket(): ConnectionStatus {
  const { notification } = App.useApp()

  useEffect(() => acquireRealtimeConnection(), [])

  useEffect(
    () =>
      addNotificationListener((message) => {
        const isLowStock = message.type === 'LOW_STOCK'
        notification.open({
          key: message.id,
          type: isLowStock ? 'warning' : message.severity,
          message: message.title,
          description: message.message,
          duration: isLowStock || message.type === 'STOCK_ALERT' ? LOW_STOCK_TOAST_SECONDS : DEFAULT_TOAST_SECONDS,
          placement: 'topRight',
        })
      }),
    [notification],
  )

  return useRealtimeStore((state) => state.status)
}
