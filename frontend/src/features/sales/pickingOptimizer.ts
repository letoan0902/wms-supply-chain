/**
 * Thuật toán tối ưu hóa Lộ trình lấy hàng (Smart Picking Route Optimizer - FR17).
 *
 * 1. Phân bổ số lượng từng dòng SO vào các lô/ô kệ còn tồn theo đúng quy tắc trừ tồn của inventory-service
 *    (StockItemRepository.findAvailableStockItemsByProductIdAndWarehouseIdWithPessimisticLock): bỏ lô bị khóa,
 *    lô đã hết hạn (HSD <= hôm nay) và lô hết hàng; lô có HSD sớm nhất lấy trước (FEFO), lô không có HSD xếp sau,
 *    cùng HSD thì theo id bản ghi tồn (FIFO). Không lấy quá tồn thực tế của từng lô.
 * 2. Phần không đủ tồn được tách thành dòng "thiếu hàng" không có vị trí, không bịa ra ô kệ.
 * 3. Sắp xếp các điểm dừng theo Zone -> Rack -> Bin (so sánh tự nhiên: R2 < R10) để đi một vòng.
 *
 * Số lượng được tính bằng số nguyên theo đơn vị 0,001 (khớp DECIMAL(18,3) của backend) để không lệch số thực.
 */

import type { PickingItem } from './pickingTypes'
import type { SalesOrder } from './types'

export interface ProductLotInfo {
  stockItemId?: number
  productId: number
  lotNo?: string
  mfgDate?: string
  expDate?: string
  binLocationId?: number
  binLocationCode?: string
  zone?: string
  rack?: string
  bin?: string
  /** Tồn thực tế (OnHand) tại ô kệ / lô */
  quantity?: number
  availableQuantity?: number
  locked?: boolean
}

const QTY_SCALE = 1000

function toUnits(qty: number | string | null | undefined): number {
  const n = Number(qty)
  return Number.isFinite(n) ? Math.round(n * QTY_SCALE) : 0
}

function fromUnits(units: number): number {
  return units / QTY_SCALE
}

