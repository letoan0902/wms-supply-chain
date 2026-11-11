import { useEffect, useState } from 'react'

/** Giá trị chỉ đổi sau khi `value` đứng yên `delayMs` mili giây (dùng cho ô tìm kiếm). */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}
