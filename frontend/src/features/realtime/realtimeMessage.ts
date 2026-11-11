import type { RealtimeMessage, RealtimeMessageType, RealtimeSeverity } from './types'

/** Các kênh STOMP mà client đăng ký, theo hợp đồng với notification-service. */
export const REALTIME_CHANNELS = ['alerts', 'orders', 'inventory', 'kpi'] as const

/**
 * Topic của doanh nghiệp đang đăng nhập: `/topic/company/{companyId}/alerts`, `/orders`, `/inventory`, `/kpi`.
 * Máy chủ chỉ cho đăng ký kênh của đúng doanh nghiệp trong token gửi ở frame CONNECT.
 */
export function realtimeTopics(companyId: number): string[] {
  return REALTIME_CHANNELS.map((channel) => `/topic/company/${companyId}/${channel}`)
}

/** Tiêu đề và mức độ mặc định theo loại tin, chỉ dùng khi backend gửi thiếu trường. */
const MESSAGE_DEFAULTS: Record<RealtimeMessageType, { title: string; severity: RealtimeSeverity }> = {
  LOW_STOCK: { title: 'Cảnh báo tồn kho thấp', severity: 'warning' },
  ORDER_CREATED: { title: 'Đơn hàng mới', severity: 'info' },
  ORDER_DISPATCHED: { title: 'Đơn hàng đã xuất kho', severity: 'success' },
  INVENTORY_UPDATED: { title: 'Cập nhật tồn kho', severity: 'info' },
  KPI_UPDATED: { title: 'Cập nhật KPI', severity: 'info' },
}

const SEVERITIES: readonly RealtimeSeverity[] = ['info', 'success', 'warning', 'error']

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isMessageType(value: unknown): value is RealtimeMessageType {
  return typeof value === 'string' && Object.hasOwn(MESSAGE_DEFAULTS, value)
}

function isSeverity(value: unknown): value is RealtimeSeverity {
  return typeof value === 'string' && (SEVERITIES as readonly string[]).includes(value)
}

/**
 * Đọc nội dung một frame MESSAGE thành phong bì tin thời gian thực.
 * Trả về null nếu không phải JSON hợp lệ, thiếu `id`/`payload` hoặc `type` không thuộc hợp đồng,
 * để tin hỏng bị bỏ qua mà không làm sập ứng dụng.
 */
export function parseRealtimeMessage(body: string): RealtimeMessage | null {
  let data: unknown
  try {
    data = JSON.parse(body)
  } catch {
    return null
  }
  if (!isRecord(data)) return null

  const { id, type, severity, title, message, occurredAt, payload } = data
  if (typeof id !== 'string' || id === '' || !isMessageType(type) || !isRecord(payload)) {
    return null
  }

  const defaults = MESSAGE_DEFAULTS[type]
  // Các trường trong payload có thể null theo hợp đồng; chỉ kiểm tra payload là object,
  // nơi dùng từng trường phải tự xử lý giá trị null.
  return {
    id,
    type,
    severity: isSeverity(severity) ? severity : defaults.severity,
    title: typeof title === 'string' && title !== '' ? title : defaults.title,
    message: typeof message === 'string' ? message : '',
    occurredAt: typeof occurredAt === 'string' ? occurredAt : new Date().toISOString(),
    payload,
  } as unknown as RealtimeMessage
}
