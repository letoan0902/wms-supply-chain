import axios from 'axios'
import { useCallback, useEffect, useState } from 'react'
import { fetchCountLines } from './stocktakeApi'
import { mergeCountLine } from './countOps'
import type { StocktakeCountLine, StocktakeLocation } from './types'

/** Cỡ trang lớn nhất máy chủ cho phép và số trang tối đa tải cho một vị trí. */
const PAGE_SIZE = 100
const MAX_PAGES = 20

interface State {
  key: string
  binLocationId: number
  lines: StocktakeCountLine[]
  error: unknown
  truncated: boolean
}

/**
 * Mọi dòng đếm của một vị trí (GET .../count-lines?binCode=...). Bộ lọc `binCode` của máy chủ là "chứa chuỗi" nên
 * kết quả được lọc lại đúng `binLocationId`. Không có tồn hệ thống. `applyLine` cập nhật một dòng vừa ghi số đếm
 * mà không phải tải lại.
 */
export function useLocationLines(sessionId: number | null, location: StocktakeLocation | null) {
  const [token, setToken] = useState(0)
  const key = sessionId !== null && location ? `${sessionId}|${location.binLocationId}#${token}` : null
  const [state, setState] = useState<State | null>(null)

  useEffect(() => {
    if (sessionId === null || !location || key === null) return
    const controller = new AbortController()
    const load = async () => {
      const collected: StocktakeCountLine[] = []
      let truncated = false
      for (let page = 0; page < MAX_PAGES; page++) {
        const res = await fetchCountLines(
          sessionId,
          { binCode: location.binLocationCode, page, size: PAGE_SIZE },
          controller.signal,
        )
        collected.push(...(res.content ?? []).filter((l) => l.binLocationId === location.binLocationId))
        if (res.last || (res.content ?? []).length === 0) break
        if (page === MAX_PAGES - 1) truncated = true
      }
      return { lines: collected, truncated }
    }
    load()
      .then(({ lines, truncated }) =>
        setState((prev) => {
          // Một lần ghi số đếm có thể trả về sau khi máy chủ đã trả danh sách này: giữ bản có số lần ghi lớn hơn
          const newer = new Map<number, StocktakeCountLine>()
          if (prev && prev.binLocationId === location.binLocationId) {
            prev.lines.forEach((l) => newer.set(l.lineId, l))
          }
          const merged = lines.map((l) => {
            const local = newer.get(l.lineId)
            return local && local.countTimes > l.countTimes ? local : l
          })
          return { key, binLocationId: location.binLocationId, lines: merged, error: null, truncated }
        }),
      )
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) {
          setState({ key, binLocationId: location.binLocationId, lines: [], error, truncated: false })
        }
      })
    return () => controller.abort()
  }, [sessionId, location, key])

  const reload = useCallback(() => setToken((t) => t + 1), [])
  const applyLine = useCallback((line: StocktakeCountLine) => {
    setState((prev) => (prev ? { ...prev, lines: mergeCountLine(prev.lines, line, prev.binLocationId) } : prev))
  }, [])

  const current = key !== null && state?.key === key
  const sameLocation = state !== null && location !== null && state.binLocationId === location.binLocationId
  return {
    lines: sameLocation ? state.lines : [],
    loading: key !== null && !current,
    error: current ? (state?.error ?? null) : null,
    truncated: sameLocation ? state.truncated : false,
    reload,
    applyLine,
  }
}
