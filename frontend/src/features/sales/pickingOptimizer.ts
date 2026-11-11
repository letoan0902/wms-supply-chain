/**
 * Thuật toán tối ưu hóa Lộ trình lấy hàng (Smart Picking Route Optimizer - FR17).
 * Sắp xếp các điểm dừng nhặt hàng theo thứ tự ô kệ tối ưu: Zone -> Rack -> Bin -> FEFO (Hạn dùng).
 */

import dayjs from 'dayjs'
import type { BinLocation } from '../warehouses/types'
import type { PickingItem } from './pickingTypes'
import type { SalesOrder } from './types'

export interface ProductLotInfo {
  productId: number
  lotNo?: string
  mfgDate?: string
  expDate?: string
  binLocationId?: number
  binLocationCode?: string
  zone?: string
  rack?: string
  bin?: string
  availableQuantity?: number
}

/**
 * Trích xuất thứ tự số học tự nhiên để sắp xếp Rack, Bin (ví dụ: '01' < '02' < '10').
 */
function extractNaturalNumber(str: string): number {
  const match = str.match(/\d+/)
  return match ? parseInt(match[0], 10) : 0
}

/**
 * So sánh 2 vị trí ô kệ theo thứ tự: Zone -> Rack -> Bin -> Hạn dùng (FEFO).
 */
export function comparePickingLocations(a: PickingItem, b: PickingItem): number {
  // 1. So sánh Zone (Khu vực, ví dụ: 'A' vs 'B')
  const zoneA = (a.zone || '').trim().toUpperCase()
  const zoneB = (b.zone || '').trim().toUpperCase()
  if (zoneA !== zoneB) {
    if (!zoneA) return 1
    if (!zoneB) return -1
    return zoneA.localeCompare(zoneB)
  }

  // 2. So sánh Rack (Dãy kệ, ví dụ: '01' vs '02')
  const rackA = (a.rack || '').trim()
  const rackB = (b.rack || '').trim()
  if (rackA !== rackB) {
    const numA = extractNaturalNumber(rackA)
    const numB = extractNaturalNumber(rackB)
    if (numA !== numB) return numA - numB
    return rackA.localeCompare(rackB)
  }

  // 3. So sánh Bin (Ô kệ, ví dụ: '01' vs '02')
  const binA = (a.bin || '').trim()
  const binB = (b.bin || '').trim()
  if (binA !== binB) {
    const numA = extractNaturalNumber(binA)
    const numB = extractNaturalNumber(binB)
    if (numA !== numB) return numA - numB
    return binA.localeCompare(binB)
  }

  // 4. Nếu trùng vị trí ô kệ, ưu tiên lô có Hạn dùng gần nhất (FEFO)
  if (a.expDate && b.expDate) {
    const dateA = dayjs(a.expDate)
    const dateB = dayjs(b.expDate)
    if (dateA.isValid() && dateB.isValid()) {
      return dateA.diff(dateB)
    }
  }

  return a.skuCode.localeCompare(b.skuCode)
}

/**
 * Tạo danh sách lộ trình lấy hàng thông minh (Smart Picking List) cho một Đơn bán hàng (SO).
 * Tự động gán vị trí ô kệ tối ưu và đánh số thứ tự chặng di chuyển (Step 1, Stop 2...).
 */
export function buildOptimizedPickingList(
  order: SalesOrder,
  warehouseBins: BinLocation[] = [],
  productLots: ProductLotInfo[] = []
): PickingItem[] {
  if (!order || !order.items || order.items.length === 0) {
    return []
  }

  // Tạo map tra cứu vị trí ô kệ theo ID và mã
  const binMapById = new Map<number, BinLocation>()
  warehouseBins.forEach((b) => binMapById.set(b.id, b))

  // Nhóm các lô theo productId
  const lotsByProduct = new Map<number, ProductLotInfo[]>()
  productLots.forEach((lot) => {
    const arr = lotsByProduct.get(lot.productId) || []
    arr.push(lot)
    lotsByProduct.set(lot.productId, arr)
  })

  const rawItems: PickingItem[] = []

  order.items.forEach((item, index) => {
    const availableLots = lotsByProduct.get(item.productId) || []

    if (availableLots.length > 0) {
      // Có thông tin lô hàng cụ thể trong kho: dùng vị trí của lô
      const bestLot = availableLots[0]
      const binObj = bestLot.binLocationId ? binMapById.get(bestLot.binLocationId) : undefined

      const zone = bestLot.zone || binObj?.zone || 'A'
      const rack = bestLot.rack || binObj?.rack || '01'
      const bin = bestLot.bin || binObj?.bin || '01'
      const locationCode = bestLot.binLocationCode || binObj?.code || `${zone}-${rack}-${bin}`

      rawItems.push({
        id: `pick-${item.id}-${bestLot.binLocationId || index}`,
        soItemId: item.id,
        productId: item.productId,
        skuCode: item.skuCode,
        productName: item.productName,
        uom: item.uom || 'Cái',
        orderedQuantity: item.quantity,
        pickedQuantity: 0,
        isPicked: false,
        locationId: bestLot.binLocationId || binObj?.id,
        locationCode,
        zone,
        rack,
        bin,
        lotNo: bestLot.lotNo,
        mfgDate: bestLot.mfgDate,
        expDate: bestLot.expDate,
        step: 0,
        note: item.note,
      })
    } else {
      // Nếu chưa có bảng lô cụ thể, phân bổ theo danh sách ô kệ có sẵn trong kho
      const fallbackBin = warehouseBins.length > 0 ? warehouseBins[index % warehouseBins.length] : undefined
      const zone = fallbackBin?.zone || (index % 2 === 0 ? 'A' : 'B')
      const rack = fallbackBin?.rack || String((index % 5) + 1).padStart(2, '0')
      const bin = fallbackBin?.bin || String((index % 8) + 1).padStart(2, '0')
      const locationCode = fallbackBin?.code || `${zone}-${rack}-${bin}`

      rawItems.push({
        id: `pick-${item.id}-${fallbackBin?.id || index}`,
        soItemId: item.id,
        productId: item.productId,
        skuCode: item.skuCode,
        productName: item.productName,
        uom: item.uom || 'Cái',
        orderedQuantity: item.quantity,
        pickedQuantity: 0,
        isPicked: false,
        locationId: fallbackBin?.id,
        locationCode,
        zone,
        rack,
        bin,
        step: 0,
        note: item.note,
      })
    }
  })

  // Sắp xếp lộ trình theo thứ tự tối ưu hóa: Zone -> Rack -> Bin -> FEFO
  rawItems.sort(comparePickingLocations)

  // Đánh số thứ tự chặng dừng (Stop 1, Stop 2...)
  return rawItems.map((it, idx) => ({
    ...it,
    step: idx + 1,
  }))
}
