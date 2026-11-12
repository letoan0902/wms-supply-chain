import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchAlerts, fetchAlertSummary } from '../inventory/inventoryApi'
import { isRelevantWarehouse } from '../inventory/inventoryUtils'
import type { StockAlert, StockAlertKind, StockAlertSummary } from '../inventory/types'
import { toViewError } from '../inventory/viewError'
import { addNotificationListener } from './realtimeConnection'
import { useRealtimeStore } from './realtimeStore'

/**
 * Dữ liệu của chuông cảnh báo tồn kho trên Header: số đếm (GET /api/inventory/alerts/summary) và danh sách
 * (GET /api/inventory/alerts), tự làm mới khi có tin thời gian thực trên kết nối STOMP dùng chung của ứng dụng.
 */

/** Loại cảnh báo cần xử lý (mua thêm hoặc xuất trước, xử lý hàng hết hạn); tồn lâu (DEAD_STOCK) không tính. */
export const URGENT_KINDS: readonly StockAlertKind[] = ['LOW_STOCK', 'NEAR_EXPIRY', 'EXPIRED']

export type AlertFilter = 'ALL' | (typeof URGENT_KINDS)[number]

/** Số dòng tối đa hiển thị trong popover; xem đủ ở màn hình Tồn kho. */
export const POPOVER_ALERT_LIMIT = 20

/** Gom các tin cảnh báo đến gần nhau thành một lần gọi lại API. */
const ALERT_EVENT_DELAY_MS = 1500
/** Biến động tồn kho xảy ra liên tục khi nhập xuất: làm mới thưa hơn. */
const INVENTORY_EVENT_DELAY_MS = 10000

/** Số cảnh báo cần xử lý trong tóm tắt: tồn thấp, cận hạn, hết hạn. */
export function urgentCount(summary: StockAlertSummary | null): number {
  if (!summary) return 0
  return URGENT_KINDS.reduce((total, kind) => total + (summary.countsByKind?.[kind] ?? 0), 0)
}

export interface StockAlertFeed {
  summary: StockAlertSummary | null
  summaryError: string | null
  items: StockAlert[]
  /** Đã có ít nhất một lần tải danh sách xong (thành công hoặc lỗi) */
  listReady: boolean
  listLoading: boolean
  listError: string | null
  /** Thời điểm tải danh sách hoặc số đếm thành công gần nhất */
  loadedAt: Date | null
  /** Gọi lại số đếm và danh sách (danh sách chỉ khi popover đang mở) */
  refresh: () => void
}

interface Options {
  /** Người dùng được xem cảnh báo tồn kho; false thì không gọi API nào */
  enabled: boolean
  /** Popover đang mở ở mục cảnh báo: chỉ khi đó mới tải danh sách */
  listOpen: boolean
  filter: AlertFilter
  /** Kho trong phạm vi của người dùng; null là mọi kho của doanh nghiệp (ADMIN, ACCOUNTANT) */
  scopeWarehouseIds: readonly number[] | null
}

function errorText(error: unknown, fallback: string): string {
  return toViewError(error, fallback).message || fallback
}

