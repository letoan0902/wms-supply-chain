import { Tag, Tree } from 'antd'
import type { TreeDataNode } from 'antd'
import { formatInstant, formatQuantity } from '../reportFormat'
import type { TraceNode } from '../types'

const DIRECTION_TAGS: Record<string, { label: string; color: string }> = {
  ROOT: { label: 'Lô', color: 'purple' },
  UPSTREAM: { label: 'Nguồn gốc', color: 'blue' },
  CURRENT: { label: 'Đang ở', color: 'green' },
  DOWNSTREAM: { label: 'Đã đi', color: 'orange' },
}

function toTreeData(node: TraceNode, depth: number, uom: string | null): TreeDataNode {
  const tag = depth <= 1 ? DIRECTION_TAGS[node.direction] : undefined
  return {
    key: node.key,
    title: (
      <span className="inline-flex flex-wrap items-center gap-x-2 gap-y-0.5 py-0.5">
        {tag && (
          <Tag color={tag.color} className="!mr-0">
            {tag.label}
          </Tag>
        )}
        <span className={depth === 0 ? 'font-semibold' : undefined}>{node.title}</span>
        {node.quantity !== null && node.quantity !== undefined && (
          <span className="font-mono text-xs text-gray-600">
            {formatQuantity(node.quantity)}
            {uom ? ` ${uom}` : ''}
          </span>
        )}
        {node.occurredAt && <span className="text-xs text-gray-400">{formatInstant(node.occurredAt)}</span>}
      </span>
    ),
    children: (node.children ?? []).map((child) => toTreeData(child, depth + 1, uom)),
  }
}

interface Props {
  tree: TraceNode
  uom: string | null
}

/** Cây truy vết của lô do backend dựng sẵn: gốc là lô, nhánh nguồn gốc, vị trí hiện tại và nơi đã đi. */
export default function LotTraceTree({ tree, uom }: Props) {
  return (
    // key theo gốc để mở lại toàn bộ cây khi xem lô khác
    <Tree key={tree.key} showLine selectable={false} defaultExpandAll treeData={[toTreeData(tree, 0, uom)]} />
  )
}
