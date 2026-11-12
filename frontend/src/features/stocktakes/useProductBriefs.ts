import { useEffect, useMemo, useState } from 'react'
import { fetchProductBriefs } from '../inventory/inventoryApi'
import type { ProductBrief } from '../inventory/types'

/**
 * SKU, tên, đơn vị của các sản phẩm đang hiển thị (dòng kiểm kê chỉ có `productId`). Dùng bộ nhớ đệm chung của
 * màn hình tồn kho nên mỗi sản phẩm chỉ gọi product-service một lần trong vài phút.
 */
export function useProductBriefs(ids: readonly number[]): Map<number, ProductBrief> {
  const key = useMemo(() => [...new Set(ids)].sort((a, b) => a - b).join(','), [ids])
  const [state, setState] = useState<{ key: string; map: Map<number, ProductBrief> }>({ key: '', map: new Map() })

  useEffect(() => {
    if (!key) return
    let ignore = false
    const list = key.split(',').map(Number)
    fetchProductBriefs(list).then((map) => {
      if (!ignore) {
        setState((prev) => {
          const merged = new Map(prev.map)
          map.forEach((value, id) => merged.set(id, value))
          return { key, map: merged }
        })
      }
    })
    return () => {
      ignore = true
    }
  }, [key])

  return state.map
}

/** Nhãn sản phẩm: `SKU - Tên`, chưa tải được thì `Sản phẩm ID n`. */
export function productLabel(map: Map<number, ProductBrief>, productId: number): string {
  const p = map.get(productId)
  return p ? `${p.sku} - ${p.name}` : `Sản phẩm ID ${productId}`
}
