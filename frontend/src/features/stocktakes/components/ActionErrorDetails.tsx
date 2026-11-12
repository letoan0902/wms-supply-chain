import { useMemo } from 'react'
import { getApiErrorMessage, getErrorStatus } from '../../../services/api'
import { frozenLocationsOf } from '../../../services/stockDocuments'
import { formatQty, reservedConflictsOf } from '../stocktakeLogic'
import { productLabel, useProductBriefs } from '../useProductBriefs'

interface Props {
  error: unknown
  fallback: string
  /** Giải thích riêng cho lỗi 403 của thao tác này (ví dụ Kế toán duyệt trùng người duyệt cấp quản lý) */
  forbiddenHint?: string
}

/**
 * Nội dung lỗi của một thao tác kiểm kê: thông báo tiếng Việt của máy chủ và chi tiết trong `data` của lỗi 409
 * (vị trí đang thuộc đợt kiểm kê khác; các dòng mà tồn sau điều chỉnh nhỏ hơn số đang giữ chỗ hoặc âm).
 */
export default function ActionErrorDetails({ error, fallback, forbiddenHint }: Props) {
  const status = getErrorStatus(error)
  const message = getApiErrorMessage(error, fallback)
  const frozen = status === 409 ? frozenLocationsOf(error) : []
  const reserved = useMemo(() => reservedConflictsOf(error), [error])
  const products = useProductBriefs(
    useMemo(() => reserved.map((r) => r.productId).filter((id): id is number => id !== null), [reserved]),
  )

  return (
    <div className="flex flex-col gap-2">
      {message && <div>{message}</div>}
      {frozen.length > 0 && (
        <ul className="list-disc pl-5 text-sm">
          {frozen.map((f, index) => (
            <li key={`${f.binLocationId ?? f.binLocationCode}-${index}`}>
              Vị trí <span className="font-mono font-semibold">{f.binLocationCode || '?'}</span> đang thuộc đợt kiểm kê{' '}
              <span className="font-mono font-semibold">{f.sessionCode || '?'}</span> chưa kết thúc.
            </li>
          ))}
        </ul>
      )}
      {reserved.length > 0 && (
        <>
          <ul className="list-disc pl-5 text-sm max-h-60 overflow-auto">
            {reserved.map((r, index) => (
              <li key={`${r.lineId ?? index}-${index}`}>
                <span className="font-mono">{r.binLocationCode || '?'}</span> ·{' '}
                {r.productId !== null ? productLabel(products, r.productId) : 'Sản phẩm không rõ'}
                {r.lotNo ? ` · Lô ${r.lotNo}` : ''}: tồn sau điều chỉnh {formatQty(r.newQuantity)}, đang giữ chỗ{' '}
                {formatQty(r.reservedQuantity)}
              </li>
            ))}
          </ul>
          <div className="text-xs text-slate-500">
            Cần hủy hoặc chuyển giữ chỗ của đơn bán liên quan, hoặc yêu cầu đếm lại các dòng này, rồi duyệt lại.
          </div>
        </>
      )}
      {status === 403 && (
        <div className="text-xs text-slate-500">
          {forbiddenHint ??
            'Máy chủ từ chối vì vai trò của bạn không được làm thao tác này, hoặc bạn không phụ trách kho của đợt kiểm kê.'}
        </div>
      )}
      {status === 409 && frozen.length === 0 && reserved.length === 0 && (
        <div className="text-xs text-slate-500">
          Trạng thái đợt có thể vừa được người khác thay đổi. Màn hình đã tải lại đợt kiểm kê.
        </div>
      )}
    </div>
  )
}
