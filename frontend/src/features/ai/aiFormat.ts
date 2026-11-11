import type { AiUsage } from './types'

/** Độ dài tối đa của một câu hỏi (ChatRequest.MAX_MESSAGE_LENGTH ở ai-service). */
export const MAX_MESSAGE_LENGTH = 2000

/** Câu hỏi gợi ý khi chưa có tin nhắn; phần "..." là chỗ người dùng điền mã. */
export const SUGGESTED_QUESTIONS = [
  'Tạo kho thế nào?',
  'Tồn của SKU ... còn bao nhiêu?',
  'Đơn bán SO-... đang ở trạng thái nào?',
]

/** Tên tiếng Việt của các phép tra cứu trợ lý báo trong `lookups` (công cụ của AiChatService). */
const LOOKUP_LABELS: Record<string, string> = {
  find_product: 'tìm hàng hóa',
  get_stock: 'tồn theo mã SKU',
  get_warehouse_stock: 'tồn theo kho',
  list_warehouses: 'danh sách kho',
  get_purchase_order: 'đơn mua',
  get_sales_order: 'đơn bán',
}

/**
 * Mức dùng AI trong tháng: "8/500", "8/không giới hạn" khi `max = -1`, chỉ số đã dùng khi chưa biết giới hạn.
 * Trả `null` khi không có dữ liệu.
 */
export function formatUsage(usage: AiUsage | null | undefined): string | null {
  if (!usage || typeof usage.used !== 'number') return null
  if (usage.max === -1) return `${usage.used}/không giới hạn`
  if (typeof usage.max === 'number') return `${usage.used}/${usage.max}`
  return `${usage.used}`
}

/** Các phép tra cứu đã chạy, đổi sang tiếng Việt, bỏ trùng, giữ thứ tự; tên lạ giữ nguyên. */
export function lookupLabels(lookups: readonly string[] | null | undefined): string[] {
  if (!lookups) return []
  const labels: string[] = []
  for (const name of lookups) {
    if (typeof name !== 'string' || name.trim() === '') continue
    const label = LOOKUP_LABELS[name] ?? name
    if (!labels.includes(label)) labels.push(label)
  }
  return labels
}

/** Nội dung gửi đi (đã bỏ khoảng trắng hai đầu), `null` khi trống hoặc quá dài. */
export function normalizeQuestion(text: string): string | null {
  const question = text.trim()
  if (question === '' || question.length > MAX_MESSAGE_LENGTH) return null
  return question
}

/** Vị trí đoạn "..." đầu tiên trong câu gợi ý để chọn sẵn cho người dùng gõ đè, `null` nếu không có. */
export function placeholderRange(text: string): [number, number] | null {
  const start = text.indexOf('...')
  return start < 0 ? null : [start, start + 3]
}
