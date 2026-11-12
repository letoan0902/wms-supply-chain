import { App } from 'antd'
import { useCallback, useRef, useState } from 'react'
import { getApiErrorMessage } from '../../services/api'
import { errorMessageOf, errorStatusOf, frozenLocationsOf, isOutcomeUnknown } from '../../services/stockDocuments'
import { FrozenLocationList } from './components/StockErrorDetails'
import * as gdnApi from './gdnApi'
import type { GdnResponse } from './gdnTypes'

/** Phiếu xuất còn chờ trừ tồn: bấm "Xác nhận xuất kho" được. */
export function isAwaitingDispatch(gdn: Pick<GdnResponse, 'status'> | null | undefined): boolean {
  return gdn?.status === 'DRAFT' || gdn?.status === 'PICKING'
}

/**
 * Xác nhận xuất kho một phiếu DRAFT hoặc PICKING (`POST /api/orders/goods-delivery-notes/{id}/dispatch`): máy chủ trừ
 * tồn đồng bộ rồi mới chuyển phiếu sang COMPLETED, và chỉ trừ một lần cho mỗi phiếu nên bấm lại sau lỗi là an toàn
 * (docs/api/CHUNG_TU_TON_KHO.md mục 3). Trả phiếu sau khi xuất, hoặc null nếu chưa xuất được; nơi gọi tải lại danh
 * sách trong cả hai trường hợp.
 */
export function useGdnDispatch() {
  const { message, modal } = App.useApp()
  const [dispatchingId, setDispatchingId] = useState<number | null>(null)
  const dispatchingRef = useRef(false)

  const dispatchGdn = useCallback(
    async (gdn: GdnResponse): Promise<GdnResponse | null> => {
      if (dispatchingRef.current) return null
      dispatchingRef.current = true
      setDispatchingId(gdn.id)
      try {
        const updated = await gdnApi.dispatchGoodsDelivery(gdn.id)
        message.success(`Đã xuất kho phiếu ${updated.gdnNumber}, tồn kho đã được trừ theo phiếu này.`)
        return updated
      } catch (err) {
        const status = errorStatusOf(err)
        const frozen = status === 409 ? frozenLocationsOf(err) : []
        if (frozen.length > 0) {
          modal.error({
            title: `Chưa xuất kho được phiếu ${gdn.gdnNumber}`,
            width: 560,
            content: (
              <div className="space-y-1">
                <div>{errorMessageOf(err)}</div>
                <FrozenLocationList locations={frozen} />
                <div className="text-xs text-slate-500">
                  Phiếu giữ nguyên trạng thái. Bấm "Xác nhận xuất kho" lại sau khi đợt kiểm kê kết thúc.
                </div>
              </div>
            ),
          })
        } else if (status === 503 || isOutcomeUnknown(err)) {
          modal.warning({
            title: `Chưa xác nhận được việc trừ tồn cho phiếu ${gdn.gdnNumber}`,
            content: `${errorMessageOf(err) || 'Không nhận được phản hồi từ máy chủ.'} Bấm "Xác nhận xuất kho" lại là an toàn: tồn kho chỉ bị trừ một lần cho mỗi phiếu.`,
          })
        } else {
          message.error(getApiErrorMessage(err, `Không xuất kho được phiếu ${gdn.gdnNumber}`))
        }
        return null
      } finally {
        dispatchingRef.current = false
        setDispatchingId(null)
      }
    },
    [message, modal]
  )

  return { dispatchingId, dispatchGdn }
}
