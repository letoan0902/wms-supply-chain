import { Select } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { fetchProducts } from '../../products/productApi'
import type { Product } from '../../products/types'
import type { ProductOption } from '../types'
import { useDebouncedValue } from '../useDebouncedValue'

interface Props {
  value?: number
  /** Sản phẩm đang chọn (để vẫn hiển thị nhãn khi không nằm trong kết quả tìm gần nhất) */
  selected?: ProductOption | null
  onChange: (product: ProductOption | null) => void
  placeholder?: string
  allowClear?: boolean
  className?: string
  status?: 'error'
}

const SEARCH_SIZE = 20

function toOption(p: Product): ProductOption {
  return { id: p.id, sku: p.sku, name: p.name, uomCode: p.uomCode }
}

/** Chọn một sản phẩm, tìm theo mã SKU hoặc tên trên máy chủ (debounce, bỏ qua kết quả cũ). */
export default function ProductSelect({ value, selected, onChange, placeholder, allowClear = true, className, status }: Props) {
  const [search, setSearch] = useState('')
  const keyword = useDebouncedValue(search.trim(), 400)
  const [result, setResult] = useState<{ key: string; items: ProductOption[]; error: string | null } | null>(null)

  useEffect(() => {
    let ignore = false
    fetchProducts({ keyword: keyword || undefined, page: 0, size: SEARCH_SIZE })
      .then((page) => {
        if (!ignore) setResult({ key: keyword, items: (page.content ?? []).map(toOption), error: null })
      })
      .catch((err) => {
        if (!ignore) setResult({ key: keyword, items: [], error: getApiErrorMessage(err, 'Không tải được danh sách sản phẩm') })
      })
    return () => {
      ignore = true
    }
  }, [keyword])

  const loading = result?.key !== keyword || search.trim() !== keyword
  const items = useMemo(() => {
    const list = result?.items ?? []
    if (selected && !list.some((p) => p.id === selected.id)) return [selected, ...list]
    return list
  }, [result, selected])

  return (
    <Select<number>
      showSearch
      allowClear={allowClear}
      value={value}
      className={className}
      status={status}
      placeholder={placeholder ?? 'Tìm theo mã SKU hoặc tên sản phẩm'}
      filterOption={false}
      onSearch={setSearch}
      loading={loading}
      notFoundContent={loading ? 'Đang tìm...' : result?.error ?? 'Không có sản phẩm phù hợp'}
      options={items.map((p) => ({ value: p.id, label: `${p.sku} - ${p.name}` }))}
      onChange={(id) => {
        onChange(id === undefined || id === null ? null : (items.find((p) => p.id === id) ?? null))
        setSearch('')
      }}
      onClear={() => onChange(null)}
    />
  )
}
