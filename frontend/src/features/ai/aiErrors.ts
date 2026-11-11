import axios from 'axios'
import {
  getApiErrorMessage,
  isHandledByGlobalDialog,
  PLAN_LIMIT_EXCEEDED,
  SUBSCRIPTION_EXPIRED,
} from '../../services/api'
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

/** Cách khối tóm tắt bằng AI xử lý một lỗi: ẩn khối, hoặc báo nhẹ trong khối. */
export type SummaryErrorView =
  | { kind: 'hidden' }
  | { kind: 'planLimit'; message: string }
  | { kind: 'message'; message: string }

/**
 * Lỗi của GET /api/ai/summary: 403 (vai trò, kho không được xem KPI, thiếu doanh nghiệp) ẩn khối; hết lượt AI báo
 * trong khối; còn lại hiện thông báo của máy chủ.
 */
export function describeSummaryError(error: unknown): SummaryErrorView {
  const code = getAiErrorCode(error)
  if (code === PLAN_LIMIT_EXCEEDED) {
    return { kind: 'planLimit', message: 'Đã dùng hết lượt AI của tháng theo gói hiện tại nên chưa tạo được bản tóm tắt.' }
  }
  if (axios.isAxiosError(error) && error.response?.status === 403 && code !== SUBSCRIPTION_EXPIRED) {
    return { kind: 'hidden' }
  }
  if (isTimeoutError(error)) {
    return { kind: 'message', message: 'Tạo bản tóm tắt quá lâu nên đã dừng chờ. Bạn có thể thử lại sau.' }
  }
  return { kind: 'message', message: getApiErrorMessage(error, 'Chưa tải được bản tóm tắt bằng AI.') || 'Chưa tải được bản tóm tắt bằng AI.' }
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
