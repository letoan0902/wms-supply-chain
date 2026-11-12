import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../services/api'

interface RequestState<T> {
  key: string
  data: T | null
  error: string | null
}

export interface DashboardRequest<T> {
  data: T | null
  error: string | null
  loading: boolean
}

/**
 * Gọi một API của dashboard mỗi khi `key` đổi (kho, mốc thời gian, lần thử lại) và giữ kết quả theo khóa, để trạng thái
 * đang tải được suy ra từ việc chưa có kết quả cho khóa hiện tại thay vì đặt state đồng bộ trong effect.
 * `key` bằng null: chưa đủ điều kiện gọi (ví dụ danh sách kho chưa tải xong). `load` phải ổn định (useCallback).
 */
export function useDashboardRequest<T>(
  key: string | null,
  load: () => Promise<T>,
  fallbackError: string,
): DashboardRequest<T> {
  const [state, setState] = useState<RequestState<T> | null>(null)

  useEffect(() => {
    if (key === null) return
    let cancelled = false
    load()
      .then((data) => {
        if (!cancelled) setState({ key, data, error: null })
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          // Lỗi đã có hộp thoại dùng chung (hết hạn gói, vượt giới hạn) thì getApiErrorMessage trả chuỗi rỗng
          setState({ key, data: null, error: getApiErrorMessage(error, fallbackError) || fallbackError })
        }
      })
    return () => {
      cancelled = true
    }
  }, [key, load, fallbackError])

  if (key === null || state === null || state.key !== key) {
    return { data: null, error: null, loading: true }
  }
  return { data: state.data, error: state.error, loading: false }
}
