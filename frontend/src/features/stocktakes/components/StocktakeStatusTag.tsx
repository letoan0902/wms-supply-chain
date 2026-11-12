import { Tag } from 'antd'
import { STATUS_COLORS, STATUS_LABELS } from '../stocktakeLogic'
import type { StocktakeStatus } from '../types'

/** Thẻ trạng thái đợt kiểm kê. */
export default function StocktakeStatusTag({ status }: { status: StocktakeStatus }) {
  return (
    <Tag color={STATUS_COLORS[status] ?? 'default'} className="!m-0">
      {STATUS_LABELS[status] ?? status}
    </Tag>
  )
}
