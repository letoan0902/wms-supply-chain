import { Alert, Table, Tag, Tooltip } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { fetchAlerts } from '../inventoryApi'
import {
  alertReason,
  alertTagLabel,
  daysLeftText,
  formatInstantDate,
  formatLocalDate,
  formatQuantity,
  SEVERITY_COLORS,
  SEVERITY_LABELS,
} from '../inventoryUtils'
import type { StockAlert, StockAlertKind, StockAlertSeverity } from '../types'
import { errorTitle, toViewError } from '../viewError'
import type { ViewError } from '../viewError'

interface AlertListViewProps {
  warehouseId?: number
  kind?: StockAlertKind
  severity?: StockAlertSeverity
  keyword?: string
  showWarehouse: boolean
  refreshKey: number
  onLoaded: () => void
}

function quantityCell(value: number | null | undefined) {
  return <span className="font-mono text-[15px] font-semibold tabular-nums text-slate-900">{formatQuantity(value)}</span>
}

/**
 * Chế độ "Chỉ hiện dòng có cảnh báo": mỗi dòng là một cảnh báo của GET /api/inventory/alerts (lô hết hạn, lô cận hạn,
 * mặt hàng tồn thấp hoặc tồn lâu tại một kho). Phân trang ở máy chủ; thứ tự cố định do backend quyết định
 * (nghiêm trọng trước, rồi theo loại, hạn gần trước) nên không có sắp xếp theo cột.
 */
