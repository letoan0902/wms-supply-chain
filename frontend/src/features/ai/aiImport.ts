import type { Capability } from '../auth/permissions'
import type {
  CellValue,
  ExtractionColumn,
  ExtractionDataType,
  ExtractionRow,
  ImportResult,
  ImportRowBody,
  ImportRowResult,
} from './types'

/**
 * Logic thuần của màn hình "Nhập dữ liệu bằng AI" (không phụ thuộc React, axios): kiểm tra file, kiểm tra và chuẩn hóa
 * ô đã sửa, dựng body nhập, đối chiếu kết quả nhập với dòng của bảng xem trước. Hợp đồng ở docs/SAAS_SPEC.md, mục
 * WMS-24.2.
 */

/** Ô có độ tin cậy dưới ngưỡng này được tô vàng (docs/SAAS_SPEC.md, mục WMS-24.3; ExtractionRow ở ai-service). */
export const LOW_CONFIDENCE_THRESHOLD = 0.8

/** Dung lượng file tối đa (ExtractionLimits.MAX_FILE_BYTES). */
export const MAX_FILE_BYTES = 10 * 1024 * 1024

/** Số dòng tối đa của một lần nhập (ImportRequest). */
export const MAX_IMPORT_ROWS = 200

/** Phần mở rộng được nhận (UploadedFileType). */
export const ACCEPTED_EXTENSIONS = ['xlsx', 'xls', 'csv', 'pdf', 'png', 'jpg', 'jpeg'] as const

/** Giá trị `accept` của ô chọn file. */
export const ACCEPT_ATTRIBUTE = ACCEPTED_EXTENSIONS.map((ext) => `.${ext}`).join(',')

export interface DataTypeInfo {
  /** Tên hiển thị, ví dụ "Hàng hóa" */
  label: string
  /** Viết thường để ghép câu, ví dụ "hàng hóa" */
  noun: string
  /** Quyền dùng loại dữ liệu này (khớp ExtractionDataType.allowedRoles) */
  capability: Capability
  /** Trang danh mục tương ứng */
  catalogPath: string
  /** Viết thường để ghép câu, ví dụ "danh mục hàng hóa" */
  catalogLabel: string
  /** Gợi ý cột nên có trong file */
  hint: string
}

export const DATA_TYPES: Record<ExtractionDataType, DataTypeInfo> = {
  PRODUCT: {
    label: 'Hàng hóa',
    noun: 'hàng hóa',
    capability: 'aiImport.productSupplier',
    catalogPath: '/products',
    catalogLabel: 'danh mục hàng hóa',
    hint: 'Bắt buộc có Mã SKU, Tên hàng hóa, Đơn vị tính; nên có thêm mã vạch, danh mục, giá nhập, giá bán, thuế suất, mức tồn.',
  },
  SUPPLIER: {
    label: 'Nhà cung cấp',
    noun: 'nhà cung cấp',
    capability: 'aiImport.productSupplier',
    catalogPath: '/suppliers',
    catalogLabel: 'danh sách nhà cung cấp',
    hint: 'Bắt buộc có Mã và Tên nhà cung cấp; nên có thêm mã số thuế, người liên hệ, email, điện thoại, địa chỉ, tài khoản ngân hàng.',
  },
  CUSTOMER: {
    label: 'Khách hàng',
    noun: 'khách hàng',
    capability: 'aiImport.customer',
    catalogPath: '/customers',
    catalogLabel: 'danh sách khách hàng',
    hint: 'Bắt buộc có Mã, Tên khách hàng và Loại khách hàng (sỉ hoặc lẻ); nên có thêm mã số thuế, liên hệ, địa chỉ, hạn mức công nợ.',
  },
}

export const DATA_TYPE_ORDER: ExtractionDataType[] = ['PRODUCT', 'SUPPLIER', 'CUSTOMER']

/** Nhãn của giá trị ENUM (loại khách hàng), giống màn hình Khách hàng. */
export const OPTION_LABELS: Record<string, string> = {
  WHOLESALE: 'Khách sỉ / Đại lý',
  RETAIL: 'Khách lẻ',
}

export function isDataType(value: string | null | undefined): value is ExtractionDataType {
  return value === 'PRODUCT' || value === 'SUPPLIER' || value === 'CUSTOMER'
}

