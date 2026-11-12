/**
 * Kiểu dữ liệu của ai-service (docs/SAAS_SPEC.md, mục WMS-24.2, WMS-24.4 và WMS-18.1), khớp các record trong
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

/** Loại dữ liệu danh mục nhập được bằng AI (ExtractionDataType). */
export type ExtractionDataType = 'PRODUCT' | 'SUPPLIER' | 'CUSTOMER'

/** Kiểu giá trị của một cột xem trước (ExtractionColumn.type). */
export type ExtractionFieldType = 'TEXT' | 'SKU' | 'EMAIL' | 'NUMBER' | 'PERCENT' | 'ENUM'

/** Giá trị một ô: trường số là số JSON, ô số không đọc được giữ chuỗi gốc, ô trống là `null`. */
export type CellValue = string | number | null

/** Một cột của bảng xem trước (ExtractionColumn), tương ứng một trường của API nhập hàng loạt. */
export interface ExtractionColumn {
  field: string
  label: string
  type: ExtractionFieldType | string
  required: boolean
  /** Độ dài tối đa của trường chuỗi, `null` nếu không giới hạn */
  maxLength: number | null
  /** Giá trị hợp lệ của trường ENUM (hiện chỉ `customerType`), rỗng với kiểu khác */
  options: string[]
  /** Cột trong file (từ 1) đã ghép vào trường này; `null` khi không có cột khớp hoặc AI đọc PDF, ảnh */
  sourceColumn: number | null
  sourceHeader: string | null
  /** Độ tin cậy ghép cột, 0 đến 1 */
  mappingConfidence: number | null
}

/** Một dòng của bảng xem trước (ExtractionRow). */
export interface ExtractionRow {
  /** Số thứ tự dòng trong kết quả trích xuất, từ 1 */
  row: number
  /** Số dòng trong file Excel, CSV; `null` với PDF, ảnh */
  sourceRow: number | null
  values: Record<string, CellValue>
  /** Độ tin cậy từng ô, 0 đến 1 */
  confidence: Record<string, number | null>
  warnings: string[]
}

/** `data` của POST /api/ai/extractions và GET /api/ai/extractions/{id} (ExtractionResponse). */
export interface Extraction {
  extractionId: string
  dataType: ExtractionDataType
  fileName: string
  fileType: string
  /** `COLUMN_MAPPING`, `HEADER_MATCHING` (chế độ giả lập) hoặc `AI_DOCUMENT` (AI đọc PDF, ảnh) */
  method: string
  /** Số dòng dữ liệu tìm thấy trong file (có thể lớn hơn số dòng trả về) */
  totalRows: number
  /** File có hơn 200 dòng, chỉ 200 dòng đầu được lấy */
  truncated: boolean
  columns: ExtractionColumn[]
  rows: ExtractionRow[]
  warnings: string[]
  createdAt: string
  /** Sau thời điểm này không xem lại, không nhập được nữa (404) */
  expiresAt: string
  /** Chỉ có trong kết quả của POST, `null` khi xem lại */
  usage: AiUsage | null
}

/** Một dòng gửi POST /api/ai/extractions/{id}/import (ImportRequest.Row). */
export interface ImportRowBody {
  /** Số dòng của bảng xem trước, chỉ để đối chiếu */
  row: number
  values: Record<string, string | number>
}

export type ImportRowStatus = 'CREATED' | 'SKIPPED' | 'FAILED'

/** Kết quả một dòng (BulkImportResult.RowResult). */
export interface ImportRowResult {
  /** Thứ tự trong mảng `rows` đã gửi (từ 1), không phải số dòng của bảng xem trước */
  row: number
  status: ImportRowStatus | string
  id: number | null
  code: string | null
  message: string | null
}

/** `data` của POST /api/ai/extractions/{id}/import (BulkImportResult). */
export interface ImportResult {
  total: number
  created: number
  skipped: number
  failed: number
  results: ImportRowResult[]
}

/** `data` của GET /api/ai/usage (AiUsageResponse). */
export interface AiUsageInfo {
  used: number
  max: number | null
  /** Tháng đang đếm, dạng yyyy-MM */
  month: string
  plan: string | null
}
