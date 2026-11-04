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
 * Làm tròn 2 chữ số thập phân (HALF_UP) khớp chuẩn BigDecimal.setScale(2, RoundingMode.HALF_UP) trên backend.
 */
export function round2(num: number): number {
  return Math.round((num + Number.EPSILON) * 100) / 100
}

/**
 * Tính toán thành tiền, chiết khấu và thuế VAT cho 1 dòng sản phẩm (PO Item).
 * Khớp chuẩn với PurchaseOrderService trên backend (làm tròn 2 chữ số thập phân):
 * 1. Thành tiền thô (rawSubtotal) = round2(quantity * unitPrice)
 * 2. Tiền chiết khấu (discount): ưu tiên discountAmount > 0, ngược lại tính theo discountPercent %
 * 3. Tiền tính thuế (taxableAmount) = max(0, round2(rawSubtotal - discount))
 * 4. Tiền thuế VAT (tax): ưu tiên taxAmount > 0, ngược lại tính theo taxRate %
 * 5. Thành tiền dòng (lineTotal) = round2(taxableAmount + tax)
 */
export function calculateLineTotals(item: PurchaseOrderItemPayload): LineCalculationResult {
  const qty = Number(item.quantity) || 0
  const price = Number(item.unitPrice) || 0
  const rawSubtotal = round2(Math.max(0, qty * price))

  const discPercent = Math.min(100, Math.max(0, Number(item.discountPercent) || 0))
  let discount = 0
  if (
    item.discountAmount !== undefined &&
    item.discountAmount !== null &&
    item.discountAmount > 0
  ) {
    discount = round2(Number(item.discountAmount))
  } else if (discPercent > 0) {
    discount = round2((rawSubtotal * discPercent) / 100)
  }

  const taxableAmount = Math.max(0, round2(rawSubtotal - discount))

  const taxRate = Math.min(100, Math.max(0, Number(item.taxRate) || 0))
  let tax = 0
  if (
    item.taxAmount !== undefined &&
    item.taxAmount !== null &&
    item.taxAmount > 0
  ) {
    tax = round2(Number(item.taxAmount))
  } else if (taxRate > 0) {
    tax = round2((taxableAmount * taxRate) / 100)
  }

  const lineTotal = round2(taxableAmount + tax)

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
    subtotal: round2(subtotal),
    totalDiscount: round2(totalDiscount),
    totalTax: round2(totalTax),
    grandTotal: round2(grandTotal),
    totalQuantity: round2(totalQuantity),
    itemCount: items.length,
  }
}
