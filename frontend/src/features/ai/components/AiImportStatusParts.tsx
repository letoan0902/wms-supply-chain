import { Tooltip } from 'antd'
import { share } from '../aiImportStatus'

export interface BarSegment {
  key: string
  label: string
  count: number
  color: string
}

/**
 * Thanh tỉ lệ một hàng: mỗi đoạn một trạng thái, rộng theo số dòng; đoạn đủ rộng thì ghi số ngay trên thanh. Dùng
 * chung cho bước xem trước và bước kết quả để cùng một bảng màu.
 */
export function ProportionBar({ segments, total, label }: { segments: BarSegment[]; total: number; label: string }) {
  const visible = segments.filter((segment) => segment.count > 0)
  const description = visible.map((segment) => `${segment.label}: ${segment.count}`).join(', ')
  return (
    <div
      role="img"
      aria-label={`${label}. ${description}`}
      className="flex h-3.5 w-full overflow-hidden rounded-full bg-slate-100"
    >
      {visible.map((segment) => {
        const width = share(segment.count, total)
        return (
          <Tooltip key={segment.key} title={`${segment.label}: ${segment.count} dòng`}>
            <div
              className="h-full border-r border-white/70 last:border-r-0"
              style={{ width: `${width}%`, background: segment.color, minWidth: 6 }}
            />
          </Tooltip>
        )
      })}
    </div>
  )
}

interface StatTileProps {
  title: string
  value: number
  hint?: string
  /** Màu chấm và viền khi chọn; bỏ trống cho ô "Tất cả" */
  color?: string
  active?: boolean
  /** Có `onClick` thì ô là nút lọc (bấm hoặc Enter, Space) */
  onClick?: () => void
}

/** Ô số liệu cùng kiểu thẻ KPI của Dashboard: tiêu đề nhỏ, con số lớn, một dòng giải thích. */
export function StatTile({ title, value, hint, color, active = false, onClick }: StatTileProps) {
  const clickable = Boolean(onClick)
  const muted = value === 0 && !active
  const content = (
    <>
      <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
        {color && <span aria-hidden className="inline-block h-2 w-2 shrink-0 rounded-full" style={{ background: color }} />}
        <span className="truncate">{title}</span>
      </div>
      <div className={`mt-0.5 text-[1.35rem] font-semibold tabular-nums ${muted ? 'text-slate-300' : 'text-slate-900'}`}>
        {value}
      </div>
      {hint && <div className="text-[11px] leading-snug text-slate-500">{hint}</div>}
    </>
  )
  const base = 'h-full w-full rounded-lg border bg-white px-3 py-2 text-left transition-colors duration-150'
  if (!clickable) return <div className={`${base} border-slate-200/80`}>{content}</div>
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`${base} cursor-pointer focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500 ${
        active ? 'bg-slate-50/70 shadow-2xs' : 'border-slate-200/80 hover:border-slate-300'
      }`}
      style={active ? { borderColor: color ?? '#1e293b', boxShadow: `inset 0 0 0 1px ${color ?? '#1e293b'}` } : undefined}
    >
      {content}
    </button>
  )
}