/** Kiểm tra file trước khi tải lên: trả thông báo lỗi tiếng Việt, `null` khi hợp lệ. */
export function checkFile(file: { name: string; size: number }): string | null {
  const dot = file.name.lastIndexOf('.')
  const extension = dot >= 0 ? file.name.slice(dot + 1).toLowerCase() : ''
  if (!(ACCEPTED_EXTENSIONS as readonly string[]).includes(extension)) {
    return 'Định dạng file không được hỗ trợ. Chỉ nhận xlsx, xls, csv, pdf, png, jpg.'
  }
  if (file.size <= 0) return 'File rỗng, hãy chọn file khác.'
  if (file.size > MAX_FILE_BYTES) return 'File vượt quá dung lượng cho phép (tối đa 10 MB).'
  return null
}

/** Dung lượng dễ đọc: "850 KB", "2,4 MB". */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} MB`
}

// Đọc số theo đúng quy tắc NumberParser của ai-service để giao diện và máy chủ hiểu giống nhau.
const CURRENCY = /(vn[dđ]|[đ₫])/giu
// Lớp \s của JavaScript đã gồm cả khoảng trắng không ngắt dòng (NBSP, NNBSP) như SPACES của NumberParser
const SPACES = /\s+/g
const NUMBER_CHARS = /^[+-]?[0-9.,]*[0-9][0-9.,]*$/
const SCIENTIFIC = /^[+-]?[0-9]+(\.[0-9]+)?[eE][+-]?[0-9]+$/
const THOUSANDS_GROUPS = /^[+-]?[0-9]{1,3}(?:[.,][0-9]{3})+$/

function normalizeSeparators(text: string): string | null {
  const lastDot = text.lastIndexOf('.')
  const lastComma = text.lastIndexOf(',')
  if (lastDot >= 0 && lastComma >= 0) {
    const decimal = lastDot > lastComma ? '.' : ','
    const grouping = decimal === '.' ? ',' : '.'
    const cut = Math.max(lastDot, lastComma)
    const integerPart = text.slice(0, cut)
    const fraction = text.slice(cut + 1)
    if (integerPart.includes(decimal) || fraction.includes(grouping) || !THOUSANDS_GROUPS.test(integerPart)) {
      return null
    }
    return `${integerPart.split(grouping).join('')}.${fraction}`
  }
  const separator = lastDot >= 0 ? '.' : lastComma >= 0 ? ',' : ''
  if (separator === '') return text
  const count = text.split(separator).length - 1
  if (count > 1) {
    return THOUSANDS_GROUPS.test(text) ? text.split(separator).join('') : null
  }
  const index = text.indexOf(separator)
  const integerPart = text.slice(0, index)
  const fraction = text.slice(index + 1)
  const digits = integerPart.replace(/[+-]/g, '')
  if (fraction.length === 3 && digits !== '' && !/^0+$/.test(digits)) return integerPart + fraction
  if (fraction === '') return integerPart === '' ? null : integerPart
  return `${digits === '' ? `${integerPart}0` : integerPart}.${fraction}`
}

/**
 * Đọc số viết kiểu Việt Nam hoặc quốc tế ("1.250.000", "1,250,000", "12,5", "150.000 đ", "10%", "(1.000)").
 * Không phải số thì `null`.
 */
export function parseNumber(raw: string): number | null {
  let text = raw.replace(CURRENCY, '').replace(SPACES, '')
  if (text === '') return null
  if (text.endsWith('%')) text = text.slice(0, -1)
  let negative = false
  if (text.startsWith('(') && text.endsWith(')') && text.length > 2) {
    negative = true
    text = text.slice(1, -1)
  }
  let normalized: string | null
  if (SCIENTIFIC.test(text)) {
    normalized = text
  } else {
    if (!NUMBER_CHARS.test(text)) return null
    normalized = normalizeSeparators(text)
  }
  if (normalized === null) return null
  const value = Number(normalized)
  if (!Number.isFinite(value)) return null
  return negative ? -value : value
}

export function isNumericColumn(column: Pick<ExtractionColumn, 'type'>): boolean {
  return column.type === 'NUMBER' || column.type === 'PERCENT'
}

export function isEmptyValue(value: CellValue | undefined): boolean {
  return value === null || value === undefined || (typeof value === 'string' && value.trim() === '')
}

/** Giá trị ENUM khớp một lựa chọn (không phân biệt hoa thường), không khớp thì `null`. */
function matchOption(options: readonly string[], value: CellValue): string | null {
  if (value === null) return null
  const text = String(value).trim().toUpperCase()
  return options.find((option) => option.toUpperCase() === text) ?? null
}

/**
 * Chuẩn hóa nội dung người dùng vừa sửa trong một ô: trống thành `null`; ô số đọc được thành số (không đọc được giữ
 * chuỗi để báo lỗi); ô ENUM khớp lựa chọn thành mã; ô chữ bỏ khoảng trắng hai đầu.
 */
export function coerceCellInput(column: ExtractionColumn, input: string): CellValue {
  const text = input.trim()
  if (text === '') return null
  if (isNumericColumn(column)) return parseNumber(text) ?? text
  if (column.type === 'ENUM' && column.options.length > 0) return matchOption(column.options, text) ?? text
  return text
}

/** Nội dung ban đầu của ô khi bắt đầu sửa. */
export function cellDraft(value: CellValue | undefined): string {
  if (value === null || value === undefined) return ''
  return String(value)
}

/** Hiển thị giá trị một ô trong bảng: số theo kiểu Việt Nam, ENUM theo nhãn. */
export function formatCell(column: ExtractionColumn, value: CellValue | undefined): string {
  if (isEmptyValue(value)) return ''
  if (typeof value === 'number' && isNumericColumn(column)) {
    return value.toLocaleString('vi-VN', { maximumFractionDigits: 3 })
  }
  if (column.type === 'ENUM') {
    const option = matchOption(column.options, value ?? null)
    if (option) return OPTION_LABELS[option] ?? option
  }
  return String(value)
}

/**
 * Lỗi của một dòng trước khi nhập, theo trường: thiếu trường bắt buộc, ô số không đọc được, giá trị ngoài lựa chọn,
 * vượt độ dài tối đa. Dòng có lỗi không chọn được để nhập. Các kiểm tra khác (mẫu SKU, email, trùng mã) do API nhập
 * hàng loạt làm cho từng dòng.
 */
export function validateRow(
  columns: readonly ExtractionColumn[],
  values: Record<string, CellValue>,
): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const column of columns) {
    const value = values[column.field]
    if (isEmptyValue(value)) {
      if (column.required) errors[column.field] = `Thiếu ${column.label} (bắt buộc)`
      continue
    }
    if (isNumericColumn(column)) {
      if (typeof value !== 'number' && parseNumber(String(value)) === null) {
        errors[column.field] = `${column.label} phải là số`
      }
      continue
    }
    if (column.type === 'ENUM' && column.options.length > 0) {
      if (matchOption(column.options, value ?? null) === null) {
        errors[column.field] = `${column.label} phải là một trong: ${column.options
          .map((option) => OPTION_LABELS[option] ?? option)
          .join(', ')}`
      }
      continue
    }
    if (column.maxLength !== null && column.maxLength > 0 && String(value).trim().length > column.maxLength) {
      errors[column.field] = `${column.label} tối đa ${column.maxLength} ký tự`
    }
  }
  return errors
}

/** Dòng của bảng xem trước ở giao diện: dữ liệu của máy chủ cộng các ô người dùng đã sửa. */
export interface PreviewRow extends ExtractionRow {
  /** Các trường người dùng đã sửa (không tô vàng nữa) */
  edited: string[]
  /** Lỗi của lần nhập trước (dòng FAILED được đưa về bảng để sửa) */
  lastError: string | null
}

export function toPreviewRows(rows: readonly ExtractionRow[]): PreviewRow[] {
  return rows.map((row) => ({
    row: row.row,
    sourceRow: row.sourceRow ?? null,
    values: { ...(row.values ?? {}) },
    confidence: { ...(row.confidence ?? {}) },
    warnings: Array.isArray(row.warnings) ? row.warnings : [],
    edited: [],
    lastError: null,
  }))
}

/** Ô có độ tin cậy thấp mà người dùng chưa sửa. */
export function isLowConfidence(row: PreviewRow, field: string): boolean {
  if (row.edited.includes(field)) return false
  const confidence = row.confidence[field]
  return typeof confidence === 'number' && confidence < LOW_CONFIDENCE_THRESHOLD
}

/** Dòng cần xem lại: có lỗi, có cảnh báo, có lỗi của lần nhập trước hoặc có ô độ tin cậy thấp chưa sửa. */
export function needsReview(
  row: PreviewRow,
  columns: readonly ExtractionColumn[],
  errors: Record<string, string>,
): boolean {
  if (Object.keys(errors).length > 0 || row.warnings.length > 0 || row.lastError) return true
  return columns.some((column) => isLowConfidence(row, column.field))
}

/** Sửa một ô: trả dòng mới (không đổi dòng cũ). */
export function applyCellEdit(row: PreviewRow, field: string, value: CellValue): PreviewRow {
  return {
    ...row,
    values: { ...row.values, [field]: value },
    edited: row.edited.includes(field) ? row.edited : [...row.edited, field],
  }
}

/**
 * Body nhập theo đúng thứ tự `rows`: chỉ các trường của bảng, bỏ ô trống, ô số thành số, ô ENUM thành mã, chuỗi bỏ
 * khoảng trắng hai đầu.
 */
export function buildImportRows(columns: readonly ExtractionColumn[], rows: readonly PreviewRow[]): ImportRowBody[] {
  return rows.map((row) => {
    const values: Record<string, string | number> = {}
    for (const column of columns) {
      const value = row.values[column.field]
      if (isEmptyValue(value)) continue
      if (isNumericColumn(column)) {
        const number = typeof value === 'number' ? value : parseNumber(String(value))
        values[column.field] = number ?? String(value)
      } else if (column.type === 'ENUM' && column.options.length > 0) {
        values[column.field] = matchOption(column.options, value ?? null) ?? String(value).trim()
      } else {
        values[column.field] = String(value).trim()
      }
    }
    return { row: row.row, values }
  })
}

/** Kết quả một dòng đã đối chiếu về dòng của bảng xem trước. */
export interface MappedRowResult extends ImportRowResult {
  /** Số dòng của bảng xem trước, `null` khi máy chủ trả `row` ngoài phạm vi đã gửi */
  previewRow: number | null
}

/**
 * Đối chiếu kết quả nhập: `results[k].row` là thứ tự trong mảng đã gửi (từ 1), nên dòng xem trước là
 * `submittedRows[row - 1]`.
 *
 * @param submittedRows số dòng xem trước theo đúng thứ tự đã gửi
 */
export function mapImportResults(submittedRows: readonly number[], result: ImportResult): MappedRowResult[] {
  return (result.results ?? []).map((item) => {
    const index = Number(item.row) - 1
    const previewRow = Number.isInteger(index) && index >= 0 && index < submittedRows.length ? submittedRows[index] : null
    return { ...item, previewRow }
  })
}

/**
 * Lỗi kiểu dữ liệu của cả lần nhập (400 VALIDATION_ERROR, `data` dạng `{ "rows[0].values.purchasePrice": "..." }`,
 * chỉ số từ 0) đổi về dòng xem trước và trường. Khóa không đúng dạng bị bỏ.
 */
export function mapImportValidationErrors(
  submittedRows: readonly number[],
  data: Record<string, string> | null | undefined,
): Record<number, Record<string, string>> {
  const mapped: Record<number, Record<string, string>> = {}
  if (!data) return mapped
  for (const [key, message] of Object.entries(data)) {
    const match = /^rows\[(\d+)\]\.values\.(\w+)$/.exec(key)
    if (!match) continue
    const previewRow = submittedRows[Number(match[1])]
    if (previewRow === undefined) continue
    mapped[previewRow] = { ...(mapped[previewRow] ?? {}), [match[2]]: message }
  }
  return mapped
}

/** Thời gian còn lại tới khi kết quả trích xuất hết hạn, `null` khi không đọc được thời điểm. */
export function remainingMs(expiresAt: string | null | undefined, now: number): number | null {
  if (!expiresAt) return null
  const time = new Date(expiresAt).getTime()
  return Number.isNaN(time) ? null : time - now
}

/** "còn 42 phút", "còn dưới 1 phút", "đã hết hạn". */
export function formatRemaining(ms: number): string {
  if (ms <= 0) return 'đã hết hạn'
  const minutes = Math.floor(ms / 60_000)
  return minutes < 1 ? 'còn dưới 1 phút' : `còn ${minutes} phút`
}

/** Giờ địa phương "10:05". */
export function formatClock(iso: string | null | undefined): string | null {
  if (!iso) return null
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return null
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

/** Phần trăm độ tin cậy: 0.95 thành "95%". */
export function formatConfidence(value: number | null | undefined): string | null {
  if (typeof value !== 'number' || Number.isNaN(value)) return null
  return `${Math.round(value * 100)}%`
}

export const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  CREATED: { label: 'Đã tạo', color: 'green' },
  SKIPPED: { label: 'Bỏ qua (mã đã có)', color: 'gold' },
  FAILED: { label: 'Lỗi', color: 'red' },
}
