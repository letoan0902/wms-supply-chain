/**
 * Hàm thuần (không phụ thuộc React) dựng tham số truy vấn cho các API danh sách của quản trị nền tảng:
 * bỏ tham số rỗng để máy chủ dùng giá trị mặc định, cắt khoảng trắng của từ khóa.
 */

/** Số dòng mỗi trang mặc định, cùng mặc định `size` của máy chủ. */
export const PLATFORM_PAGE_SIZE = 10

/** Bộ lọc danh sách doanh nghiệp; `page` tính từ 0. */
export interface CompanyFilters {
  keyword?: string
  status?: string
  plan?: string
  page: number
  size: number
}

/** Bộ lọc danh sách thanh toán; `from`, `to` là ngày `yyyy-MM-dd` theo giờ Việt Nam (lọc theo ngày tạo, gồm hai đầu). */
export interface PaymentFilters {
  status?: string
  companyId?: number
  from?: string
  to?: string
  page: number
  size: number
}

type QueryValue = string | number

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

function put(target: Record<string, QueryValue>, key: string, value: string | number | null | undefined): void {
  if (value === null || value === undefined) return
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (trimmed) target[key] = trimmed
    return
  }
  if (Number.isFinite(value)) target[key] = value
}

/** Tham số của GET /api/auth/platform/companies. */
export function buildCompanyQuery(filters: CompanyFilters): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = { page: Math.max(0, filters.page), size: filters.size }
  put(query, 'keyword', filters.keyword)
  put(query, 'status', filters.status)
  put(query, 'plan', filters.plan)
  return query
}

/** Tham số của GET /api/auth/platform/payments; ngày sai định dạng `yyyy-MM-dd` bị bỏ qua. */
export function buildPaymentQuery(filters: PaymentFilters): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = { page: Math.max(0, filters.page), size: filters.size }
  put(query, 'status', filters.status)
  put(query, 'companyId', filters.companyId)
  if (filters.from && ISO_DATE.test(filters.from)) query.from = filters.from
  if (filters.to && ISO_DATE.test(filters.to)) query.to = filters.to
  return query
}