export function useStockAlertFeed({ enabled, listOpen, filter, scopeWarehouseIds }: Options): StockAlertFeed {
  const [summary, setSummary] = useState<StockAlertSummary | null>(null)
  const [summaryError, setSummaryError] = useState<string | null>(null)
  const [items, setItems] = useState<StockAlert[]>([])
  const [listReady, setListReady] = useState(false)
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState<string | null>(null)
  const [loadedAt, setLoadedAt] = useState<Date | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const scopeRef = useRef(scopeWarehouseIds)
  useEffect(() => {
    scopeRef.current = scopeWarehouseIds
  }, [scopeWarehouseIds])

  const refresh = useCallback(() => setRefreshKey((key) => key + 1), [])

  // Số đếm cho huy hiệu: khi vào ứng dụng, mỗi lần mở popover, khi có tin thời gian thực và khi nối lại
  useEffect(() => {
    if (!enabled) return
    let ignore = false
    fetchAlertSummary()
      .then((data) => {
        if (ignore) return
        setSummary(data)
        setSummaryError(null)
        setLoadedAt(new Date())
      })
      .catch((err: unknown) => {
        if (!ignore) setSummaryError(errorText(err, 'Không tải được số cảnh báo tồn kho'))
      })
    return () => {
      ignore = true
    }
  }, [enabled, refreshKey])

  // Danh sách: chỉ tải khi popover mở. Thứ tự do backend quyết định: khẩn cấp trước, rồi theo loại, hạn gần trước.
  useEffect(() => {
    if (!enabled || !listOpen) return
    let ignore = false
    const load = async () => {
      setListLoading(true)
      try {
        const page = await fetchAlerts(
          filter === 'ALL'
            ? { page: 0, size: POPOVER_ALERT_LIMIT }
            : { kind: filter, page: 0, size: POPOVER_ALERT_LIMIT },
        )
        if (ignore) return
        const content = page.content ?? []
        // Không lọc loại thì backend trả cả tồn lâu (mức thông tin, xếp cuối): chuông chỉ hiện loại cần xử lý
        setItems(filter === 'ALL' ? content.filter((alert) => URGENT_KINDS.includes(alert.kind)) : content)
        setListError(null)
        setLoadedAt(new Date())
      } catch (err) {
        if (!ignore) setListError(errorText(err, 'Không tải được danh sách cảnh báo tồn kho'))
      } finally {
        if (!ignore) {
          setListLoading(false)
          setListReady(true)
        }
      }
    }
    void load()
    return () => {
      ignore = true
    }
  }, [enabled, listOpen, filter, refreshKey])

  // Tin thời gian thực trên kết nối dùng chung (không mở kết nối riêng). Toast do useWebSocket hiển thị, ở đây chỉ
  // gọi lại API, gom nhiều tin thành một lần gọi; tab bị ẩn thì chờ người dùng quay lại mới gọi.
  useEffect(() => {
    if (!enabled) return
    let timer: ReturnType<typeof setTimeout> | null = null
    let dueAt = 0
    let pending = false
    const isHidden = () => document.visibilityState === 'hidden'

    const fire = () => {
      timer = null
      if (isHidden()) {
        pending = true
        return
      }
      pending = false
      setRefreshKey((key) => key + 1)
    }

    const schedule = (delay: number) => {
      const due = Date.now() + delay
      if (timer && dueAt <= due) return
      if (timer) clearTimeout(timer)
      dueAt = due
      timer = setTimeout(fire, delay)
    }

    // Tin của kho ngoài phạm vi đã bị realtimeConnection bỏ qua trước khi tới đây
    const unsubscribeAlerts = addNotificationListener((message) => {
      if (message.type === 'STOCK_ALERT' || message.type === 'LOW_STOCK') schedule(ALERT_EVENT_DELAY_MS)
    })

    let hadConnection = useRealtimeStore.getState().status === 'connected'
    const unsubscribeStore = useRealtimeStore.subscribe((state, previous) => {
      const update = state.lastInventoryUpdate
      if (update && update !== previous.lastInventoryUpdate && isRelevantWarehouse(update.warehouseId, scopeRef.current)) {
        schedule(INVENTORY_EVENT_DELAY_MS)
      }
      if (state.status === 'connected' && previous.status !== 'connected') {
        // Nối lại sau khi mất kết nối: có thể đã lỡ tin trong lúc mất kết nối nên gọi lại API
        if (hadConnection) schedule(ALERT_EVENT_DELAY_MS)
        hadConnection = true
      }
    })

    const handleVisibility = () => {
      if (!isHidden() && pending) schedule(0)
    }
    document.addEventListener('visibilitychange', handleVisibility)

    return () => {
      unsubscribeAlerts()
      unsubscribeStore()
      document.removeEventListener('visibilitychange', handleVisibility)
      if (timer) clearTimeout(timer)
    }
  }, [enabled])

  return { summary, summaryError, items, listReady, listLoading, listError, loadedAt, refresh }
}