export default function AlertListView({
  warehouseId,
  kind,
  severity,
  keyword,
  showWarehouse,
  refreshKey,
  onLoaded,
}: AlertListViewProps) {
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<StockAlert[]>([])
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<ViewError | null>(null)

  const onLoadedRef = useRef(onLoaded)
  useEffect(() => {
    onLoadedRef.current = onLoaded
  }, [onLoaded])

  useEffect(() => {
    let ignore = false
    const load = async () => {
      setLoading(true)
      try {
        const data = await fetchAlerts({ warehouseId, kind, severity, keyword, page: page - 1, size: pageSize })
        if (ignore) return
        setRows(data.content ?? [])
        setTotal(data.totalElements ?? 0)
        setError(null)
        onLoadedRef.current()
      } catch (err) {
        if (ignore) return
        setRows([])
        setTotal(0)
        setError(toViewError(err, 'Không tải được danh sách cảnh báo'))
      } finally {
        if (!ignore) setLoading(false)
      }
    }
    void load()
    return () => {
      ignore = true
    }
  }, [warehouseId, kind, severity, keyword, page, pageSize, refreshKey])

  const columns: TableProps<StockAlert>['columns'] = [
    {
      title: <span className="text-sm font-semibold text-slate-700">#</span>,
      key: 'index',
      width: 48,
      align: 'center',
      render: (_v, _r, i) => <span className="text-sm tabular-nums text-slate-500">{(page - 1) * pageSize + i + 1}</span>,
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Cảnh báo</span>,
      key: 'tag',
      width: 120,
      render: (_, row) => (
        <Tooltip title={SEVERITY_LABELS[row.severity]}>
          <Tag color={SEVERITY_COLORS[row.severity]} style={{ fontWeight: 600 }}>
            {alertTagLabel(row.kind, row.severity)}
          </Tag>
        </Tooltip>
      ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Mã SKU</span>,
      key: 'sku',
      width: 120,
      render: (_, row) => (
        <span className="font-mono text-[15px] font-semibold text-blue-600">{row.skuCode ?? `#${row.productId}`}</span>
      ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Tên hàng hóa</span>,
      key: 'name',
      width: 180,
      render: (_, row) => (
        <span className="text-[15px] font-semibold text-slate-900">
          {row.productName ?? <span className="text-slate-400">Không rõ tên</span>}
        </span>
      ),
    },
    ...(showWarehouse
      ? [
          {
            title: <span className="text-sm font-semibold text-slate-700">Kho</span>,
            key: 'warehouse',
            width: 140,
            render: (_: unknown, row: StockAlert) => (
              <span className="text-sm text-slate-700">{row.warehouseName ?? row.warehouseCode ?? '—'}</span>
            ),
          },
        ]
      : []),
    {
      title: <span className="text-sm font-semibold text-slate-700">Lô / vị trí</span>,
      key: 'lot',
      width: 150,
      render: (_, row) =>
        row.stockItemId === null ? (
          <span className="text-sm text-slate-400">Cả mặt hàng tại kho</span>
        ) : (
          <div>
            <div className="text-sm text-slate-700">{row.lotNo ? `Lô ${row.lotNo}` : 'Không số lô'}</div>
            {row.binCode ? <code className="rounded bg-slate-100 px-1 py-0.5 text-xs font-medium text-slate-700">{row.binCode}</code> : null}
          </div>
        ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Hạn dùng</span>,
      key: 'expDate',
      width: 120,
      render: (_, row) =>
        row.expDate ? (
          <div>
            <div className="text-sm tabular-nums text-slate-600">{formatLocalDate(row.expDate)}</div>
            <span className={`text-xs font-medium ${row.daysLeft !== null && row.daysLeft <= 0 ? 'text-red-600' : 'text-amber-600'}`}>
              {daysLeftText(row.daysLeft)}
            </span>
          </div>
        ) : (
          <span className="text-sm text-slate-400">—</span>
        ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Số lượng</span>,
      key: 'quantity',
      width: 96,
      align: 'right',
      render: (_, row) => quantityCell(row.quantity),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Khả dụng</span>,
      key: 'available',
      width: 96,
      align: 'right',
      render: (_, row) => quantityCell(row.availableQuantity),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Ngưỡng</span>,
      key: 'threshold',
      width: 150,
      render: (_, row) =>
        row.kind === 'LOW_STOCK' ? (
          <div className="text-sm text-slate-600">
            <div>An toàn: <span className="font-semibold tabular-nums">{formatQuantity(row.safetyStock)}</span></div>
            <div>Đặt lại: <span className="font-semibold tabular-nums">{formatQuantity(row.reorderPoint)}</span></div>
          </div>
        ) : row.kind === 'DEAD_STOCK' ? (
          <div className="text-sm text-slate-600">
            <div><span className="font-semibold tabular-nums">{row.daysWithoutMovement ?? '—'}</span> ngày không nhập xuất</div>
            <div>Lần cuối: {formatInstantDate(row.lastMovementAt)}</div>
          </div>
        ) : (
          <span className="text-sm text-slate-400">—</span>
        ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Nội dung</span>,
      key: 'message',
      width: 300,
      render: (_, row) => (
        <Tooltip title={alertReason(row)}>
          <span className="text-sm text-slate-700">{row.message}</span>
        </Tooltip>
      ),
    },
  ]

  if (error) {
    return (
      <Alert
        type={error.status === 503 || error.status === 403 ? 'warning' : 'error'}
        showIcon
        message={errorTitle(error.status)}
        description={error.message}
      />
    )
  }

  return (
    <Table<StockAlert>
      rowKey="id"
      size="middle"
      loading={loading}
      columns={columns}
      dataSource={rows}
      scroll={{ x: showWarehouse ? 1560 : 1420 }}
      rowClassName={(row) => (row.kind === 'EXPIRED' ? 'inventory-row-expired' : '')}
      onChange={(pagination) => {
        setPage(pagination.current ?? 1)
        setPageSize(pagination.pageSize ?? 20)
      }}
      locale={{ emptyText: 'Không có cảnh báo nào khớp bộ lọc' }}
      pagination={{
        current: page,
        pageSize,
        total,
        showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100],
        showTotal: (count) => `${count} cảnh báo`,
      }}
    />
  )
}
