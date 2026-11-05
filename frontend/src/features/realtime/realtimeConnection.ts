import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import type { IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getToken } from '../../utils/storage'
import { fetchKpiSnapshot } from './realtimeApi'
import { parseRealtimeMessage, REALTIME_TOPICS } from './realtimeMessage'
import { useRealtimeStore } from './realtimeStore'
import type { ConnectionStatus, NotifiableMessage, RealtimeMessage } from './types'

/**
 * Quản lý một kết nối SockJS/STOMP duy nhất cho toàn ứng dụng (WMS-02.4).
 *
 * Kết nối nằm ở cấp module thay vì trong từng component để:
 * - React 18 StrictMode chạy effect hai lần ở dev vẫn chỉ có một kết nối (hủy kết nối được hoãn
 *   một nhịp và bị bỏ nếu component gắn lại ngay);
 * - nhiều nơi cùng dùng hook mà không mở thêm kết nối.
 */

/** Endpoint SockJS, cùng origin với trang (Vite proxy ở dev, Nginx ở Docker). */
const WS_URL = import.meta.env.VITE_WS_URL || '/ws-wms'

/** Thời gian chờ lần nối lại đầu tiên; mỗi lần thất bại tiếp theo tăng gấp đôi, tối đa 60 giây. */
const RECONNECT_DELAY_MS = 5000
const MAX_RECONNECT_DELAY_MS = 60000
const HEARTBEAT_MS = 10000

type NotificationListener = (message: NotifiableMessage) => void

let client: Client | null = null
let refCount = 0
let teardownTimer: ReturnType<typeof setTimeout> | null = null
/** Token đã gửi trong frame CONNECT gần nhất */
let tokenInUse: string | null = null
/** Token bị máy chủ từ chối (frame ERROR): không nối lại bằng token này nữa */
let rejectedToken: string | null = null
const listeners = new Set<NotificationListener>()

function setStatus(status: ConnectionStatus): void {
  useRealtimeStore.getState().setStatus(status)
}

/** Lấy số KPI hiện tại qua REST; lỗi (backend chưa triển khai, mất mạng...) thì bỏ qua yên lặng. */
async function loadKpiSnapshot(): Promise<void> {
  try {
    const kpi = await fetchKpiSnapshot()
    if (kpi) useRealtimeStore.getState().setKpi(kpi)
  } catch {
    // Bỏ qua: KPI sẽ được cập nhật khi có tin KPI_UPDATED
  }
}

function handleRealtimeMessage(message: RealtimeMessage): void {
  const store = useRealtimeStore.getState()
  switch (message.type) {
    case 'KPI_UPDATED':
      store.setKpi(message.payload)
      return
    case 'INVENTORY_UPDATED':
      store.setLastInventoryUpdate(message.payload)
      return
    default:
      // Tin trùng `id` (ví dụ backend gửi lại) không thêm vào danh sách và không hiện toast lần nữa
      if (!store.addNotification(message)) return
      listeners.forEach((listener) => {
        try {
          listener(message)
        } catch {
          // Lỗi hiển thị toast không được làm gián đoạn việc nhận tin
        }
      })
  }
}

function handleFrame(frame: IMessage): void {
  const message = parseRealtimeMessage(frame.body)
  if (!message) {
    if (import.meta.env.DEV) {
      console.warn('[realtime] Bỏ qua tin không hợp lệ:', frame.body)
    }
    return
  }
  handleRealtimeMessage(message)
}

function createClient(): Client {
  const stompClient: Client = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    reconnectDelay: RECONNECT_DELAY_MS,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,

    // Chạy trước mỗi lần nối (kể cả nối lại): luôn đọc token mới nhất vì token có thể vừa được làm mới
    beforeConnect: async () => {
      const token = getToken()
      if (!token || token === rejectedToken) {
        // Chưa đăng nhập hoặc token đã bị từ chối: dừng hẳn thay vì nối lại vô hạn với token cũ
        if (client === stompClient) setStatus('disconnected')
        await stompClient.deactivate()
        return
      }
      tokenInUse = token
      stompClient.connectHeaders = { Authorization: `Bearer ${token}` }
    },

    onConnect: () => {
      if (client !== stompClient) return
      rejectedToken = null
      setStatus('connected')
      REALTIME_TOPICS.forEach((topic) => {
        stompClient.subscribe(topic, handleFrame)
      })
      void loadKpiSnapshot()
    },

    // Máy chủ gửi frame ERROR (thường do token sai hoặc hết hạn) rồi đóng kết nối.
    // Không để thư viện tự nối lại vô hạn với token cũ: dừng client và đánh dấu token bị từ chối.
    onStompError: () => {
      if (client !== stompClient) return
      rejectedToken = tokenInUse
      setStatus('disconnected')
      void stompClient.deactivate()
      // Gọi một API REST: nếu token đã hết hạn, interceptor của services/api.ts làm mới token trước khi
      // request hoàn tất (làm mới thất bại thì người dùng bị đưa về trang đăng nhập).
      // Có token mới thì nối lại đúng một lần bằng token đó; vẫn token cũ thì dừng, chờ người dùng bấm "Kết nối lại".
      void loadKpiSnapshot().then(() => {
        const token = getToken()
        if (client === stompClient && token && token !== rejectedToken) {
          setStatus('connecting')
          stompClient.activate()
        }
      })
    },

    onWebSocketClose: () => {
      if (client !== stompClient) return
      // Còn active nghĩa là thư viện sẽ tự nối lại sau RECONNECT_DELAY_MS (tăng dần)
      if (stompClient.active) setStatus('reconnecting')
    },
  })
  return stompClient
}

function start(): void {
  rejectedToken = null
  client = createClient()
  setStatus('connecting')
  client.activate()
}

function stop(): void {
  teardownTimer = null
  const current = client
  client = null
  tokenInUse = null
  void current?.deactivate()
  if (getToken()) {
    setStatus('idle')
  } else {
    // Đã đăng xuất (MainLayout bị gỡ sau khi xóa token): xóa dữ liệu thời gian thực của phiên cũ
    useRealtimeStore.getState().reset()
  }
}

/**
 * Bắt đầu dùng kết nối thời gian thực; trả về hàm trả lại (gọi trong cleanup của effect).
 * Kết nối chỉ đóng khi không còn nơi nào dùng.
 */
export function acquireRealtimeConnection(): () => void {
  refCount += 1
  if (teardownTimer) {
    clearTimeout(teardownTimer)
    teardownTimer = null
  }
  if (!client) start()

  let released = false
  return () => {
    if (released) return
    released = true
    refCount -= 1
    if (refCount === 0) {
      // Hoãn một nhịp: StrictMode gắn lại component ngay sau cleanup sẽ hủy lịch đóng này
      teardownTimer = setTimeout(stop, 0)
    }
  }
}

/** Đăng ký nhận thông báo mới (dùng để hiện toast); trả về hàm hủy đăng ký. */
export function addNotificationListener(listener: NotificationListener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Người dùng chủ động thử kết nối lại sau khi kết nối đã dừng. */
export function reconnectRealtime(): void {
  if (!client || client.active || !getToken()) return
  rejectedToken = null
  setStatus('connecting')
  client.activate()
}
