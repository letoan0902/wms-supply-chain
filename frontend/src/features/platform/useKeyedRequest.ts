import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../services/api'

interface RequestState<T> {
  key: string
  data: T | null
  error: string | null
}

export interface KeyedRequest<T> {
  /** Kết quả thành công gần nhất (có thể của bộ lọc trước trong lúc đang tải bộ lọc mới, để bảng không bị trống) */
  data: T | null
  /** Lỗi của lần gọi ứng với khóa hiện tại */
  error: string | null
  loading: boolean
}

/**
 * Gọi API mỗi khi `key` đổi (bộ lọc, trang, lần tải lại), cùng cách với useDashboardRequest nhưng giữ dữ liệu cũ
 * trong lúc tải để bảng phân trang không nhấp nháy. `key` bằng null: không gọi. `load` phải ổn định (useCallback)
 * hoặc đổi cùng `key`.
 */
export function useKeyedRequest<T>(key: string | null, load: () => Promise<T>, fallbackError: string): KeyedRequest<T> {
  const [state, setState] = useState<RequestState<T> | null>(null)
  const [lastData, setLastData] = useState<T | null>(null)

  useEffect(() => {
    if (key === null) return
    let cancelled = false
    load()
      .then((data) => {
        if (cancelled) return
        setState({ key, data, error: null })
        setLastData(data)
      })
      .catch((error: unknown) => {
        if (!cancelled) setState({ key, data: null, error: getApiErrorMessage(error, fallbackError) || fallbackError })
      })
    return () => {
      cancelled = true
    }
  }, [key, load, fallbackError])

  if (key === null) return { data: null, error: null, loading: false }
  const settled = state !== null && state.key === key
  return {
    data: settled ? (state.data ?? lastData) : lastData,
    error: settled ? state.error : null,
    loading: !settled,
  }
}
