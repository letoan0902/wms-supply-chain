import axios from 'axios'
import { useEffect, useState } from 'react'

interface QueryState<T> {
  key: string
  data: T | null
  error: unknown
}

/**
 * Gọi một API báo cáo mỗi khi tham số đổi. Request cũ bị hủy (AbortController) và kết quả chỉ được nhận khi khớp
 * đúng bộ tham số hiện tại, nên đổi bộ lọc nhanh không làm hiện số liệu cũ. Trong lúc tải vẫn giữ dữ liệu lần trước
 * để bảng không bị nháy.
 *
 * @param fetcher     hàm gọi API (định nghĩa ở cấp module để không đổi tham chiếu)
 * @param params      tham số đã useMemo; null nghĩa là chưa đủ điều kiện gọi
 * @param reloadToken tăng lên để tải lại với cùng tham số
 */
export function useReportQuery<P, T>(
  fetcher: (params: P, signal: AbortSignal) => Promise<T>,
  params: P | null,
  reloadToken = 0,
) {
  const key = params === null ? null : `${JSON.stringify(params)}#${reloadToken}`
  const [state, setState] = useState<QueryState<T> | null>(null)

  useEffect(() => {
    if (params === null || key === null) return
    const controller = new AbortController()
    fetcher(params, controller.signal)
      .then((data) => setState({ key, data, error: null }))
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setState({ key, data: null, error })
      })
    return () => controller.abort()
  }, [fetcher, params, key])

  const isCurrent = key !== null && state?.key === key
  return {
    /** Dữ liệu lần tải thành công gần nhất (có thể của bộ lọc trước trong lúc đang tải) */
    data: key === null ? null : (state?.data ?? null),
    loading: key !== null && !isCurrent,
    error: isCurrent ? (state?.error ?? null) : null,
  }
}
