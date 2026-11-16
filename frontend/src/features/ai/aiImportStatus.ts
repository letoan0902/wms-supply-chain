import { formatConfidence, isEmptyValue, isLowConfidence } from './aiImport'
import type { PreviewRow } from './aiImport'
import type { CellValue, ExtractionColumn, ExtractionDataType } from './types'

/**
 * Trạng thái từng dòng của bảng xem trước "Nhập dữ liệu bằng AI" và các con số tóm tắt (không phụ thuộc React).
 * Dựa trên kiểm tra sẵn có của aiImport.ts (validateRow, isLowConfidence), thêm phát hiện trùng mã trong cùng file
 * theo đúng quy tắc của API nhập hàng loạt: mã so sánh sau khi cắt khoảng trắng và viết hoa, dòng sau trùng mã dòng
 * trước bị máy chủ báo lỗi (ProductBulkService, PartnerBulkImportService).
 */

/** Trạng thái của một dòng; `excluded` là dòng người dùng đã bỏ chọn (vẫn nằm trong bảng, không gửi đi). */
export type RowStatus = 'ready' | 'review' | 'error' | 'duplicate' | 'excluded'

/** Vấn đề của dòng, không tính việc bỏ chọn. */
export type RowProblem = Exclude<RowStatus, 'excluded'>

export const ROW_STATUS_ORDER: readonly RowStatus[] = ['ready', 'review', 'error', 'duplicate', 'excluded']

export interface StatusMeta {
  /** Nhãn ngắn của thẻ trạng thái trong bảng */
  label: string
  /** Nhãn của ô tóm tắt */
  title: string
  /** Một câu giải thích dưới con số */
  hint: string
  /** Màu có sẵn của Tag antd */
  tagColor: string
  /** Màu đoạn trong thanh tỉ lệ, cùng họ màu với Tag */
  barColor: string
}

/** Một bảng màu dùng chung cho bước xem trước, hộp xác nhận và bước kết quả. */
export const ROW_STATUS_META: Record<RowStatus, StatusMeta> = {
  ready: {
    label: 'Sẵn sàng',
    title: 'Sẵn sàng nhập',
    hint: 'Đủ thông tin, AI đọc chắc chắn',
    tagColor: 'green',
    barColor: '#52c41a',
  },
  review: {
    label: 'Cần kiểm tra',
    title: 'Cần kiểm tra',
    hint: 'AI chưa chắc hoặc có ghi chú, vẫn nhập được',
    tagColor: 'gold',
    barColor: '#faad14',
  },
  error: {
    label: 'Lỗi',
    title: 'Lỗi phải sửa',
    hint: 'Thiếu trường bắt buộc hoặc sai kiểu',
    tagColor: 'red',
    barColor: '#ff4d4f',
  },
  duplicate: {
    label: 'Trùng mã',
    title: 'Trùng mã trong file',
    hint: 'Mỗi mã chỉ nhập được một dòng',
    tagColor: 'purple',
    barColor: '#9254de',
  },
  excluded: {
    label: 'Không nhập',
    title: 'Đã bỏ chọn',
    hint: 'Giữ trong bảng, không gửi đi',
    tagColor: 'default',
    barColor: '#cbd5e1',
  },
}

/** Trường mã dùng để nhận biết trùng (trường đầu tiên của ExtractionDataType ở ai-service). */
export const IDENTITY_FIELD: Record<ExtractionDataType, string> = {
  PRODUCT: 'sku',
  SUPPLIER: 'code',
  CUSTOMER: 'code',
}

/**
 * Ghi chú trùng mã mà máy chủ tự sinh lúc đọc file (ExtractionAssembler: `<Mã> "X" trùng với dòng N, khi nhập dòng
 * này sẽ bị báo lỗi`, đồng thời hạ độ tin cậy ô mã xuống 0,5). Màn hình kiểm tra trùng mã theo các dòng đang được
 * chọn nên ghi chú này được thay bằng kết quả kiểm tra đó (cập nhật khi bỏ chọn), không giữ câu đã cũ.
 */
