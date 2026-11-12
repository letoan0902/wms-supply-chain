import axios from 'axios'
import { useCallback, useEffect, useState } from 'react'
import { fetchStocktake } from './stocktakeApi'
import type { StocktakeSession } from './types'

interface State {
  key: string
  session: StocktakeSession | null
  error: unknown
}

/**
 * Đợt kiểm kê theo ID (GET /api/inventory/stocktakes/{id}). `reload` tải lại; `replace` nhận đợt do một thao tác
 * ghi trả về (bắt đầu, kết thúc, duyệt...) để khỏi gọi lại máy chủ. Trong lúc tải lại vẫn giữ dữ liệu cũ.
 */
export function useStocktakeSession(id: number | null) {
  const [token, setToken] = useState(0)
  const key = id === null ? null : `${id}#${token}`
  const [state, setState] = useState<State | null>(null)

  useEffect(() => {
    if (id === null || key === null) return
    const controller = new AbortController()
    fetchStocktake(id, controller.signal)
      .then((session) => setState({ key, session, error: null }))
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setState((prev) => ({ key, session: prev?.session ?? null, error }))
      })
    return () => controller.abort()
  }, [id, key])

  const reload = useCallback(() => setToken((t) => t + 1), [])
  const replace = useCallback(
    (session: StocktakeSession) => {
      if (key !== null) setState({ key, session, error: null })
    },
    [key],
  )

  const current = key !== null && state?.key === key
  const session = state?.session && state.session.id === id ? state.session : null
  return {
    session,
    loading: key !== null && !current,
    error: current ? (state?.error ?? null) : null,
    reload,
    replace,
  }
}
