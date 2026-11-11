/**
 * Kiểu dữ liệu của ai-service (docs/SAAS_SPEC.md, mục WMS-24.4 và WMS-18.1), khớp các record trong
 * backend/ai-service/src/main/java/vn/wms/ai/dto.
 */

/** `LIVE` là Claude trả lời, `MOCK` là trợ lý giả lập theo quy tắc (chưa bật AI thật). */
export type AiMode = 'LIVE' | 'MOCK'

/** Số lượt AI đã dùng trong tháng (AskResponse.Usage). `max = -1` là không giới hạn, `null` khi chưa đọc được gói. */
export interface AiUsage {
  used: number
  max: number | null
}

/** Body của POST /api/ai/chat (ChatRequest): `message` từ 1 đến 2000 ký tự. */
export interface ChatRequestBody {
  message: string
  /** Mã hội thoại nhận ở câu trả lời trước; bỏ trống để bắt đầu hội thoại mới */
  conversationId?: string
}

/** `data` của POST /api/ai/chat (ChatResponse). */
export interface ChatReply {
  /** Văn bản thường tiếng Việt, có thể có xuống dòng; hiển thị nguyên văn, không render HTML */
  answer: string
  conversationId: string
  /** Hội thoại cũ đã hết hạn hoặc không hợp lệ nên máy chủ mở hội thoại mới */
  newConversation: boolean
  /** Câu trả lời bị cắt vì quá dài */
  truncated: boolean
  /** Trợ lý từ chối trả lời; lượt này không bị tính */
  refused: boolean
  mode: AiMode | string
  /** Các phép tra cứu dữ liệu đã chạy, ví dụ `get_stock`; rỗng khi trả lời từ hướng dẫn sử dụng */
  lookups: string[] | null
  usage: AiUsage | null
}

/** `data` của GET /api/ai/summary (SummaryResponse): bản tóm tắt số liệu điều hành cho dashboard. */
export interface AiSummary {
  /** Toàn văn: các dòng "- " của bullets, rồi "Gợi ý:" và các dòng của actions */
  summary: string
  /** 3 đến 5 ý về các con số đáng chú ý */
  bullets: string[]
  /** 1 đến 2 hành động gợi ý */
  actions: string[]
  /** Thời điểm lập (UTC, ISO 8601); bản lấy từ bộ nhớ đệm giữ nguyên thời điểm lập */
  generatedAt: string
  /** Lấy từ bộ nhớ đệm 30 phút của máy chủ, không tốn lượt AI */
  fromCache: boolean
  mode: AiMode | string
  warehouseId: number | null
  /** Có dùng chỉ số tài chính (chỉ ADMIN, ACCOUNTANT được xem) */
  financialsIncluded: boolean
  usage: AiUsage | null
}

/** `data` của GET /api/ai/usage (AiUsageResponse). */
export interface AiUsageInfo {
  used: number
  max: number | null
  /** Tháng đang đếm, dạng yyyy-MM */
  month: string
  plan: string | null
}
