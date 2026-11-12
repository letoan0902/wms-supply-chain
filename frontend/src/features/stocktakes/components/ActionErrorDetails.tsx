import { getApiErrorMessage, getErrorStatus } from '../../../services/api'
import { frozenLocationsOf } from '../../../services/stockDocuments'

interface Props {
  error: unknown
  fallback: string
}

/**
 * Nội dung lỗi của một thao tác kiểm kê: thông báo tiếng Việt của máy chủ và chi tiết trong `data` của lỗi 409
 * (vị trí đang thuộc đợt kiểm kê khác).
 */
export default function ActionErrorDetails({ error, fallback }: Props) {
  const status = getErrorStatus(error)
  const message = getApiErrorMessage(error, fallback)
  const frozen = status === 409 ? frozenLocationsOf(error) : []

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
      {status === 403 && (
        <div className="text-xs text-slate-500">
          Máy chủ từ chối vì vai trò của bạn không được làm thao tác này, hoặc bạn không phụ trách kho của đợt kiểm kê.
        </div>
      )}
      {status === 409 && frozen.length === 0 && (
        <div className="text-xs text-slate-500">
          Trạng thái đợt có thể vừa được người khác thay đổi. Màn hình đã tải lại đợt kiểm kê.
        </div>
      )}
    </div>
  )
}
