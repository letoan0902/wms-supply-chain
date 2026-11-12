import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type {
  AiSummary,
  AiUsageInfo,
  ChatReply,
  ChatRequestBody,
  Extraction,
  ExtractionDataType,
  ImportResult,
  ImportRowBody,
} from './types'

/**
 * REST API của ai-service qua API Gateway (/api/ai/**). Trợ lý, tóm tắt, mức dùng: mọi vai trò đã đăng nhập; trích
 * xuất và nhập dữ liệu: vai trò theo loại dữ liệu (aiImport.* trong features/auth/permissions.ts).
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

/**
 * Trích xuất PDF, ảnh: máy chủ chờ Claude tối đa 180 giây (gọi kiểu stream), nginx chờ /api/ai/ 200 giây; trình
 * duyệt chờ lâu hơn một chút để thấy lỗi của máy chủ trước khi tự dừng.
 */
export const EXTRACTION_TIMEOUT_MS = 210_000

/** Nhập tối đa 200 dòng, mỗi dòng một transaction ở service phía sau. */
export const IMPORT_TIMEOUT_MS = 120_000

/**
 * Tải một file lên để AI trích xuất thành bảng xem trước (POST /api/ai/extractions, multipart `file`, `dataType`).
 * Chưa ghi gì vào danh mục; tính một lượt AI khi thành công. `signal` để người dùng hủy chờ.
 */
export async function extractDocument(
  file: File,
  dataType: ExtractionDataType,
  signal?: AbortSignal,
): Promise<Extraction> {
  const form = new FormData()
  form.append('file', file)
  form.append('dataType', dataType)
  // Bỏ Content-Type mặc định (application/json) để trình duyệt tự đặt multipart/form-data kèm boundary
  const res = await api.post<ApiResponse<Extraction>>('/ai/extractions', form, {
    timeout: EXTRACTION_TIMEOUT_MS,
    signal,
    headers: { 'Content-Type': undefined },
  })
  return res.data.data
}

/** Xem lại kết quả trích xuất trong vòng 1 giờ (GET /api/ai/extractions/{id}); `usage` là `null`. */
export async function fetchExtraction(extractionId: string): Promise<Extraction> {
  const res = await api.get<ApiResponse<Extraction>>(`/ai/extractions/${encodeURIComponent(extractionId)}`)
  return res.data.data
}

/**
 * Nhập các dòng đã sửa và chọn (POST /api/ai/extractions/{id}/import, 1 đến 200 dòng). Không tính lượt AI.
 * `results[k].row` là thứ tự trong `rows` đã gửi (từ 1).
 */
export async function importExtraction(extractionId: string, rows: ImportRowBody[]): Promise<ImportResult> {
  const res = await api.post<ApiResponse<ImportResult>>(
    `/ai/extractions/${encodeURIComponent(extractionId)}/import`,
    { rows },
    { timeout: IMPORT_TIMEOUT_MS },
  )
  return res.data.data
}
