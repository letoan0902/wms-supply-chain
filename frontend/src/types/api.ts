/**
 * Lỗi validate theo từng trường: { tênTrường: thôngBáo }.
 * Nằm trong `data` của response lỗi có mã VALIDATION_ERROR.
 */
export type FieldErrors = Record<string, string>

/**
 * Khung phản hồi chuẩn của backend (vn.wms.common.api.ApiResponse, xem docs/CONVENTIONS.md).
 * Trường `data` bị bỏ khỏi JSON khi không có dữ liệu.
 */
export interface ApiResponse<T = undefined> {
  success: boolean
  /** "OK" khi thành công, ngược lại là tên mã lỗi (VALIDATION_ERROR, NOT_FOUND, CONFLICT...) */
  code: string
  message: string
  data: T
  timestamp: string
}

/** Response lỗi: `data` là danh sách lỗi theo trường (nếu có). */
export type ApiErrorResponse = ApiResponse<FieldErrors | undefined>

/** Dữ liệu phân trang trả về trong `data`. Chỉ số trang bắt đầu từ 0. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
