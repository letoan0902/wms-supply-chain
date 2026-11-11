import { useEffect, useRef } from 'react'
import { addNotificationListener } from '../realtime/realtimeConnection'
import { useRealtimeStore } from '../realtime/realtimeStore'
import { isRelevantWarehouse, throttleDelay } from './inventoryUtils'

/** Khoảng cách tối thiểu giữa hai lần tự làm mới do tin thời gian thực. */
export const LIVE_REFRESH_MIN_INTERVAL_MS = 5000

interface LiveRefreshOptions {
  /** Kho bảng đang xem; null là mọi kho trong phạm vi của người dùng */
  visibleWarehouseIds: readonly number[] | null
  /** Làm mới trang hiện tại của bảng */
  onRefresh: () => void
  /** Tắt tạm (ví dụ khi trang chưa sẵn sàng) */
  enabled: boolean
}

/**
 * Tự làm mới bảng tồn kho khi có tin thời gian thực của kho đang xem, dùng chung kết nối STOMP của ứng dụng
 * (không mở kết nối riêng, không hỏi định kỳ):
 * - INVENTORY_UPDATED: đọc từ store (`lastInventoryUpdate`) mà kết nối chung cập nhật;
 * - STOCK_ALERT, LOW_STOCK: nhận qua `addNotificationListener`.
 * Mỗi {@link LIVE_REFRESH_MIN_INTERVAL_MS} làm mới tối đa một lần; khi tab bị ẩn thì chỉ ghi nhận và làm mới một lần
 * lúc người dùng quay lại tab.
 */
export function useLiveRefresh({ visibleWarehouseIds, onRefresh, enabled }: LiveRefreshOptions): void {
  const onRefreshRef = useRef(onRefresh)
  const visibleRef = useRef(visibleWarehouseIds)
  const enabledRef = useRef(enabled)
  const lastRunRef = useRef<number | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pendingRef = useRef(false)

  useEffect(() => {
    onRefreshRef.current = onRefresh
    visibleRef.current = visibleWarehouseIds
    enabledRef.current = enabled
  }, [onRefresh, visibleWarehouseIds, enabled])

  useEffect(() => {
    const isHidden = () => typeof document !== 'undefined' && document.visibilityState === 'hidden'

    const run = () => {
      lastRunRef.current = Date.now()
      pendingRef.current = false
      onRefreshRef.current()
    }

    const trigger = () => {
      if (!enabledRef.current) return
      if (isHidden()) {
        pendingRef.current = true
        return
      }
      if (timerRef.current) return
      const delay = throttleDelay(Date.now(), lastRunRef.current, LIVE_REFRESH_MIN_INTERVAL_MS)
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

    const handleWarehouse = (warehouseId: number | null | undefined) => {
      if (isRelevantWarehouse(warehouseId, visibleRef.current)) trigger()
    }

    const unsubscribeStore = useRealtimeStore.subscribe((state, previous) => {
      const update = state.lastInventoryUpdate
      if (update && update !== previous.lastInventoryUpdate) {
        handleWarehouse(update.warehouseId)
      }
    })

    const unsubscribeAlerts = addNotificationListener((message) => {
      if (message.type === 'STOCK_ALERT' || message.type === 'LOW_STOCK') {
        handleWarehouse(message.payload?.warehouseId)
      }
    })

    const handleVisibility = () => {
      if (!isHidden() && pendingRef.current) trigger()
    }
    document.addEventListener('visibilitychange', handleVisibility)

    return () => {
      unsubscribeStore()
      unsubscribeAlerts()
      document.removeEventListener('visibilitychange', handleVisibility)
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [])
}
