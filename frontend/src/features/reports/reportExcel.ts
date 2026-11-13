import type { GroupBy, InOutBalanceRow, InOutBalanceTotals, StockCardLine, StockCardReport } from './types'
import {
  documentTypeLabel,
  formatDate,
  formatInstant,
  movementTypeLabel,
  productLabel,
  toNumber,
  warehouseLabel,
} from './reportFormat'

/**
 * Dựng nội dung sheet Excel của báo cáo dưới dạng mảng hai chiều (thuần, không phụ thuộc thư viện xlsx) để dễ kiểm
 * tra. Bố cục cố định: dòng tiêu đề, dòng điều kiện lọc, dòng tiêu đề cột, các dòng dữ liệu, dòng tổng, ghi chú.
 * Ô không xác định (null) để trống, không ghi 0.
 */

export type SheetCell = string | number | null

export interface SheetSpec {
  sheetName: string
  rows: SheetCell[][]
  columnCount: number
  /** Chỉ số dòng tiêu đề cột */
  headerRowIndex: number
  /** Các cột số lượng (định dạng tối đa 3 chữ số thập phân) */
  quantityColumns: number[]
  /** Các cột tiền (định dạng số nguyên có phân cách hàng nghìn) */
  moneyColumns: number[]
  /** Độ rộng cột theo số ký tự */
  columnWidths: number[]
  /** Các dòng có ô đầu trải hết chiều ngang (tiêu đề, điều kiện lọc, ghi chú) */
  fullWidthRows: number[]
}

export interface InOutBalanceSheetInput {
  fromDate: string
  toDate: string
  groupBy: GroupBy
  rows: InOutBalanceRow[]
  totals: InOutBalanceTotals
  dataNote: string | null
  valuationNote: string | null
  generatedAt: string | null
  /** Giá trị bộ lọc đã chọn, dạng chữ để ghi vào dòng điều kiện */
  filterLabels: {
    warehouse: string
    category?: string
    product?: string
    keyword?: string
  }
}

const VALUE_KEYS = ['openingValue', 'valueIn', 'valueOut', 'closingValue'] as const

/** true nếu backend đã trả ít nhất một giá trị tiền khác null (hiện tại luôn false vì chưa có giá vốn). */
export function hasMoneyValues(rows: InOutBalanceRow[], totals: InOutBalanceTotals | null | undefined): boolean {
  const any = (o: Pick<InOutBalanceRow, (typeof VALUE_KEYS)[number]>) => VALUE_KEYS.some((k) => toNumber(o[k]) !== null)
  return (totals ? any(totals) : false) || rows.some(any)
}

export function buildInOutBalanceSheet(input: InOutBalanceSheetInput): SheetSpec {
  const byWarehouse = input.groupBy !== 'PRODUCT'
  const withMoney = hasMoneyValues(input.rows, input.totals)

  const header: string[] = ['STT', 'Mã SKU', 'Tên sản phẩm', 'ĐVT']
  const widths: number[] = [6, 18, 40, 10]
  if (byWarehouse) {
    header.push('Kho')
    widths.push(28)
  }
  const firstQty = header.length
  header.push('Tồn đầu kỳ', 'Nhập trong kỳ', 'Xuất trong kỳ', 'Tồn cuối kỳ')
  widths.push(16, 16, 16, 16)
  const quantityColumns = [firstQty, firstQty + 1, firstQty + 2, firstQty + 3]
  const moneyColumns: number[] = []
  if (withMoney) {
    const firstMoney = header.length
    header.push('Giá trị tồn đầu kỳ', 'Giá trị nhập', 'Giá trị xuất', 'Giá trị tồn cuối kỳ')
    widths.push(20, 20, 20, 20)
    moneyColumns.push(firstMoney, firstMoney + 1, firstMoney + 2, firstMoney + 3)
  }
  const columnCount = header.length

  const filters = [`Kỳ: ${formatDate(input.fromDate)} - ${formatDate(input.toDate)}`, `Kho: ${input.filterLabels.warehouse}`]
  if (input.filterLabels.category) filters.push(`Danh mục: ${input.filterLabels.category}`)
  if (input.filterLabels.product) filters.push(`Sản phẩm: ${input.filterLabels.product}`)
  if (input.filterLabels.keyword) filters.push(`Từ khóa: ${input.filterLabels.keyword}`)
  filters.push(`Gom dòng: ${byWarehouse ? 'theo sản phẩm và kho' : 'theo sản phẩm (gộp các kho)'}`)

  const rows: SheetCell[][] = [['BÁO CÁO XUẤT - NHẬP - TỒN KHO'], [filters.join('; ')], header]
  const fullWidthRows = [0, 1]

  input.rows.forEach((r, i) => {
    const row: SheetCell[] = [i + 1, r.skuCode ?? '', r.productName ?? productLabel(r.skuCode, r.productName, r.productId), r.uom ?? '']
    if (byWarehouse) row.push(warehouseLabel(r.warehouseCode, r.warehouseName, r.warehouseId))
    row.push(toNumber(r.openingQuantity), toNumber(r.quantityIn), toNumber(r.quantityOut), toNumber(r.closingQuantity))
    if (withMoney) row.push(toNumber(r.openingValue), toNumber(r.valueIn), toNumber(r.valueOut), toNumber(r.closingValue))
    rows.push(row)
  })

  const totalRow: SheetCell[] = new Array(columnCount).fill(null)
  totalRow[0] = 'Tổng cộng'
  totalRow[quantityColumns[0]] = toNumber(input.totals.openingQuantity)
  totalRow[quantityColumns[1]] = toNumber(input.totals.quantityIn)
  totalRow[quantityColumns[2]] = toNumber(input.totals.quantityOut)
  totalRow[quantityColumns[3]] = toNumber(input.totals.closingQuantity)
  if (withMoney) {
    totalRow[moneyColumns[0]] = toNumber(input.totals.openingValue)
    totalRow[moneyColumns[1]] = toNumber(input.totals.valueIn)
    totalRow[moneyColumns[2]] = toNumber(input.totals.valueOut)
    totalRow[moneyColumns[3]] = toNumber(input.totals.closingValue)
  }
  rows.push(totalRow)

  const notes: string[] = []
  if (input.dataNote) notes.push(`Ghi chú số liệu: ${input.dataNote}`)
  if (!withMoney && input.valuationNote) notes.push(`Giá trị tiền: ${input.valuationNote}`)
  if (input.generatedAt) notes.push(`Lập lúc: ${formatInstant(input.generatedAt)}`)
  if (notes.length > 0) rows.push([])
  for (const note of notes) {
    fullWidthRows.push(rows.length)
    rows.push([note])
  }

  return {
    sheetName: 'Xuat_Nhap_Ton',
    rows,
    columnCount,
    headerRowIndex: 2,
    quantityColumns,
    moneyColumns,
    columnWidths: widths,
    fullWidthRows,
  }
}

