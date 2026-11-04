import type { PurchaseOrderItemPayload } from './types'

export interface LineCalculationResult {
  rawSubtotal: number
  discount: number
  taxableAmount: number
  tax: number
  lineTotal: number
}

export interface OrderCalculationResult {
  subtotal: number
  totalDiscount: number
  totalTax: number
  grandTotal: number
  totalQuantity: number
  itemCount: number
}

/**
 * Tính toán thành tiền, chiết khấu và thuế VAT cho 1 dòng sản phẩm (PO Item).
 * Khớp chuẩn với công thức backend:
 * 1. Tiền hàng trước CK = quantity * unitPrice
 * 2. Tiền CK = rawSubtotal * (discountPercent / 100)
 * 3. Tiền tính thuế = rawSubtotal - discount
 * 4. Tiền VAT = taxableAmount * (taxRate / 100)
 * 5. Thành tiền dòng = taxableAmount + tax
 */
export function calculateLineTotals(item: PurchaseOrderItemPayload): LineCalculationResult {
  const qty = Number(item.quantity) || 0
  const price = Number(item.unitPrice) || 0
  const rawSubtotal = Math.max(0, qty * price)

  const discPercent = Math.min(100, Math.max(0, Number(item.discountPercent) || 0))
  const discount =
    item.discountAmount !== undefined &&
    item.discountAmount !== null &&
    item.discountAmount > 0 &&
    discPercent === 0
      ? Number(item.discountAmount)
      : Math.round((rawSubtotal * discPercent) / 100)

  const taxableAmount = Math.max(0, rawSubtotal - discount)
  const taxRate = Math.min(100, Math.max(0, Number(item.taxRate) || 0))
  const tax =
    item.taxAmount !== undefined &&
    item.taxAmount !== null &&
    item.taxAmount > 0 &&
    taxRate === 0
      ? Number(item.taxAmount)
      : Math.round((taxableAmount * taxRate) / 100)

  const lineTotal = taxableAmount + tax

  return {
    rawSubtotal,
    discount,
    taxableAmount,
    tax,
    lineTotal,
  }
}

/**
 * Tính tổng cộng đơn mua hàng (Grand Total & Summary).
 */
export function calculateOrderTotals(items: PurchaseOrderItemPayload[]): OrderCalculationResult {
  let subtotal = 0
  let totalDiscount = 0
  let totalTax = 0
  let grandTotal = 0
  let totalQuantity = 0

  for (const item of items) {
    const calc = calculateLineTotals(item)
    subtotal += calc.rawSubtotal
    totalDiscount += calc.discount
    totalTax += calc.tax
    grandTotal += calc.lineTotal
    totalQuantity += Number(item.quantity) || 0
  }

  return {
    subtotal,
    totalDiscount,
    totalTax,
    grandTotal,
    totalQuantity,
    itemCount: items.length,
  }
}
