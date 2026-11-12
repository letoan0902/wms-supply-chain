import { RedoOutlined } from '@ant-design/icons'
import { Alert, Button } from 'antd'
import type { QueueItem } from '../countQueue'
import { describeOp, type CountOp } from '../countOps'

interface Props {
  items: readonly QueueItem<CountOp>[]
  onRetry: (item: QueueItem<CountOp>) => void
  onDiscard: (item: QueueItem<CountOp>) => void
}

/**
 * Các lần ghi số đếm chưa thành công: không bao giờ tự gửi lại hay tự xóa; người dùng bấm "Thử lại" hoặc "Bỏ".
 * Lần ghi không rõ kết quả (mất mạng) có cảnh báo riêng vì thử lại có thể cộng hai lần.
 */
export default function QueueFailures({ items, onRetry, onDiscard }: Props) {
  const failed = items.filter((item) => item.status === 'failed' || item.status === 'unknown')
  if (failed.length === 0) return null
  return (
    <Alert
      type="error"
      showIcon
      message={`Lần ghi chưa thành công (${failed.length})`}
      description={
        <ul className="flex flex-col gap-2 mt-1">
          {failed.map((item) => (
            <li key={item.id} className="flex flex-col gap-1 border-t border-red-100 pt-2 first:border-0 first:pt-0">
              <span className="text-sm font-medium break-words">{describeOp(item.payload)}</span>
              <span className="text-xs break-words">
                {item.status === 'unknown'
                  ? 'Không rõ máy chủ đã ghi hay chưa (mất kết nối hoặc hết thời gian chờ). Danh sách đã được tải lại: kiểm tra số đã đếm của dòng trước khi thử lại, vì thử lại có thể cộng thêm lần nữa.'
                  : item.error || 'Máy chủ chưa ghi số đếm này.'}
              </span>
              <div className="flex gap-2">
                <Button size="large" icon={<RedoOutlined />} onClick={() => onRetry(item)} data-write-action>
                  Thử lại
                </Button>
                <Button size="large" onClick={() => onDiscard(item)}>
                  Bỏ
                </Button>
              </div>
            </li>
          ))}
        </ul>
      }
    />
  )
}
