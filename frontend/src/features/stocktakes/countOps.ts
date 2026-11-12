/**
 * Thao tác ghi số đếm của màn hình đếm mù (phần tử của hàng đợi trong countQueue.ts) và cách gắn thao tác đang
 * chờ hoặc lỗi vào đúng dòng. Tệp không phụ thuộc React hay axios.
 */
import { isOutcomeUnknown } from '../../services/stockDocuments'
import type { FailureKind, QueueItem } from './countQueue'
import { lineKeyOf } from './stocktakeLogic'
import type { CountMode, StocktakeCountLine } from './types'

/** Lỗi màn hình tự phát hiện trước khi gửi (mã không có trong danh mục, thiếu số lô...): không gửi lại. */
export class ScanRejection extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ScanRejection'
  }
}

/**
 * Không tra được sản phẩm vì lỗi kết nối product-service: chưa ghi gì nên thử lại an toàn (khác lỗi không rõ kết
 * quả của chính lần ghi số đếm).
 */
export class LookupFailure extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'LookupFailure'
  }
}

/**
 * Phân loại lỗi của một lần ghi số đếm cho hàng đợi: lỗi tự phát hiện thì loại bỏ kèm thông báo; không rõ máy chủ
 * đã ghi chưa (không có phản hồi, 502, 504) thì `unknown`; còn lại (máy chủ trả lỗi, tức là chưa ghi) là `failed`.
 */
export function classifyCountError(error: unknown): FailureKind {
  if (error instanceof ScanRejection) return 'rejected'
  if (error instanceof LookupFailure) return 'failed'
  return isOutcomeUnknown(error) ? 'unknown' : 'failed'
}

interface OpBase {
  binLocationId: number
  binLocationCode: string
  quantity: number
}

/** Quét mã hàng hóa (mã vạch, SKU hoặc tem lô): tra sản phẩm khi tới lượt gửi rồi cộng dồn (ADD). */
export interface ScanOp extends OpBase {
  kind: 'scan'
  code: string
  /** Số lô đọc từ tem lô */
  labelLotNo: string | null
  /** Số lô người dùng nhập sẵn ở ô "Số lô" lúc quét (dùng khi hàng quản lý theo lô mà tem không có lô) */
  typedLotNo: string
  /** Kết quả tra sản phẩm, điền khi gửi (để gắn lỗi vào dòng) */
  resolvedProductId?: number
  resolvedLotNo?: string | null
  resolvedLabel?: string
}

/** Bấm +1 hoặc nhập số trên một dòng đã có. */
export interface LineOp extends OpBase {
  kind: 'line'
  lineId: number
  productId: number
  lotNo: string | null
  mode: CountMode
  label: string
}

/** Ghi hàng ngoài danh sách (hàng phát sinh) cho một sản phẩm đã chọn. */
export interface ProductOp extends OpBase {
  kind: 'product'
  productId: number
  lotNo: string | null
  mode: CountMode
  label: string
}

export type CountOp = ScanOp | LineOp | ProductOp

/** Mô tả ngắn một thao tác để hiện trong danh sách lỗi. */
export function describeOp(op: CountOp): string {
  const at = `tại ${op.binLocationCode}`
  if (op.kind === 'scan') {
    const what = op.resolvedLabel ?? `mã ${op.code}`
    return `Quét ${what}${op.resolvedLotNo ? ` (lô ${op.resolvedLotNo})` : ''} +${op.quantity} ${at}`
  }
  const lot = op.lotNo ? ` (lô ${op.lotNo})` : ''
  return op.mode === 'SET'
    ? `Ghi số ${op.label}${lot} = ${op.quantity} ${at}`
    : `${op.label}${lot} +${op.quantity} ${at}`
}

/** Khóa dòng mà thao tác nhắm tới, hoặc null nếu chưa biết (mã quét chưa tra xong). */
export function opLineKey(op: CountOp): string | null {
  if (op.kind === 'scan') {
    return op.resolvedProductId !== undefined
      ? lineKeyOf(op.binLocationId, op.resolvedProductId, op.resolvedLotNo ?? null)
      : null
  }
  return lineKeyOf(op.binLocationId, op.productId, op.lotNo)
}

export interface LineQueueState {
  pending: number
  failed: QueueItem<CountOp>[]
}

/** Gom thao tác đang chờ và lỗi theo dòng (khóa vị trí, sản phẩm, lô) để hiện ngay trên dòng. */
export function queueStateByLine(items: readonly QueueItem<CountOp>[]): Map<string, LineQueueState> {
  const result = new Map<string, LineQueueState>()
  items.forEach((item) => {
    const key = opLineKey(item.payload)
    if (key === null) return
    const state = result.get(key) ?? { pending: 0, failed: [] }
    if (item.status === 'queued' || item.status === 'sending') state.pending += 1
    else state.failed.push(item)
    result.set(key, state)
  })
  return result
}

/**
 * Cập nhật danh sách dòng của vị trí đang đếm bằng dòng máy chủ vừa trả: thay dòng cùng `lineId`, hoặc thêm vào
 * cuối nếu là dòng mới (hàng phát sinh) của đúng vị trí đó. Dòng của vị trí khác thì giữ nguyên danh sách.
 */
export function mergeCountLine(
  lines: readonly StocktakeCountLine[],
  updated: StocktakeCountLine,
  binLocationId: number,
): StocktakeCountLine[] {
  const index = lines.findIndex((l) => l.lineId === updated.lineId)
  if (index >= 0) {
    const next = lines.slice()
    next[index] = updated
    return next
  }
  if (updated.binLocationId !== binLocationId) return lines.slice()
  return [...lines, updated]
}