export function isDuplicateNote(note: string): boolean {
  return note.includes('trùng với dòng') && note.includes('khi nhập dòng này sẽ bị báo lỗi')
}

/**
 * Ghi chú của AI cho một dòng (`ExtractionRow.warnings`), trừ ghi chú trùng mã tự sinh. Giống màn hình cũ, ghi chú
 * gắn với dòng chứ không với ô và vẫn giữ sau khi người dùng sửa ô (máy chủ không cho biết ghi chú thuộc ô nào).
 */
export function rowNotes(warnings: readonly string[]): string[] {
  return warnings.filter((note) => typeof note === 'string' && note.trim() !== '' && !isDuplicateNote(note))
}

/** Mã đã chuẩn hóa như máy chủ (cắt khoảng trắng, viết hoa); trống thì `null`. */
export function normalizeCode(value: CellValue | undefined): string | null {
  if (isEmptyValue(value)) return null
  return String(value).trim().toUpperCase()
}

/** Đánh giá một dòng của bảng xem trước. */
export interface RowAssessment {
  row: number
  status: RowStatus
  problem: RowProblem
  included: boolean
  /** Lỗi chặn nhập theo trường (validateRow cộng lỗi máy chủ trả về) */
  errors: Record<string, string>
  /** Dòng phía trên (đang được chọn) có cùng mã, `null` khi không trùng */
  duplicateOf: number | null
  /** Các trường AI chưa chắc chắn mà người dùng chưa sửa */
  lowFields: string[]
  /** Ghi chú của AI cho dòng (rowNotes) */
  notes: string[]
  /** Lý do bằng lời, theo thứ tự quan trọng, để hiện khi rê chuột lên thẻ trạng thái */
  reasons: string[]
  /** Lý do ngắn hiện ngay dưới thẻ trạng thái (rỗng khi dòng sẵn sàng) */
  shortReason: string
  /** Số ghi chú AI còn lại ngoài ghi chú đầu tiên đã nêu trong `shortReason` (hiện "+n") */
  moreNotes: number
}

/**
 * Đánh giá mọi dòng theo đúng thứ tự gửi đi. Thứ tự ưu tiên: lỗi chặn nhập, trùng mã, cần kiểm tra (ô AI chưa chắc
 * chắn, ghi chú của AI, lỗi của lần nhập trước), sẵn sàng; dòng đã bỏ chọn có trạng thái `excluded` nhưng vẫn giữ
 * `problem` để biết vì sao. Trùng mã chỉ xét giữa các dòng đang được chọn: bỏ chọn một trong hai dòng trùng thì dòng
 * còn lại trở về bình thường.
 */
