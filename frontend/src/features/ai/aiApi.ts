import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { AiSummary, AiUsageInfo, ChatReply, ChatRequestBody } from './types'

/**
 * REST API của ai-service qua API Gateway (/api/ai/**). Mọi vai trò đã đăng nhập đều gọi được.
 */

/**
 * Một câu hỏi có thể mất tới 90 giây ở máy chủ (app.ai.assistant.timeout); axios dùng chung chỉ chờ 15 giây nên
 * lời gọi này đặt thời gian chờ riêng, dư thêm cho mạng (nginx chờ /api/ai/ 200 giây).
 */
export const CHAT_TIMEOUT_MS = 120_000

/** Gửi một câu hỏi cho trợ lý AI. Tính một lượt AI khi có câu trả lời (câu bị từ chối không tính). */
export async function sendChatMessage(body: ChatRequestBody): Promise<ChatReply> {
  const res = await api.post<ApiResponse<ChatReply>>('/ai/chat', body, { timeout: CHAT_TIMEOUT_MS })
  return res.data.data
}

/** Bản tóm tắt chờ tối đa 60 giây ở máy chủ (app.ai.summary.timeout) cộng thời gian lấy KPI và báo cáo. */
export const SUMMARY_TIMEOUT_MS = 90_000

/**
 * Bản tóm tắt số liệu điều hành bằng AI cho dashboard. Máy chủ lưu 30 phút; `refresh = true` tạo bản mới và tốn
 * một lượt AI. Hết lượt AI thì khối tự báo, không mở hộp thoại dùng chung (khối tự tải khi mở dashboard).
 *
 * @param warehouseId kho đang chọn trên dashboard, `null` là phạm vi mặc định của người xem
 */
export async function fetchAiSummary(warehouseId: number | null, refresh = false): Promise<AiSummary> {
  const params: Record<string, string | number | boolean> = {}
  if (warehouseId !== null) params.warehouseId = warehouseId
  if (refresh) params.refresh = true
  const res = await api.get<ApiResponse<AiSummary>>('/ai/summary', {
    params,
    timeout: SUMMARY_TIMEOUT_MS,
    skipPlanLimitDialog: true,
  })
  return res.data.data
}

/** Số lượt AI doanh nghiệp đã dùng trong tháng và giới hạn của gói. */
export async function fetchAiUsage(): Promise<AiUsageInfo> {
  const res = await api.get<ApiResponse<AiUsageInfo>>('/ai/usage')
  return res.data.data
}
