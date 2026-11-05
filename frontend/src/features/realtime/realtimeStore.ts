import { create } from 'zustand'
import type {
  ConnectionStatus,
  InventoryUpdatedPayload,
  KpiSnapshot,
  NotifiableMessage,
  RealtimeNotification,
} from './types'

/** Số thông báo tối đa giữ trong danh sách gần đây. */
export const MAX_NOTIFICATIONS = 50

interface RealtimeState {
  status: ConnectionStatus
  /** Thông báo gần đây, mới nhất đứng đầu, không trùng `id` */
  notifications: RealtimeNotification[]
  /** Số thông báo chưa đọc trong danh sách */
  unreadCount: number
  /** Snapshot KPI mới nhất (từ REST lúc vừa nối hoặc từ tin KPI_UPDATED) */
  kpi: KpiSnapshot | null
  /** Biến động tồn kho gần nhất (INVENTORY_UPDATED), dành cho các màn hình cần làm mới số liệu */
  lastInventoryUpdate: InventoryUpdatedPayload | null

  setStatus: (status: ConnectionStatus) => void
  /** Thêm thông báo; trả về false nếu `id` đã có (tin trùng, không cần hiện toast lại). */
  addNotification: (message: NotifiableMessage) => boolean
  markAllAsRead: () => void
  clearNotifications: () => void
  setKpi: (kpi: KpiSnapshot) => void
  setLastInventoryUpdate: (payload: InventoryUpdatedPayload) => void
  /** Xóa toàn bộ dữ liệu khi đăng xuất. */
  reset: () => void
}

const initialState = {
  status: 'idle' as ConnectionStatus,
  notifications: [] as RealtimeNotification[],
  unreadCount: 0,
  kpi: null as KpiSnapshot | null,
  lastInventoryUpdate: null as InventoryUpdatedPayload | null,
}

function countUnread(notifications: RealtimeNotification[]): number {
  return notifications.reduce((total, item) => (item.read ? total : total + 1), 0)
}

export const useRealtimeStore = create<RealtimeState>()((set, get) => ({
  ...initialState,

  setStatus: (status) => set({ status }),

  addNotification: (message) => {
    if (get().notifications.some((item) => item.id === message.id)) {
      return false
    }
    const notification: RealtimeNotification = {
      id: message.id,
      type: message.type,
      severity: message.severity,
      title: message.title,
      message: message.message,
      occurredAt: message.occurredAt,
      read: false,
    }
    const notifications = [notification, ...get().notifications].slice(0, MAX_NOTIFICATIONS)
    set({ notifications, unreadCount: countUnread(notifications) })
    return true
  },

  markAllAsRead: () =>
    set((state) => ({
      notifications: state.notifications.map((item) => (item.read ? item : { ...item, read: true })),
      unreadCount: 0,
    })),

  clearNotifications: () => set({ notifications: [], unreadCount: 0 }),

  setKpi: (kpi) => set({ kpi }),

  setLastInventoryUpdate: (payload) => set({ lastInventoryUpdate: payload }),

  reset: () => set(initialState),
}))
