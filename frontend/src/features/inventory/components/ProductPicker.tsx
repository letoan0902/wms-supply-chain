import { Select, Spin } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { fetchProducts } from '../../products/productApi'

interface Option {
  value: number
  label: string
}

interface ProductPickerProps {
  value?: number
  onChange: (productId: number | undefined) => void
}

/** Thời gian chờ sau lần gõ cuối trước khi tìm sản phẩm. */
const SEARCH_DEBOUNCE_MS = 400

/**
 * Chọn một sản phẩm theo SKU hoặc tên (tìm ở product-service). Dùng cho bảng theo lô vì GET /api/inventory/lots chỉ
 * lọc được theo `productId`.
 */
export default function ProductPicker({ value, onChange }: ProductPickerProps) {
  const [options, setOptions] = useState<Option[]>([])
  const [searching, setSearching] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const requestRef = useRef(0)

  useEffect(
    () => () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    },
    [],
  )

  const handleSearch = (text: string) => {
    if (timerRef.current) clearTimeout(timerRef.current)
    const keyword = text.trim()
    if (!keyword) {
      setOptions((current) => current.filter((option) => option.value === value))
      return
    }
    timerRef.current = setTimeout(() => {
      const requestId = requestRef.current + 1
      requestRef.current = requestId
      setSearching(true)
      fetchProducts({ keyword, size: 20 })
        .then((page) => {
          if (requestRef.current !== requestId) return
          setOptions((page.content ?? []).map((p) => ({ value: p.id, label: `${p.sku} - ${p.name}` })))
        })
        .catch(() => {
          if (requestRef.current === requestId) setOptions([])
        })
        .finally(() => {
          if (requestRef.current === requestId) setSearching(false)
        })
    }, SEARCH_DEBOUNCE_MS)
  }

  return (
    <Select<number>
      showSearch
      allowClear
      value={value}
      placeholder="Hàng hóa (gõ SKU hoặc tên)"
      filterOption={false}
      onSearch={handleSearch}
      onChange={(next) => onChange(next ?? undefined)}
      notFoundContent={searching ? <Spin size="small" /> : 'Gõ để tìm hàng hóa'}
      options={options}
      style={{ width: '100%' }}
    />
  )
}