export function assessRows(
  rows: readonly PreviewRow[],
  columns: readonly ExtractionColumn[],
  errorsByRow: ReadonlyMap<number, Record<string, string>>,
  excluded: ReadonlySet<number>,
  identityField: string,
): RowAssessment[] {
  const labelOf = new Map(columns.map((column) => [column.field, column.label]))
  const identityLabel = labelOf.get(identityField) ?? 'Mã'
  // Dòng đầu tiên (đang được chọn) của mỗi mã: dòng chọn sau trùng mã sẽ bị máy chủ báo lỗi
  const firstRowByCode = new Map<string, number>()
  for (const row of rows) {
    const code = normalizeCode(row.values[identityField])
    if (code !== null && !excluded.has(row.row) && !firstRowByCode.has(code)) firstRowByCode.set(code, row.row)
  }

  return rows.map((row) => {
    const included = !excluded.has(row.row)
    const errors = errorsByRow.get(row.row) ?? {}
    const notes = rowNotes(row.warnings)
    const serverDuplicate = row.warnings.some(isDuplicateNote)
    const lowFields = columns
      .filter((column) => isLowConfidence(row, column.field))
      // Ô mã bị hạ độ tin cậy chỉ vì ghi chú trùng mã tự sinh: đã thay bằng kiểm tra trùng mã ở dưới
      .filter((column) => column.field !== identityField || !serverDuplicate)
      .map((column) => column.field)

    // Dòng đang chọn: trùng một dòng chọn phía trên. Dòng đã bỏ chọn: trùng một dòng đang chọn (nếu chọn lại sẽ trùng).
    const code = normalizeCode(row.values[identityField])
    const first = code !== null ? firstRowByCode.get(code) : undefined
    const duplicateOf = first !== undefined && first !== row.row ? first : null

    const reasons: string[] = []
    reasons.push(...Object.values(errors))
    if (duplicateOf !== null) {
      reasons.push(`${identityLabel} "${code}" trùng với dòng ${duplicateOf}: sửa mã hoặc bỏ chọn một trong hai dòng`)
    }
    if (row.lastError) reasons.push(`Lần nhập trước bị lỗi: ${row.lastError}`)
    const editedNote = row.edited.length > 0 ? ' (ghi lúc đọc file, trước khi bạn sửa)' : ''
    // Ghi chú trùng nguyên văn một lỗi đã nêu (ví dụ "Thiếu Đơn vị tính (bắt buộc)") thì không nhắc lại
    const errorTexts = new Set(Object.values(errors))
    for (const note of notes) if (!errorTexts.has(note)) reasons.push(`AI ghi chú: ${note}${editedNote}`)
    for (const field of lowFields) {
      // Lỗi chặn nhập của ô này đã nêu ở trên; trùng mã đã có câu riêng
      if (errors[field] || (field === identityField && duplicateOf !== null)) continue
      const confidence = formatConfidence(row.confidence[field])
      reasons.push(
        `AI chưa chắc chắn ở ô ${labelOf.get(field) ?? field}${confidence ? ` (độ tin cậy ${confidence})` : ''}`,
      )
    }

    const problem: RowProblem =
      Object.keys(errors).length > 0
        ? 'error'
        : duplicateOf !== null
          ? 'duplicate'
          : lowFields.length > 0 || notes.length > 0 || row.lastError
            ? 'review'
            : 'ready'
    const shortReason =
      problem === 'error'
        ? (Object.values(errors)[0] ?? '')
        : problem === 'duplicate'
          ? `Trùng mã với dòng ${duplicateOf}`
          : problem === 'review'
            ? notes.length > 0
              ? `AI ghi chú: ${notes[0]}`
              : lowFields.length > 0
                ? `Xem lại: ${lowFields.map((field) => labelOf.get(field) ?? field).join(', ')}`
                : 'Lần nhập trước bị lỗi'
            : ''
    return {
      row: row.row,
      status: included ? problem : 'excluded',
      problem,
      included,
      errors,
      duplicateOf,
      lowFields,
      notes,
      reasons,
      shortReason,
      moreNotes: problem === 'review' && notes.length > 1 ? notes.length - 1 : 0,
    }
  })
}

export interface StatusSummary {
  total: number
  /** Số dòng theo trạng thái (dòng bỏ chọn chỉ đếm ở `excluded`), tổng bằng `total` */
  counts: Record<RowStatus, number>
  /** Dòng sẽ được gửi khi bấm nhập (nút nhập chỉ mở khi `blocking = 0`) */
  toImport: number
  /** Dòng đang được chọn nhưng còn lỗi hoặc trùng mã: phải sửa hoặc bỏ chọn trước khi nhập */
  blocking: number
  /** Dòng cần xem lại: cần kiểm tra, lỗi, trùng mã (không tính dòng đã bỏ chọn) */
  attention: number
  /** Dòng đã bỏ chọn theo vấn đề của dòng */
  excludedByProblem: Record<RowProblem, number>
}

export function summarize(assessments: readonly RowAssessment[]): StatusSummary {
  const counts: Record<RowStatus, number> = { ready: 0, review: 0, error: 0, duplicate: 0, excluded: 0 }
  const excludedByProblem: Record<RowProblem, number> = { ready: 0, review: 0, error: 0, duplicate: 0 }
  for (const item of assessments) {
    counts[item.status] += 1
    if (item.status === 'excluded') excludedByProblem[item.problem] += 1
  }
  const blocking = counts.error + counts.duplicate
  return {
    total: assessments.length,
    counts,
    toImport: counts.ready + counts.review,
    blocking,
    attention: counts.review + blocking,
    excludedByProblem,
  }
}

