import axios from 'axios'
import { getApiErrorMessage, isHandledByGlobalDialog } from '../../services/api'
import type { ApiErrorResponse } from '../../types/api'

/** Mã lỗi (`code`) trong phản hồi lỗi của backend, ví dụ FORBIDDEN, PLAN_LIMIT_EXCEEDED. */
export function getAiErrorCode(error: unknown): string | undefined {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.code
  }
  return undefined
}

/** Request bị hủy vì quá thời gian chờ của trình duyệt (máy chủ có thể vẫn đang xử lý). */
export function isTimeoutError(error: unknown): boolean {
  return axios.isAxiosError(error) && !error.response && (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT')
}

/**
 * Thông báo lỗi của trợ lý để hiện trong khung chat, `null` khi lỗi đã có hộp thoại gói dịch vụ dùng chung
 * (hết lượt AI, gói hết hạn) nên không báo lần hai.
 */
export function describeAiError(error: unknown, fallback: string): string | null {
  if (isHandledByGlobalDialog(error)) return null
  if (isTimeoutError(error)) {
    return 'Quá thời gian chờ câu trả lời. Bạn có thể gửi lại câu hỏi.'
  }
  return getApiErrorMessage(error, fallback) || fallback
}
