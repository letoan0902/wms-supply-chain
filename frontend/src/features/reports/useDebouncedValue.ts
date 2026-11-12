import { useEffect, useState } from 'react'

/** Trả về giá trị chỉ cập nhật sau khi `value` ngừng thay đổi `delayMs` mili giây (tránh gọi API theo từng phím). */
export function useDebouncedValue<T>(value: T, delayMs = 400): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}
