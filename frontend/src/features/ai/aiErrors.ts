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

/** Người dùng tự hủy request (AbortController). */
export function isCanceledError(error: unknown): boolean {
  return axios.isCancel(error)
}

/** `data` của phản hồi lỗi (ví dụ danh sách ô lỗi của VALIDATION_ERROR), `undefined` khi không có. */
export function getAiErrorData(error: unknown): Record<string, string> | undefined {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const data = error.response?.data?.data
    return data && typeof data === 'object' ? data : undefined
  }
  return undefined
}

/**
 * Thông báo lỗi khi tải file để trích xuất: hiện nguyên thông báo tiếng Việt của máy chủ (file không đọc được, không có
 * cột phù hợp, chế độ giả lập không đọc PDF, ảnh...); `null` khi lỗi đã có hộp thoại gói dịch vụ dùng chung.
 */
export function describeExtractionError(error: unknown): string | null {
  if (isHandledByGlobalDialog(error)) return null
  if (isTimeoutError(error)) {
    return 'Quá thời gian chờ kết quả trích xuất. Hãy thử lại, hoặc tách file thành nhiều file nhỏ hơn.'
  }
  return getApiErrorMessage(error, 'Không trích xuất được dữ liệu từ file.') || 'Không trích xuất được dữ liệu từ file.'
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