/** Ngày hôm nay theo giờ máy, dạng YYYY-MM-DD (cùng dạng LocalDate backend trả về). */
function localToday(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const naturalCollator = new Intl.Collator('en', { numeric: true, sensitivity: 'base' })

/** So sánh tự nhiên có xác định: chuỗi rỗng xếp cuối, 'R2' < 'R10', hòa thì so nguyên văn. */
function compareNatural(a?: string, b?: string): number {
  const x = (a || '').trim().toUpperCase()
  const y = (b || '').trim().toUpperCase()
  if (x === y) return 0
  if (!x) return 1
  if (!y) return -1
  const c = naturalCollator.compare(x, y)
  if (c !== 0) return c
  return x < y ? -1 : 1
}

/** So sánh hạn dùng dạng YYYY-MM-DD: hạn sớm trước, không có hạn xếp sau. */
function compareExpiry(a?: string, b?: string): number {
  if (a === b) return 0
  if (!a) return 1
  if (!b) return -1
  return a < b ? -1 : 1
}

function compareNumber(a?: number, b?: number): number {
  const x = a ?? Number.MAX_SAFE_INTEGER
  const y = b ?? Number.MAX_SAFE_INTEGER
  return x - y
}

/** Lô còn được phép xuất theo đúng điều kiện của backend. */
export function isPickableLot(lot: ProductLotInfo, today: string = localToday()): boolean {
  if (lot.locked) return false
  if (toUnits(lot.quantity) <= 0) return false
  if (lot.expDate && lot.expDate.slice(0, 10) <= today) return false
  return true
}

/** Thứ tự FEFO của backend: HSD tăng dần (không có HSD xếp sau), rồi id bản ghi tồn tăng dần. */
function compareFefo(a: ProductLotInfo, b: ProductLotInfo): number {
  return (
    compareExpiry(a.expDate, b.expDate) ||
    compareNumber(a.stockItemId, b.stockItemId) ||
    compareNatural(a.binLocationCode, b.binLocationCode)
  )
}

/**
 * So sánh 2 điểm dừng theo thứ tự: Zone -> Rack -> Bin -> Hạn dùng (FEFO) -> Lô -> SKU.
 * Dòng thiếu hàng (không có vị trí) luôn xếp cuối.
 */
export function comparePickingLocations(a: PickingItem, b: PickingItem): number {
  if (Boolean(a.shortage) !== Boolean(b.shortage)) return a.shortage ? 1 : -1
  return (
    compareNatural(a.zone, b.zone) ||
    compareNatural(a.rack, b.rack) ||
    compareNatural(a.bin, b.bin) ||
    compareNatural(a.locationCode, b.locationCode) ||
    compareExpiry(a.expDate, b.expDate) ||
    compareNatural(a.lotNo, b.lotNo) ||
    compareNatural(a.skuCode, b.skuCode) ||
    a.soItemId - b.soItemId ||
    (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)
  )
}

/**
 * Tạo danh sách lộ trình lấy hàng thông minh (Smart Picking List) cho một Đơn bán hàng (SO)
 * từ danh sách lô/ô kệ còn tồn của kho xuất. Đánh số thứ tự chặng di chuyển (1, 2, 3...).
 */
export function buildOptimizedPickingList(
  order: SalesOrder,
  productLots: ProductLotInfo[] = [],
  today: string = localToday()
): PickingItem[] {
  if (!order || !order.items || order.items.length === 0) {
    return []
  }

  // Nhóm các lô còn xuất được theo productId, sắp theo FEFO
  const lotsByProduct = new Map<number, ProductLotInfo[]>()
  productLots
    .filter((lot) => isPickableLot(lot, today))
    .forEach((lot) => {
      const arr = lotsByProduct.get(lot.productId) || []
      arr.push(lot)
      lotsByProduct.set(lot.productId, arr)
    })
  lotsByProduct.forEach((arr) => arr.sort(compareFefo))

  // Tồn còn lại của từng lô (cùng sản phẩm có thể nằm ở nhiều dòng SO)
  const remainingByLot = new Map<ProductLotInfo, number>()
  productLots.forEach((lot) => remainingByLot.set(lot, toUnits(lot.quantity)))

  const rawItems: PickingItem[] = []

  order.items.forEach((item) => {
    const base = {
      soItemId: item.id,
      productId: item.productId,
      skuCode: item.skuCode,
      productName: item.productName,
      uom: item.uom || '',
      pickedQuantity: 0,
      isPicked: false,
      step: 0,
      note: item.note,
    }

    let remaining = toUnits(item.quantity)
    for (const lot of lotsByProduct.get(item.productId) || []) {
      if (remaining <= 0) break
      const lotRemaining = remainingByLot.get(lot) || 0
      if (lotRemaining <= 0) continue
      const take = Math.min(remaining, lotRemaining)
      remainingByLot.set(lot, lotRemaining - take)
      remaining -= take

      rawItems.push({
        ...base,
        id: `pick-${item.id}-${lot.stockItemId ?? lot.binLocationId ?? rawItems.length}`,
        orderedQuantity: fromUnits(take),
        stockItemId: lot.stockItemId,
        locationId: lot.binLocationId,
        locationCode: lot.binLocationCode || '',
        zone: lot.zone || '',
        rack: lot.rack || '',
        bin: lot.bin || '',
        lotNo: lot.lotNo,
        mfgDate: lot.mfgDate,
        expDate: lot.expDate,
      })
    }

    if (remaining > 0) {
      // Không đủ tồn khả dụng: giữ phần thiếu, không gán vị trí giả
      rawItems.push({
        ...base,
        id: `pick-${item.id}-thieu`,
        orderedQuantity: fromUnits(remaining),
        shortage: true,
        locationCode: '',
        zone: '',
        rack: '',
        bin: '',
      })
    }
  })

  // Sắp xếp lộ trình theo thứ tự tối ưu hóa: Zone -> Rack -> Bin -> FEFO
  rawItems.sort(comparePickingLocations)

  // Đánh số thứ tự chặng dừng (1, 2...)
  return rawItems.map((it, idx) => ({
    ...it,
    step: idx + 1,
  }))
}
