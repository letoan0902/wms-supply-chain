import { EditOutlined, LoadingOutlined, PlusOutlined, WarningOutlined } from '@ant-design/icons'
import { Button, Tag } from 'antd'
import type { LineQueueState } from '../countOps'
import { formatQty } from '../stocktakeLogic'
import type { StocktakeCountLine } from '../types'

interface Props {
  line: StocktakeCountLine
  productText: string
  uomText: string | null
  queue: LineQueueState | undefined
  /** Được ghi số đếm (đúng vai trò và đợt đang đếm) */
  editable: boolean
  step: number
  onAdd: () => void
  onSet: () => void
}

/**
 * Một dòng cần đếm trên màn hình đếm mù: CHỈ hiện số đã đếm trong đợt này, không bao giờ hiện tồn hệ thống.
 * Nút lớn để bấm trên điện thoại hoặc máy quét cầm tay.
 */
export default function CountLineCard({ line, productText, uomText, queue, editable, step, onAdd, onSet }: Props) {
  const failed = queue?.failed.length ?? 0
  const pending = queue?.pending ?? 0
  return (
    <div
      className={`rounded-lg border p-3 flex flex-col gap-2 bg-white ${
        failed > 0 ? 'border-red-400' : line.counted ? 'border-emerald-300' : 'border-slate-200'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <div className="font-medium break-words">{productText}</div>
          <div className="flex flex-wrap items-center gap-1 mt-1">
            {line.lotNo && <Tag className="!m-0">Lô {line.lotNo}</Tag>}
            {line.unexpected && <Tag color="purple" className="!m-0">Hàng phát sinh</Tag>}
            {line.recountRequested && <Tag color="orange" className="!m-0">Đếm lại</Tag>}
          </div>
        </div>
        <div className="text-right shrink-0">
          {line.counted ? (
            <>
              <div className="text-2xl font-bold leading-none">{formatQty(line.countedQuantity)}</div>
              <div className="text-xs text-slate-500 mt-1">
                {uomText ? `${uomText} · ` : ''}
                {line.countTimes} lần ghi
              </div>
            </>
          ) : (
            <Tag className="!m-0 !text-sm !px-2 !py-1">Chưa đếm</Tag>
          )}
        </div>
      </div>

      {(pending > 0 || failed > 0) && (
        <div className="flex flex-wrap gap-2 text-xs">
          {pending > 0 && (
            <span className="text-blue-600">
              <LoadingOutlined /> Đang gửi {pending} lần ghi
            </span>
          )}
          {failed > 0 && (
            <span className="text-red-600">
              <WarningOutlined /> {failed} lần ghi lỗi, xem mục "Lần ghi chưa thành công" để thử lại
            </span>
          )}
        </div>
      )}

      {editable && (
        <div className="grid grid-cols-2 gap-2">
          <Button size="large" type="primary" icon={<PlusOutlined />} onClick={onAdd} className="!h-12">
            {step === 1 ? '+1' : `+${formatQty(step)}`}
          </Button>
          <Button size="large" icon={<EditOutlined />} onClick={onSet} className="!h-12">
            Nhập số
          </Button>
        </div>
      )}
    </div>
  )
}
