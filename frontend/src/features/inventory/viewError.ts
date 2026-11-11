import { getApiErrorMessage, getErrorStatus } from '../../services/api'

/** Lỗi của một bảng, giữ mã HTTP để hiển thị đúng cách (403, 404, 503...). */
export interface ViewError {
  status: number | undefined
  message: string
}

/** Chuyển lỗi gọi API thành lỗi hiển thị, ưu tiên thông báo tiếng Việt của backend. */
export function toViewError(error: unknown, fallback: string): ViewError {
  const status = getErrorStatus(error)
  let message = getApiErrorMessage(error, fallback)
  if (status === 403 && message === fallback) {
    message = 'Tài khoản không có quyền xem dữ liệu này hoặc kho đã chọn không thuộc phạm vi được phân công.'
  } else if (status === 404 && message === fallback) {
    message = 'Không tìm thấy dữ liệu (kho có thể đã bị xóa hoặc không thuộc doanh nghiệp).'
  } else if (status === 503 && message === fallback) {
    message = 'Dịch vụ tạm thời không sẵn sàng, vui lòng thử lại sau.'
  }
  return { status, message }
}

/** Tiêu đề ngắn theo mã lỗi. */
export function errorTitle(status: number | undefined): string {
  switch (status) {
    case 401:
      return 'Phiên đăng nhập đã hết hạn'
    case 403:
      return 'Không có quyền xem'
    case 404:
      return 'Không tìm thấy'
    case 503:
      return 'Dịch vụ tạm thời không sẵn sàng'
    default:
      return 'Không tải được dữ liệu'
  }
}
