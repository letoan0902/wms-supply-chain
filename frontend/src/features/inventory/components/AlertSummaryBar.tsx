import { Alert, Skeleton, Tag, Tooltip, Typography } from 'antd'
import { KIND_LABELS, KIND_ORDER, SEVERITY_COLORS, SEVERITY_LABELS, SEVERITY_ORDER } from '../inventoryUtils'
import type { StockAlertKind, StockAlertSeverity, StockAlertSummary } from '../types'
import type { ViewError } from '../viewError'

/** Màu chip theo loại: hết hạn đỏ, tồn thấp đỏ (gồm cả mức vàng), cận hạn vàng, tồn lâu xanh dương. */
const KIND_COLORS: Record<StockAlertKind, string> = {
  EXPIRED: SEVERITY_COLORS.CRITICAL,
  LOW_STOCK: SEVERITY_COLORS.CRITICAL,
  NEAR_EXPIRY: SEVERITY_COLORS.WARNING,
  DEAD_STOCK: SEVERITY_COLORS.INFO,
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
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <Typography.Text type="secondary">Cảnh báo ngày {summary.alertDate.split('-').reverse().join('/')}:</Typography.Text>
        {KIND_ORDER.map((kind) => {
          const unavailable = kind === 'LOW_STOCK' && !summary.lowStockAvailable
          const count = summary.countsByKind?.[kind] ?? 0
          const active = activeKind === kind
          return (
            <Tooltip key={kind} title={unavailable ? summary.note : kindHint(kind, summary)}>
              <Tag.CheckableTag
                checked={active}
                onChange={() => onSelectKind(active ? undefined : kind)}
                style={{
                  border: '1px solid',
                  borderColor: active ? 'transparent' : 'var(--ant-color-border, #d9d9d9)',
                  opacity: unavailable ? 0.6 : 1,
                }}
              >
                <Tag color={KIND_COLORS[kind]} style={{ marginInlineEnd: 6 }}>
                  {KIND_LABELS[kind]}
                </Tag>
                {unavailable ? 'chưa tính được' : count}
              </Tag.CheckableTag>
            </Tooltip>
          )
        })}
        <span className="mx-1 text-gray-300">|</span>
        {SEVERITY_ORDER.map((severity) => {
          const active = activeSeverity === severity
          return (
            <Tag.CheckableTag
              key={severity}
              checked={active}
              onChange={() => onSelectSeverity(active ? undefined : severity)}
              style={{ border: '1px solid', borderColor: active ? 'transparent' : 'var(--ant-color-border, #d9d9d9)' }}
            >
              <Tag color={SEVERITY_COLORS[severity]} style={{ marginInlineEnd: 6 }}>
                {SEVERITY_LABELS[severity]}
              </Tag>
              {summary.countsBySeverity?.[severity] ?? 0}
            </Tag.CheckableTag>
          )
        })}
      </div>
      {!summary.lowStockAvailable ? (
        <Alert
          type="warning"
          showIcon
          message="Chưa tính được cảnh báo tồn thấp"
          description={`${summary.note ?? 'Không đọc được mức tồn an toàn từ danh mục hàng hóa.'} Bảng vẫn hiển thị các cảnh báo khác; thiếu thẻ "Cháy hàng", "Sắp hết" không có nghĩa là tồn đủ.`}
        />
      ) : null}
    </div>
  )
}
