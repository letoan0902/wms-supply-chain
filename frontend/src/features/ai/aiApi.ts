import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { AiUsageInfo, ChatReply, ChatRequestBody } from './types'

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

/** Số lượt AI doanh nghiệp đã dùng trong tháng và giới hạn của gói. */
export async function fetchAiUsage(): Promise<AiUsageInfo> {
  const res = await api.get<ApiResponse<AiUsageInfo>>('/ai/usage')
  return res.data.data
}