/** Nội dung ô "Số chứng từ": loại chứng từ kèm số (thiếu số thì kèm ID). */
export function documentCell(type: string | null, number: string | null, id: number | null): string {
  const label = documentTypeLabel(type)
  const ref = number ?? (id !== null && id !== undefined ? `ID ${id}` : '')
  return [label, ref].filter(Boolean).join(' ')
}

export function buildStockCardSheet(card: StockCardReport, lines: StockCardLine[]): SheetSpec {
  const header = [
    'STT',
    'Thời điểm',
    'Loại biến động',
    'Số chứng từ',
    'Chứng từ tham chiếu',
    'Số lô',
    'Hạn dùng',
    'Vị trí',
    'Nhập',
    'Xuất',
    'Tồn',
    'Người thực hiện',
    'Diễn giải',
  ]
  const IN = 8
  const OUT = 9
  const BAL = 10
  const columnCount = header.length
  const product = productLabel(card.skuCode, card.productName, card.productId)
  const filters = [
    `Sản phẩm: ${product}${card.uom ? ` (ĐVT: ${card.uom})` : ''}`,
    `Kho: ${warehouseLabel(card.warehouseCode, card.warehouseName, card.warehouseId)}`,
    `Kỳ: ${formatDate(card.fromDate)} - ${formatDate(card.toDate)}`,
  ]

  const rows: SheetCell[][] = [['THẺ KHO'], [filters.join('; ')], header]
  const fullWidthRows = [0, 1]

  const opening: SheetCell[] = new Array(columnCount).fill(null)
  opening[2] = 'Tồn đầu kỳ'
  opening[BAL] = toNumber(card.openingQuantity)
  rows.push(opening)

  lines.forEach((l, i) => {
    rows.push([
      i + 1,
      formatInstant(l.occurredAt),
      movementTypeLabel(l.movementType, l.movementTypeLabel),
      documentCell(l.documentType, l.documentNumber, l.documentId),
      l.referenceType || l.referenceNumber ? documentCell(l.referenceType, l.referenceNumber, l.referenceId) : '',
      l.lotNo ?? '',
      l.expDate ? formatDate(l.expDate) : '',
      l.binLocationCode ?? '',
      toNumber(l.quantityIn),
      toNumber(l.quantityOut),
      toNumber(l.balanceAfter),
      l.performedBy ?? '',
      l.reason ?? '',
    ])
  })

  const total: SheetCell[] = new Array(columnCount).fill(null)
  total[2] = 'Cộng phát sinh trong kỳ'
  total[IN] = toNumber(card.totalIn)
  total[OUT] = toNumber(card.totalOut)
  rows.push(total)
  const closing: SheetCell[] = new Array(columnCount).fill(null)
  closing[2] = 'Tồn cuối kỳ'
  closing[BAL] = toNumber(card.closingQuantity)
  rows.push(closing)

  const notes: string[] = []
  if (card.dataNote) notes.push(`Ghi chú số liệu: ${card.dataNote}`)
  if (card.generatedAt) notes.push(`Lập lúc: ${formatInstant(card.generatedAt)}`)
  if (notes.length > 0) rows.push([])
  for (const note of notes) {
    fullWidthRows.push(rows.length)
    rows.push([note])
  }

  return {
    sheetName: 'The_kho',
    rows,
    columnCount,
    headerRowIndex: 2,
    quantityColumns: [IN, OUT, BAL],
    moneyColumns: [],
    columnWidths: [6, 18, 26, 30, 26, 16, 12, 20, 14, 14, 14, 16, 30],
    fullWidthRows,
  }
}
