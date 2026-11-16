import { Alert, Skeleton, Tooltip, Typography } from 'antd'
import { KIND_LABELS, KIND_ORDER, SEVERITY_LABELS, SEVERITY_ORDER } from '../inventoryUtils'
import type { StockAlertKind, StockAlertSeverity, StockAlertSummary } from '../types'
import type { ViewError } from '../viewError'

/** Màu nền và chữ cho mỗi loại cảnh báo – tông slate trung tính, chỉ điểm nhấn bằng dot nhỏ. */
const KIND_DOT: Record<StockAlertKind, string> = {
  EXPIRED: 'bg-red-500',
  LOW_STOCK: 'bg-amber-500',
  NEAR_EXPIRY: 'bg-yellow-500',
  DEAD_STOCK: 'bg-slate-400',
}

const SEVERITY_DOT: Record<StockAlertSeverity, string> = {
  CRITICAL: 'bg-red-500',
  WARNING: 'bg-amber-500',
  INFO: 'bg-blue-500',
}

interface AlertSummaryBarProps {
  summary: StockAlertSummary | null
  loading: boolean
  error: ViewError | null
  activeKind?: StockAlertKind
  activeSeverity?: StockAlertSeverity
  onSelectKind: (kind: StockAlertKind | undefined) => void
  onSelectSeverity: (severity: StockAlertSeverity | undefined) => void
}

function kindHint(kind: StockAlertKind, summary: StockAlertSummary): string {
  switch (kind) {
    case 'EXPIRED':
      return 'Lô còn hàng đã tới hoặc quá hạn dùng'
    case 'NEAR_EXPIRY':
      return `Lô còn hàng hết hạn trong ${summary.nearExpiryDays} ngày tới`
    case 'LOW_STOCK':
      return 'Tồn dùng được nhỏ hơn hoặc bằng mức an toàn (đỏ) hoặc điểm đặt hàng lại (vàng)'
    case 'DEAD_STOCK':
      return `Còn tồn nhưng không nhập xuất từ ${summary.deadStockDays} ngày trở lên`
  }
}

/** Thanh tóm tắt số cảnh báo theo loại và mức; bấm một chip để chỉ hiện các cảnh báo đó. */
export default function AlertSummaryBar({
  summary,
  loading,
  error,
  activeKind,
  activeSeverity,
  onSelectKind,
  onSelectSeverity,
}: AlertSummaryBarProps) {
  if (error) {
    return <Alert type="warning" showIcon message={`Không tải được tóm tắt cảnh báo: ${error.message}`} />
  }
  if (!summary) {
    return loading ? <Skeleton.Input active size="small" block /> : null
  }

  return (
    <div className="flex flex-col gap-3">
      {/* Thanh cảnh báo gọn, đồng nhất */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-medium text-slate-500">
          Cảnh báo ngày {summary.alertDate.split('-').reverse().join('/')}:
        </span>

        <div className="flex flex-wrap items-center gap-1.5">
          {KIND_ORDER.map((kind) => {
            const unavailable = kind === 'LOW_STOCK' && !summary.lowStockAvailable
            const count = summary.countsByKind?.[kind] ?? 0
            const active = activeKind === kind
            return (
              <Tooltip key={kind} title={unavailable ? summary.note : kindHint(kind, summary)}>
                <button
                  type="button"
                  onClick={() => onSelectKind(active ? undefined : kind)}
                  className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-sm font-medium transition-colors ${
                    active
                      ? 'border-slate-900 bg-slate-900 text-white'
                      : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50'
                  } ${unavailable ? 'opacity-50' : ''}`}
                >
                  <span className={`inline-block h-2 w-2 rounded-full ${KIND_DOT[kind]}`} />
                  {KIND_LABELS[kind]}
                  <span className={`font-semibold tabular-nums ${active ? 'text-white' : 'text-slate-900'}`}>
                    {unavailable ? '—' : count}
                  </span>
                </button>
              </Tooltip>
            )
          })}
        </div>

        <span className="mx-0.5 text-slate-300">|</span>

        <div className="flex flex-wrap items-center gap-1.5">
          {SEVERITY_ORDER.map((sev) => {
            const active = activeSeverity === sev
            return (
              <Tooltip key={sev} title={SEVERITY_LABELS[sev]}>
                <button
                  type="button"
                  onClick={() => onSelectSeverity(active ? undefined : sev)}
                  className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-sm font-medium transition-colors ${
                    active
                      ? 'border-slate-900 bg-slate-900 text-white'
                      : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  <span className={`inline-block h-2 w-2 rounded-full ${SEVERITY_DOT[sev]}`} />
                  {SEVERITY_LABELS[sev]}
                  <span className={`font-semibold tabular-nums ${active ? 'text-white' : 'text-slate-900'}`}>
                    {summary.countsBySeverity?.[sev] ?? 0}
                  </span>
                </button>
              </Tooltip>
            )
          })}
        </div>
      </div>

      {!summary.lowStockAvailable ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50/60 px-4 py-3">
          <Typography.Text className="text-sm font-medium text-amber-800">
            Chưa tính được cảnh báo tồn thấp — {summary.note ?? 'Không đọc được mức tồn an toàn từ danh mục hàng hóa.'}
          </Typography.Text>
        </div>
      ) : null}
    </div>
  )
}
