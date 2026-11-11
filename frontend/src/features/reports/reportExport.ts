import axios from 'axios'
import * as XLSX from 'xlsx'
import { getErrorStatus } from '../../services/api'
import {
  downloadInOutBalanceExport,
  downloadStockCardExport,
  fetchInOutBalance,
  fetchStockCard,
} from './reportApi'
import { buildInOutBalanceSheet, buildStockCardSheet, type InOutBalanceSheetInput, type SheetSpec } from './reportExcel'
import { EXPORT_MAX_ROWS, formatQuantity, safeFileName } from './reportFormat'
import type { InOutBalanceParams, InOutBalanceRow, StockCardLine, StockCardParams } from './types'

/**
 * Tải file Excel báo cáo. Ưu tiên endpoint xuất file của backend (.../export); nếu backend chưa có endpoint này
 * (trả 404) thì tải toàn bộ dữ liệu qua API xem (từng trang 200 dòng, tối đa 20000 dòng) rồi tạo file ngay trên
 * trình duyệt bằng thư viện xlsx với cùng bố cục.
 */

/** Số dòng mỗi lần tải khi tạo file trên trình duyệt (bằng giới hạn size của backend). */
const FALLBACK_PAGE_SIZE = 200

export interface ExportProgress {
  loaded: number
  total: number
}

export interface ExportResult {
  source: 'server' | 'browser'
  fileName: string
  rowCount?: number
}

export type ExportTask = (onProgress: (p: ExportProgress) => void, signal: AbortSignal) => Promise<ExportResult>

/** Vượt giới hạn số dòng của file tạo trên trình duyệt. */
export class ExportLimitError extends Error {
  constructor(total: number) {
    super(
      `Báo cáo có ${formatQuantity(total)} dòng, vượt giới hạn ${formatQuantity(EXPORT_MAX_ROWS)} dòng của một file Excel. ` +
        'Hãy thu hẹp kỳ báo cáo hoặc lọc theo kho, danh mục, sản phẩm rồi tải lại.',
    )
    this.name = 'ExportLimitError'
  }
}

function throwIfAborted(signal: AbortSignal): void {
  if (signal.aborted) throw new axios.CanceledError('Đã hủy tải file')
}

/** Lưu Blob thành file tải về. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Ghi SheetSpec ra file xlsx: định dạng cột số, độ rộng cột, gộp ô các dòng tiêu đề và ghi chú. */
export function writeSheetFile(spec: SheetSpec, fileName: string): void {
  const sheet = XLSX.utils.aoa_to_sheet(spec.rows)
  const numberFormats = new Map<number, string>()
  spec.quantityColumns.forEach((c) => numberFormats.set(c, '#,##0.000'))
  spec.moneyColumns.forEach((c) => numberFormats.set(c, '#,##0'))
  for (let r = spec.headerRowIndex + 1; r < spec.rows.length; r++) {
    for (const [c, format] of numberFormats) {
      const cell = sheet[XLSX.utils.encode_cell({ r, c })] as XLSX.CellObject | undefined
      if (cell && cell.t === 'n') cell.z = format
    }
  }
  sheet['!cols'] = spec.columnWidths.map((wch) => ({ wch }))
  sheet['!merges'] = spec.fullWidthRows.map((r) => ({ s: { r, c: 0 }, e: { r, c: spec.columnCount - 1 } }))
  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, sheet, spec.sheetName)
  XLSX.writeFile(workbook, fileName)
}

/** true nếu lỗi là 404 của endpoint xuất file (backend chưa có), cần chuyển sang tạo file trên trình duyệt. */
function isMissingEndpoint(error: unknown): boolean {
  return getErrorStatus(error) === 404
}

export async function exportInOutBalance(
  params: InOutBalanceParams,
  filterLabels: InOutBalanceSheetInput['filterLabels'],
  onProgress: (p: ExportProgress) => void,
  signal: AbortSignal,
): Promise<ExportResult> {
  const fileName = `${safeFileName(`Bao_cao_xuat_nhap_ton_${params.fromDate}_${params.toDate}`)}.xlsx`
  try {
    const file = await downloadInOutBalanceExport(params, signal)
    saveBlob(file.blob, file.fileName ?? fileName)
    return { source: 'server', fileName: file.fileName ?? fileName }
  } catch (error) {
    // 404 khác (ví dụ kho không tồn tại) sẽ lặp lại ở API xem bên dưới và được báo cho người dùng
    if (!isMissingEndpoint(error)) throw error
  }

  const first = await fetchInOutBalance({ ...params, page: 0, size: FALLBACK_PAGE_SIZE }, signal)
  const total = first.rows.totalElements
  if (total > EXPORT_MAX_ROWS) throw new ExportLimitError(total)
  const rows: InOutBalanceRow[] = [...first.rows.content]
  onProgress({ loaded: rows.length, total })
  for (let page = 1; page < first.rows.totalPages; page++) {
    throwIfAborted(signal)
    const next = await fetchInOutBalance({ ...params, page, size: FALLBACK_PAGE_SIZE }, signal)
    rows.push(...next.rows.content)
    onProgress({ loaded: rows.length, total })
  }
  throwIfAborted(signal)
  const spec = buildInOutBalanceSheet({
    fromDate: first.fromDate,
    toDate: first.toDate,
    groupBy: first.groupBy,
    rows,
    totals: first.totals,
    dataNote: first.dataNote,
    valuationNote: first.valuationNote,
    generatedAt: first.generatedAt,
    filterLabels,
  })
  writeSheetFile(spec, fileName)
  return { source: 'browser', fileName, rowCount: rows.length }
}

export async function exportStockCard(
  params: StockCardParams,
  onProgress: (p: ExportProgress) => void,
  signal: AbortSignal,
): Promise<ExportResult> {
  const baseName = `The_kho_SP${params.productId}_Kho${params.warehouseId}_${params.fromDate}_${params.toDate}`
  let fileName = `${safeFileName(baseName)}.xlsx`
  try {
    const file = await downloadStockCardExport(params, signal)
    saveBlob(file.blob, file.fileName ?? fileName)
    return { source: 'server', fileName: file.fileName ?? fileName }
  } catch (error) {
    if (!isMissingEndpoint(error)) throw error
  }

  const first = await fetchStockCard({ ...params, page: 0, size: FALLBACK_PAGE_SIZE }, signal)
  const total = first.lines.totalElements
  if (total > EXPORT_MAX_ROWS) throw new ExportLimitError(total)
  const lines: StockCardLine[] = [...first.lines.content]
  onProgress({ loaded: lines.length, total })
  for (let page = 1; page < first.lines.totalPages; page++) {
    throwIfAborted(signal)
    const next = await fetchStockCard({ ...params, page, size: FALLBACK_PAGE_SIZE }, signal)
    lines.push(...next.lines.content)
    onProgress({ loaded: lines.length, total })
  }
  throwIfAborted(signal)
  if (first.skuCode) {
    fileName = `${safeFileName(`The_kho_${first.skuCode}_${first.warehouseCode ?? params.warehouseId}_${params.fromDate}_${params.toDate}`)}.xlsx`
  }
  writeSheetFile(buildStockCardSheet(first, lines), fileName)
  return { source: 'browser', fileName, rowCount: lines.length }
}
