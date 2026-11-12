import { useEffect, useRef } from 'react'
import { throttleDelay } from '../inventory/inventoryUtils'
import { addNotificationListener } from '../realtime/realtimeConnection'
import { useRealtimeStore } from '../realtime/realtimeStore'

/** Khoảng cách tối thiểu giữa hai lần tự làm mới do tin thời gian thực. */
export const OUTBOUND_LIVE_REFRESH_MIN_INTERVAL_MS = 5000

/**
 * Tự làm mới danh sách phiếu xuất khi có tin thời gian thực về đơn bán hoặc tồn kho, dùng chung kết nối STOMP của
 * ứng dụng (không mở kết nối thứ hai, không hỏi định kỳ):
 * - ORDER_CREATED, ORDER_DISPATCHED của đơn bán: nhận qua `addNotificationListener`;
 * - INVENTORY_UPDATED: đọc từ store (`lastInventoryUpdate`) mà kết nối chung cập nhật.
 * Mỗi {@link OUTBOUND_LIVE_REFRESH_MIN_INTERVAL_MS} làm mới tối đa một lần; khi tab bị ẩn thì chỉ ghi nhận và làm
 * mới một lần lúc người dùng quay lại tab.
 */
export function useOutboundLiveRefresh(onRefresh: () => void): void {
  const onRefreshRef = useRef(onRefresh)
  const lastRunRef = useRef<number | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pendingRef = useRef(false)

  useEffect(() => {
    onRefreshRef.current = onRefresh
  }, [onRefresh])

  useEffect(() => {
    const isHidden = () => typeof document !== 'undefined' && document.visibilityState === 'hidden'

    const run = () => {
      lastRunRef.current = Date.now()
      pendingRef.current = false
      onRefreshRef.current()
    }

    const trigger = () => {
      if (isHidden()) {
        pendingRef.current = true
        return
      }
      if (timerRef.current) return
      const delay = throttleDelay(Date.now(), lastRunRef.current, OUTBOUND_LIVE_REFRESH_MIN_INTERVAL_MS)
      if (delay === 0) {
        run()
        return
      }
      timerRef.current = setTimeout(() => {
        timerRef.current = null
        if (isHidden()) {
          pendingRef.current = true
        } else {
          run()
        }
      }, delay)
    }

    const unsubscribeStore = useRealtimeStore.subscribe((state, previous) => {
      if (state.lastInventoryUpdate && state.lastInventoryUpdate !== previous.lastInventoryUpdate) trigger()
    })

    const unsubscribeOrders = addNotificationListener((message) => {
      if (message.type === 'ORDER_CREATED' || message.type === 'ORDER_DISPATCHED') {
        // Đơn mua không ảnh hưởng phiếu xuất
        if (message.payload?.orderType === 'PURCHASE') return
        trigger()
      }
    })

    const handleVisibility = () => {
      if (!isHidden() && pendingRef.current) trigger()
    }
    document.addEventListener('visibilitychange', handleVisibility)

    return () => {
      unsubscribeStore()
      unsubscribeOrders()
      document.removeEventListener('visibilitychange', handleVisibility)
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [])
}
