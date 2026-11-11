/**
 * REST API Client cho phân hệ Bảng xuất kho thời gian thực & Lộ trình lấy hàng (WMS-12.3 - FR17).
 *
 * Màn hình chỉ ĐỌC tồn theo lô/ô kệ (GET /api/inventory/lots) để lập lộ trình lấy hàng.
 * Xác nhận xuất kho và trừ tồn thuộc luồng phiếu xuất kho của order-service (WMS-12.1, chưa có API):
 * không gọi thẳng /api/inventory/stock/deduct hay /dispatch từ trình duyệt, vì đơn bán không được cập nhật
 * trạng thái nên có thể bị xuất lại và trừ tồn hai lần.
 */

import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import { buildOptimizedPickingList, type ProductLotInfo } from './pickingOptimizer'
import type { PickingItem } from './pickingTypes'
import type { SalesOrder } from './types'

/** Khớp vn.wms.inventory.dto.LotResponse */
interface LotApiResponseItem {
  stockItemId: number
  warehouseId?: number
  productId: number
  lotNo?: string
  mfgDate?: string
  expDate?: string
  binLocationId?: number
  binLocationCode?: string
  zone?: string
  rack?: string
  bin?: string
  quantity?: number
  reservedQuantity?: number
  availableQuantity?: number
  locked?: boolean
  expiryStatus?: string
}

/** inventory-service giới hạn tối đa 100 bản ghi một trang (spring.data.web.pageable.max-page-size) */
const LOT_PAGE_SIZE = 100
const MAX_LOT_PAGES = 20

async function fetchPickableLotsOfProduct(
  warehouseId: number,
  productId: number
): Promise<ProductLotInfo[]> {
  const result: ProductLotInfo[] = []
  for (let page = 0; page < MAX_LOT_PAGES; page++) {
    const res = await api.get<ApiResponse<PageResponse<LotApiResponseItem>>>('/inventory/lots', {
      params: {
        warehouseId,
        productId,
        hasStock: true,
        locked: false,
        // GOOD = chưa hết hạn hoặc không có HSD, cùng điều kiện với lúc trừ tồn
        expiryStatus: 'GOOD',
        page,
        size: LOT_PAGE_SIZE,
      },
    })
    const data = res.data.data
    ;(data?.content || []).forEach((it) => {
      result.push({
        stockItemId: it.stockItemId,
        productId: it.productId,
        lotNo: it.lotNo,
        mfgDate: it.mfgDate,
        expDate: it.expDate,
        binLocationId: it.binLocationId,
        binLocationCode: it.binLocationCode,
        zone: it.zone,
        rack: it.rack,
        bin: it.bin,
        quantity: it.quantity,
        availableQuantity: it.availableQuantity,
        locked: it.locked,
      })
    })
    if (!data || data.last || (data.content || []).length === 0) break
  }
  return result
}

/**
 * Tải các lô còn xuất được của từng sản phẩm tại một kho để tối ưu vị trí lấy hàng theo FEFO.
 * Lỗi API được ném ra để màn hình báo lỗi, không thay bằng dữ liệu giả.
 */
export async function fetchPickableLots(
  warehouseId: number,
  productIds: number[]
): Promise<ProductLotInfo[]> {
  const uniqueIds = Array.from(new Set(productIds.filter((id) => id != null)))
  const lists = await Promise.all(uniqueIds.map((id) => fetchPickableLotsOfProduct(warehouseId, id)))
  return lists.flat()
}

interface BackendPickingItemResponse {
  step: number
  productId: number
  skuCode: string
  productName: string
  uom?: string
  binLocationId?: number
  locationCode?: string
  zone?: string
  rack?: string
  bin?: string
  lotNo?: string
  expDate?: string
  mfgDate?: string
  quantityToPick: number
  availableQuantity?: number
}

/** Lập lộ trình lấy hàng của một đơn bán: ưu tiên API gợi ý từ backend (WMS-12.1), fallback sang FE optimizer */
export async function loadPickingRoute(order: SalesOrder): Promise<PickingItem[]> {
  if (!order.warehouseId) {
    throw new Error('Đơn bán chưa gán kho xuất hàng nên chưa lập được lộ trình lấy hàng')
  }

  // 1. Thử lấy danh sách lấy hàng tối ưu từ API backend (WMS-12.1: /orders/sales/{id}/picking-list)
  try {
    const res = await api.get<ApiResponse<{ items?: BackendPickingItemResponse[] }>>(
      `/orders/sales/${order.id}/picking-list`
    )
    const data = res.data?.data
    if (data && Array.isArray(data.items) && data.items.length > 0) {
      const soItemMap = new Map<number, number>()
      ;(order.items || []).forEach((it) => {
        soItemMap.set(it.productId, it.id)
      })

      return data.items.map((it: BackendPickingItemResponse, idx: number) => ({
        id: `pick-${soItemMap.get(it.productId) || idx}-${it.binLocationId || idx}-${it.lotNo || 'nolot'}-${idx}`,
        soItemId: soItemMap.get(it.productId) || 0,
        productId: it.productId,
        skuCode: it.skuCode,
        productName: it.productName,
        uom: it.uom || 'Cái',
        orderedQuantity: it.quantityToPick,
        pickedQuantity: 0,
        isPicked: false,
        shortage: false,
        locationId: it.binLocationId,
        locationCode: it.locationCode || `${it.zone || 'A'}-${it.rack || '01'}-${it.bin || '01'}`,
        zone: it.zone || 'A',
        rack: it.rack || '01',
        bin: it.bin || '01',
        lotNo: it.lotNo,
        mfgDate: it.mfgDate,
        expDate: it.expDate,
        step: it.step || idx + 1,
      }))
    }
  } catch (err) {
    // Nếu backend chưa deploy hoặc gặp lỗi tạm thời, tiếp tục với bộ tối ưu hóa phía client
    console.debug('Không thể tải picking-list từ order-service, tiếp tục với FE optimizer:', err)
  }

  // 2. Fallback: Tải tồn theo lô/vị trí và tối ưu hóa phía frontend
  const lots = await fetchPickableLots(
    order.warehouseId,
    (order.items || []).map((it) => it.productId)
  )
  return buildOptimizedPickingList(order, lots)
}
