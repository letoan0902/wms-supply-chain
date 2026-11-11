import { Tag, Tooltip, Typography } from 'antd'
import { alertReason, isLotAlertKind, SEVERITY_LABELS } from '../inventoryUtils'
import type { RowTag } from '../inventoryUtils'

/** Số lý do tối đa liệt kê trong tooltip của một thẻ gộp nhiều cảnh báo. */
const MAX_REASONS = 5

function TagTooltip({ tag }: { tag: RowTag }) {
  const shown = tag.alerts.slice(0, MAX_REASONS)
  return (
    <div className="max-w-xs text-xs">
      <div className="mb-1 font-semibold">
        {SEVERITY_LABELS[tag.severity]}
        {tag.count > 1 ? `: ${tag.count} lô` : ''}
      </div>
      <ul className="m-0 list-disc pl-4">
        {shown.map((alert) => (
          <li key={alert.id}>
            <div>{alertReason(alert)}</div>
            {alert.message ? <div className="opacity-80">{alert.message}</div> : null}
          </li>
        ))}
      </ul>
      {tag.alerts.length > MAX_REASONS ? <div className="mt-1">và {tag.alerts.length - MAX_REASONS} lô khác</div> : null}
    </div>
  )
}

/** Các thẻ màu cảnh báo của một dòng tồn, mỗi thẻ có tooltip ghi lý do. */
export default function AlertTags({ tags }: { tags: RowTag[] }) {
  if (tags.length === 0) {
    return <Typography.Text type="secondary">—</Typography.Text>
  }
  return (
    <div className="flex flex-wrap gap-1">
      {tags.map((tag) => (
        <Tooltip key={tag.key} title={<TagTooltip tag={tag} />}>
          <Tag color={tag.color} style={{ marginInlineEnd: 0, cursor: 'help', fontWeight: 600 }}>
            {tag.label}
            {tag.count > 1 && isLotAlertKind(tag.kind) ? ` (${tag.count} lô)` : ''}
          </Tag>
        </Tooltip>
      ))}
    </div>
  )
}
