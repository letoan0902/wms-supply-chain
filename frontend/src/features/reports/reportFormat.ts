/**
 * Hàm định dạng thuần (không phụ thuộc React, Ant Design) dùng chung cho màn hình báo cáo và bộ tạo file Excel.
 */

/** Ký hiệu hiển thị cho số liệu không xác định (backend trả null), không bao giờ hiển thị thành 0. */
export const EMPTY_CELL = '—'

/** Số dòng tối đa của một file Excel (bằng giới hạn xuất của backend). */
export const EXPORT_MAX_ROWS = 20000

/** Khoảng ngày tối đa của một kỳ báo cáo, tính cả hai đầu (bằng giới hạn của backend). */
export const MAX_RANGE_DAYS = 366

const quantityFormatter = new Intl.NumberFormat('vi-VN', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 3,
})

/** Chuyển giá trị số từ JSON (number, hoặc chuỗi nếu backend đổi cách ghi BigDecimal) thành number; null nếu không có. */
export function toNumber(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n : null
}

/** Số lượng tối đa 3 chữ số thập phân kiểu Việt Nam (1.234,5); null hiển thị bằng dấu gạch ngang. */
export function formatQuantity(value: number | string | null | undefined): string {
  const n = toNumber(value)
  return n === null ? EMPTY_CELL : quantityFormatter.format(n)
}

/** Ngày YYYY-MM-DD thành dd/MM/yyyy (không đổi múi giờ vì đây là ngày theo lịch). */
export function formatDate(value: string | null | undefined): string {
  if (!value) return EMPTY_CELL
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  return m ? `${m[3]}/${m[2]}/${m[1]}` : value
}

/** Thời điểm ISO-8601 UTC thành giờ địa phương dd/MM/yyyy HH:mm. */
export function formatInstant(value: string | null | undefined): string {
  if (!value) return EMPTY_CELL
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return EMPTY_CELL
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Số ngày của kỳ tính cả hai đầu; ngày dạng YYYY-MM-DD. */
export function inclusiveDays(fromDate: string, toDate: string): number {
  const from = Date.UTC(+fromDate.slice(0, 4), +fromDate.slice(5, 7) - 1, +fromDate.slice(8, 10))
  const to = Date.UTC(+toDate.slice(0, 4), +toDate.slice(5, 7) - 1, +toDate.slice(8, 10))
  return Math.round((to - from) / 86_400_000) + 1
}

/** Kiểm tra kỳ báo cáo như backend; trả thông báo lỗi tiếng Việt hoặc null nếu hợp lệ. */
export function validateRange(fromDate: string | null, toDate: string | null): string | null {
  if (!fromDate || !toDate) return 'Vui lòng chọn đủ ngày đầu kỳ và ngày cuối kỳ.'
  const days = inclusiveDays(fromDate, toDate)
  if (days < 1) return 'Ngày cuối kỳ phải bằng hoặc sau ngày đầu kỳ.'
  if (days > MAX_RANGE_DAYS) return `Kỳ báo cáo tối đa ${MAX_RANGE_DAYS} ngày, kỳ đang chọn có ${days} ngày.`
  return null
}

/** Tên tiếng Việt của loại chứng từ trên thẻ kho và truy vết lô. */
export const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  GOODS_RECEIPT: 'Phiếu nhập kho',
  SALES_ORDER: 'Đơn bán',
  STOCK_TRANSFER: 'Phiếu điều chuyển',
  OPENING_BALANCE: 'Số dư mở sổ',
  PURCHASE_ORDER: 'Đơn mua',
  STOCKTAKE: 'Phiếu kiểm kê',
  OTHER: 'Chứng từ khác',
}

/** Tên tiếng Việt của loại biến động, dùng khi backend không gửi movementTypeLabel. */
export const MOVEMENT_TYPE_LABELS: Record<string, string> = {
  OPENING_BALANCE: 'Số dư mở sổ kho',
  RECEIPT: 'Nhập kho theo phiếu nhập',
  TRANSFER_IN: 'Nhận hàng điều chuyển',
  ADJUSTMENT_IN: 'Điều chỉnh tăng',
  SALES_DISPATCH: 'Xuất bán theo đơn bán',
  TRANSFER_OUT: 'Xuất điều chuyển',
  ADJUSTMENT_OUT: 'Điều chỉnh giảm',
  ISSUE_OTHER: 'Xuất kho khác',
}

export function documentTypeLabel(code: string | null | undefined): string {
  if (!code) return ''
  return DOCUMENT_TYPE_LABELS[code] ?? code
}

export function movementTypeLabel(code: string | null | undefined, label?: string | null): string {
  if (label) return label
  if (!code) return ''
  return MOVEMENT_TYPE_LABELS[code] ?? code
}

/** Nhãn sản phẩm "SKU - Tên"; thiếu cả hai thì dùng ID sản phẩm. */
export function productLabel(skuCode: string | null | undefined, productName: string | null | undefined, productId: number): string {
  const parts = [skuCode, productName].filter((p): p is string => Boolean(p))
  return parts.length > 0 ? parts.join(' - ') : `Sản phẩm ID ${productId}`
}

/** Nhãn kho "Mã - Tên"; thiếu cả hai thì dùng ID kho. */
export function warehouseLabel(code: string | null | undefined, name: string | null | undefined, id: number | null | undefined): string {
  const parts = [code, name].filter((p): p is string => Boolean(p))
  if (parts.length > 0) return parts.join(' - ')
  return id === null || id === undefined ? '' : `Kho ID ${id}`
}

/** Tên file an toàn cho mọi hệ điều hành (bỏ dấu tiếng Việt, ký tự đặc biệt thay bằng gạch dưới). */
export function safeFileName(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .replace(/[^A-Za-z0-9._-]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
}