/** Bộ lọc của bảng: tất cả, một trạng thái, hoặc mọi dòng cần xem lại. */
export type RowFilter = 'all' | 'attention' | RowStatus

export function matchesFilter(item: RowAssessment, filter: RowFilter): boolean {
  if (filter === 'all') return true
  if (filter === 'attention') return item.status === 'review' || item.status === 'error' || item.status === 'duplicate'
  return item.status === filter
}

/** Dòng đang được chọn mà còn lỗi hoặc trùng mã (cho nút "Bỏ chọn các dòng lỗi"). */
export function blockingRowNumbers(assessments: readonly RowAssessment[]): number[] {
  return assessments.filter((item) => item.status === 'error' || item.status === 'duplicate').map((item) => item.row)
}

/** Dòng sẽ gửi đi (sẵn sàng, cần kiểm tra), đúng thứ tự của bảng. */
export function rowsToImport(rows: readonly PreviewRow[], assessments: readonly RowAssessment[]): PreviewRow[] {
  const sendable = new Set(
    assessments.filter((item) => item.status === 'ready' || item.status === 'review').map((item) => item.row),
  )
  return rows.filter((row) => sendable.has(row.row))
}

/**
 * Cột ẩn mặc định cho gọn: không bắt buộc và mọi dòng đều trống (thường là trường không có trong file). Tính trên
 * dữ liệu AI trả về, không theo chỉnh sửa, để cột không tự ẩn hiện khi đang sửa.
 */
export function emptyOptionalColumns(
  columns: readonly ExtractionColumn[],
  rows: readonly { values: Record<string, CellValue> }[],
): string[] {
  return columns
    .filter((column) => !column.required && rows.every((row) => isEmptyValue(row.values[column.field])))
    .map((column) => column.field)
}

/** "dòng 3, 6 và 2 dòng khác" để nhắc số dòng trong câu, tối đa `limit` số. */
export function formatRowList(rowNumbers: readonly number[], limit = 5): string {
  if (rowNumbers.length === 0) return ''
  const shown = rowNumbers.slice(0, limit).join(', ')
  const rest = rowNumbers.length - limit
  return rest > 0 ? `dòng ${shown} và ${rest} dòng khác` : `dòng ${shown}`
}

/** Phần trăm cho độ rộng đoạn thanh tỉ lệ; tổng 0 thì 0. */
export function share(count: number, total: number): number {
  if (total <= 0) return 0
  return (count / total) * 100
}

/**
 * Màu của bước kết quả, cùng bảng màu với bước xem trước: tạo mới như "Sẵn sàng", bỏ qua vì mã đã có như "Trùng mã",
 * lỗi như "Lỗi", không gửi như "Đã bỏ chọn".
 */
export const RESULT_STATUS_META: Record<'CREATED' | 'SKIPPED' | 'FAILED' | 'NOT_SENT', StatusMeta> = {
  CREATED: {
    label: 'Đã tạo',
    title: 'Đã tạo mới',
    hint: 'Đã vào danh mục',
    tagColor: ROW_STATUS_META.ready.tagColor,
    barColor: ROW_STATUS_META.ready.barColor,
  },
  SKIPPED: {
    label: 'Bỏ qua (mã đã có)',
    title: 'Bỏ qua (mã đã có)',
    hint: 'Giữ nguyên dữ liệu đang có, không ghi đè',
    tagColor: ROW_STATUS_META.duplicate.tagColor,
    barColor: ROW_STATUS_META.duplicate.barColor,
  },
  FAILED: {
    label: 'Lỗi',
    title: 'Lỗi',
    hint: 'Chưa vào danh mục, sửa và nhập tiếp được',
    tagColor: ROW_STATUS_META.error.tagColor,
    barColor: ROW_STATUS_META.error.barColor,
  },
  NOT_SENT: {
    label: 'Không gửi',
    title: 'Không gửi',
    hint: 'Dòng bạn đã bỏ chọn',
    tagColor: ROW_STATUS_META.excluded.tagColor,
    barColor: ROW_STATUS_META.excluded.barColor,
  },
}
