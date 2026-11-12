import { Select } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { useDebouncedValue } from '../../inventory/useDebouncedValue'
import { fetchProducts } from '../../products/productApi'

export interface ProductOptionItem {
  id: number
  sku: string
  name: string
}

interface Props {
  value: ProductOptionItem[]
  onChange: (value: ProductOptionItem[]) => void
  /** Số sản phẩm tối đa được chọn */
  max: number
  placeholder?: string
}

const SEARCH_SIZE = 20

/** Chọn nhiều sản phẩm, tìm theo SKU, tên hoặc mã vạch trên product-service (debounce, bỏ qua kết quả cũ). */
export default function ProductMultiSelect({ value, onChange, max, placeholder }: Props) {
  const [search, setSearch] = useState('')
  const keyword = useDebouncedValue(search.trim(), 400)
  const [result, setResult] = useState<{ key: string; items: ProductOptionItem[]; error: string | null } | null>(null)

  useEffect(() => {
    let ignore = false
    fetchProducts({ keyword: keyword || undefined, active: true, page: 0, size: SEARCH_SIZE })
      .then((page) => {
        if (!ignore) {
          setResult({
            key: keyword,
            items: (page.content ?? []).map((p) => ({ id: p.id, sku: p.sku, name: p.name })),
            error: null,
          })
        }
      })
      .catch((err) => {
        if (!ignore) setResult({ key: keyword, items: [], error: getApiErrorMessage(err, 'Không tải được danh sách hàng hóa') })
      })
    return () => {
      ignore = true
    }
  }, [keyword])

  const loading = result?.key !== keyword || search.trim() !== keyword
  const options = useMemo(() => {
    const byId = new Map<number, ProductOptionItem>()
    value.forEach((p) => byId.set(p.id, p))
    ;(result?.items ?? []).forEach((p) => byId.set(p.id, p))
    return [...byId.values()].map((p) => ({ value: p.id, label: `${p.sku} - ${p.name}` }))
  }, [result, value])

  return (
    <Select<number[]>
      mode="multiple"
      allowClear
      showSearch
      value={value.map((p) => p.id)}
      placeholder={placeholder ?? 'Để trống: kiểm mọi hàng hóa trong phạm vi'}
      filterOption={false}
      onSearch={setSearch}
      loading={loading}
      maxCount={max}
      notFoundContent={loading ? 'Đang tìm...' : (result?.error ?? 'Không có hàng hóa phù hợp')}
      options={options}
      onChange={(ids) => {
        const known = new Map<number, ProductOptionItem>()
        value.forEach((p) => known.set(p.id, p))
        ;(result?.items ?? []).forEach((p) => known.set(p.id, p))
        onChange(ids.map((id) => known.get(id)).filter((p): p is ProductOptionItem => p !== undefined))
        setSearch('')
      }}
      style={{ width: '100%' }}
    />
  )
}
