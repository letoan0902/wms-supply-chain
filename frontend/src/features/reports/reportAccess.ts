import axios from 'axios'
import { getApiErrorMessage, getErrorStatus } from '../../services/api'
import type { AuthUser } from '../../types/auth'

/**
 * Phân quyền xem báo cáo kho, khớp với ReportAccess của inventory-service:
 * ADMIN, ACCOUNTANT xem mọi kho của doanh nghiệp; WAREHOUSE_MANAGER, WAREHOUSE_STAFF chỉ xem kho được gán.
 * Vai trò khác bị backend trả 403 nên màn hình hiển thị trang 403 thay vì gọi API.
 */
export const REPORT_ROLES = ['ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] as const

export interface ReportAccessInfo {
  canView: boolean
  /** true: xem mọi kho của doanh nghiệp; false: chỉ các kho được gán */
  allWarehouses: boolean
}

export function getReportAccess(user: AuthUser | null): ReportAccessInfo {
  const roles = user?.roles ?? []
  const canView = REPORT_ROLES.some((r) => roles.includes(r))
  const allWarehouses = roles.includes('ADMIN') || roles.includes('ACCOUNTANT')
  return { canView, allWarehouses }
}

export interface ReportErrorInfo {
  status?: number
  title: string
  description: string
}

/** Diễn giải lỗi API báo cáo thành tiêu đề và mô tả tiếng Việt theo mã HTTP (thông báo chi tiết lấy từ backend). */
export function describeReportError(error: unknown, fallback = 'Không tải được báo cáo.'): ReportErrorInfo {
  const status = getErrorStatus(error)
  const detail = getApiErrorMessage(error, fallback)
  if (axios.isAxiosError(error) && !error.response) {
    return { title: 'Không kết nối được máy chủ', description: detail }
  }
  switch (status) {
    case 400:
      return { status, title: 'Điều kiện lọc không hợp lệ', description: detail }
    case 401:
      return { status, title: 'Phiên đăng nhập đã hết hạn', description: 'Vui lòng đăng nhập lại để xem báo cáo.' }
    case 403:
      return { status, title: 'Không có quyền xem dữ liệu này', description: detail }
    case 404:
      return { status, title: 'Không tìm thấy dữ liệu', description: detail }
    case 503:
      return { status, title: 'Dịch vụ tạm thời không phản hồi', description: detail }
    default:
      if (error instanceof Error && !axios.isAxiosError(error)) {
        return { title: fallback, description: error.message }
      }
      return { status, title: fallback, description: detail }
  }
}

/** Thông báo ngắn cho một lỗi (dùng với message.error). */
export function reportErrorMessage(error: unknown, fallback: string): string {
  const info = describeReportError(error, fallback)
  return info.description && info.description !== info.title ? `${info.title}: ${info.description}` : info.title
}
