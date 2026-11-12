import { Alert, Table, Tag, Tooltip, Typography } from 'antd'
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
  return <span className="tabular-nums">{formatQuantity(value)}</span>
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
      title: 'Cảnh báo',
      key: 'tag',
      width: 140,
      fixed: 'left',
      render: (_, row) => (
        <Tooltip title={SEVERITY_LABELS[row.severity]}>
          <Tag color={SEVERITY_COLORS[row.severity]} style={{ fontWeight: 600 }}>
            {alertTagLabel(row.kind, row.severity)}
          </Tag>
        </Tooltip>
      ),
    },
    {
      title: 'Mã SKU',
      key: 'sku',
      width: 130,
      render: (_, row) => <Typography.Text strong>{row.skuCode ?? `#${row.productId}`}</Typography.Text>,
    },
    {
      title: 'Tên hàng hóa',
      key: 'name',
      width: 200,
      render: (_, row) => row.productName ?? <Typography.Text type="secondary">Không rõ tên</Typography.Text>,
    },
    ...(showWarehouse
      ? [
          {
            title: 'Kho',
            key: 'warehouse',
            width: 160,
            render: (_: unknown, row: StockAlert) => row.warehouseName ?? row.warehouseCode ?? '—',
          },
        ]
      : []),
    {
      title: 'Lô / vị trí',
      key: 'lot',
      width: 170,
      render: (_, row) =>
        row.stockItemId === null ? (
          <Typography.Text type="secondary">Cả mặt hàng tại kho</Typography.Text>
        ) : (
          <div>
            <div>{row.lotNo ? `Lô ${row.lotNo}` : 'Không số lô'}</div>
            {row.binCode ? <Typography.Text code>{row.binCode}</Typography.Text> : null}
          </div>
        ),
    },
    {
      title: 'Hạn dùng',
      key: 'expDate',
      width: 140,
      render: (_, row) =>
        row.expDate ? (
          <div>
            <div>{formatLocalDate(row.expDate)}</div>
            <Typography.Text type={row.daysLeft !== null && row.daysLeft <= 0 ? 'danger' : 'warning'} className="text-xs">
              {daysLeftText(row.daysLeft)}
            </Typography.Text>
          </div>
        ) : (
          '—'
        ),
    },
    {
      title: 'Số lượng',
      key: 'quantity',
      width: 110,
      align: 'right',
      render: (_, row) => quantityCell(row.quantity),
    },
    {
      title: 'Khả dụng',
      key: 'available',
      width: 110,
      align: 'right',
      render: (_, row) => quantityCell(row.availableQuantity),
    },
    {
      title: 'Ngưỡng',
      key: 'threshold',
      width: 170,
      render: (_, row) =>
        row.kind === 'LOW_STOCK' ? (
          <div className="text-xs">
            <div>An toàn: {formatQuantity(row.safetyStock)}</div>
            <div>Đặt hàng lại: {formatQuantity(row.reorderPoint)}</div>
          </div>
        ) : row.kind === 'DEAD_STOCK' ? (
          <div className="text-xs">
            <div>{row.daysWithoutMovement ?? '—'} ngày không nhập xuất</div>
            <div>Lần cuối: {formatInstantDate(row.lastMovementAt)}</div>
          </div>
        ) : (
          '—'
        ),
    },
    {
      title: 'Nội dung',
      key: 'message',
      width: 360,
      render: (_, row) => (
        <Tooltip title={alertReason(row)}>
          <span>{row.message}</span>
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
      scroll={{ x: showWarehouse ? 1800 : 1640 }}
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
