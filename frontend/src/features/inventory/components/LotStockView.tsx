import { LockOutlined } from '@ant-design/icons'
import { Alert, Table, Tag, Tooltip } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  fetchAlertsForTags,
  fetchLots,
  fetchProductBriefs,
  TAG_ALERT_MAX_PAGES,
  TAG_ALERT_PAGE_SIZE,
} from '../inventoryApi'
import { daysLeftText, formatLocalDate, formatQuantity, indexAlerts, tagsForLotRow } from '../inventoryUtils'
import type { AlertIndex, RowTag } from '../inventoryUtils'
import type { LotExpiryFilter, LotRow, ProductBrief } from '../types'
import { errorTitle, toViewError } from '../viewError'
import type { ViewError } from '../viewError'
import AlertTags from './AlertTags'

interface LotStockViewProps {
  warehouseId?: number
  productId?: number
  lotNo?: string
  hasStock?: boolean
  locked?: boolean
  expiryStatus?: LotExpiryFilter
  /** Ngưỡng cận hạn (ngày) lấy từ GET /alerts/summary, dùng khi lọc "Cận hạn" */
  nearExpiryDays?: number
  /** Hiện cột kho khi đang xem mọi kho */
  showWarehouse: boolean
  refreshKey: number
  onLoaded: () => void
}

type LotSortField = 'expDate' | 'mfgDate' | 'quantity'

interface LotStockRow extends LotRow {
  key: string
  product: ProductBrief | null
  tags: RowTag[]
  expired: boolean
}

const EMPTY_INDEX: AlertIndex = indexAlerts([])

function quantityCell(value: number | null | undefined) {
  return <span className="font-mono text-[15px] font-semibold tabular-nums text-slate-900">{formatQuantity(value)}</span>
}

function isExpiredRow(row: LotRow): boolean {
  if (row.expiryStatus === 'EXPIRED') return true
  return row.daysUntilExpiry !== null && row.daysUntilExpiry <= 0
}

/**
 * Bảng tồn theo lô và ô kệ: số lô, ngày sản xuất, hạn dùng, số ngày còn lại, vị trí, số lượng, đã giữ chỗ, trạng thái
 * khóa và thẻ cảnh báo. Phân trang và sắp xếp ở máy chủ (theo hạn dùng, ngày sản xuất, số lượng).
 */
export default function LotStockView({
  warehouseId,
  productId,
  lotNo,
  hasStock,
  locked,
  expiryStatus,
  nearExpiryDays,
  showWarehouse,
  refreshKey,
  onLoaded,
}: LotStockViewProps) {
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [sortField, setSortField] = useState<LotSortField>('expDate')
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc')

  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<LotRow[]>([])
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<ViewError | null>(null)
  const [products, setProducts] = useState<Map<number, ProductBrief>>(new Map())
  const [alertIndex, setAlertIndex] = useState<AlertIndex>(EMPTY_INDEX)
  const [tagNote, setTagNote] = useState<string | null>(null)

  const onLoadedRef = useRef(onLoaded)
  useEffect(() => {
    onLoadedRef.current = onLoaded
  }, [onLoaded])

  useEffect(() => {
    let ignore = false
    const load = async () => {
      setLoading(true)
      const tagsPromise = fetchAlertsForTags({ warehouseId, productId })
        .then((result) => ({ ...result, error: null as ViewError | null }))
        .catch((err: unknown) => ({ alerts: [], truncated: false, error: toViewError(err, 'Không tải được cảnh báo') }))
      try {
        const data = await fetchLots({
          warehouseId,
          productId,
          lotNo,
          hasStock,
          locked,
          expiryStatus,
          expiringDays: expiryStatus === 'EXPIRING_SOON' ? nearExpiryDays : undefined,
          page: page - 1,
          size: pageSize,
          sort: `${sortField},${sortDirection}`,
        })
        const content = data.content ?? []
        const [productMap, tagResult] = await Promise.all([
          fetchProductBriefs(content.map((row) => row.productId)),
          tagsPromise,
        ])
        if (ignore) return
        setRows(content)
        setTotal(data.totalElements ?? 0)
        setProducts(productMap)
        setAlertIndex(indexAlerts(tagResult.alerts))
        setTagNote(
          tagResult.error
            ? `Không tải được nhãn cảnh báo (${tagResult.error.message}); bảng vẫn hiển thị số tồn.`
            : tagResult.truncated
              ? `Có hơn ${TAG_ALERT_MAX_PAGES * TAG_ALERT_PAGE_SIZE} cảnh báo; chỉ gắn nhãn cho các cảnh báo nghiêm trọng nhất. Chọn một kho hoặc một mặt hàng để xem đủ.`
              : null,
        )
        setError(null)
        onLoadedRef.current()
      } catch (err) {
        if (ignore) return
        setRows([])
        setTotal(0)
        setError(toViewError(err, 'Không tải được tồn kho theo lô và vị trí'))
      } finally {
        if (!ignore) setLoading(false)
      }
    }
    void load()
    return () => {
      ignore = true
    }
  }, [
    warehouseId,
    productId,
    lotNo,
    hasStock,
    locked,
    expiryStatus,
    nearExpiryDays,
    sortField,
    sortDirection,
    page,
    pageSize,
    refreshKey,
  ])

  const dataSource = useMemo<LotStockRow[]>(
    () =>
      rows.map((row) => ({
        ...row,
        key: `${row.stockItemId}`,
        product: products.get(row.productId) ?? null,
        tags: tagsForLotRow(alertIndex, row),
        expired: isExpiredRow(row),
      })),
    [rows, products, alertIndex],
  )

  const sortOrderOf = (field: LotSortField) =>
    sortField === field ? (sortDirection === 'asc' ? 'ascend' : 'descend') : null

  const columns: TableProps<LotStockRow>['columns'] = [
    {
      title: <span className="text-sm font-semibold text-slate-700">#</span>,
      key: 'index',
      width: 48,
      align: 'center',
      render: (_v, _r, i) => <span className="text-sm tabular-nums text-slate-500">{(page - 1) * pageSize + i + 1}</span>,
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Mã SKU</span>,
      key: 'sku',
      width: 120,
      render: (_, row) => (
        <span className="font-mono text-[15px] font-semibold text-blue-600">{row.product?.sku ?? `#${row.productId}`}</span>
      ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Tên hàng hóa</span>,
      key: 'name',
      width: 180,
      render: (_, row) => (
        <span className="text-[15px] font-semibold text-slate-900">
          {row.product?.name ?? <span className="text-slate-400">Không rõ tên</span>}
        </span>
      ),
    },
    ...(showWarehouse
      ? [
          {
            title: <span className="text-sm font-semibold text-slate-700">Kho</span>,
            key: 'warehouse',
            width: 140,
            render: (_: unknown, row: LotStockRow) => (
              <span className="text-sm text-slate-700">{row.warehouseName ?? row.warehouseCode ?? '—'}</span>
            ),
          },
        ]
      : []),
    {
      title: <span className="text-sm font-semibold text-slate-700">Số lô</span>,
      key: 'lotNo',
      width: 100,
      render: (_, row) => (
        <span className="text-sm text-slate-700">
          {row.lotNo ?? <span className="text-slate-400">Không số lô</span>}
        </span>
      ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Ngày SX</span>,
      key: 'mfgDate',
      width: 100,
      sorter: true,
      sortOrder: sortOrderOf('mfgDate'),
      render: (_, row) => <span className="text-sm tabular-nums text-slate-600">{formatLocalDate(row.mfgDate)}</span>,
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Hạn dùng</span>,
      key: 'expDate',
      width: 100,
      sorter: true,
      sortOrder: sortOrderOf('expDate'),
      render: (_, row) => <span className="text-sm tabular-nums text-slate-600">{formatLocalDate(row.expDate)}</span>,
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Còn lại</span>,
      key: 'daysLeft',
      width: 110,
      render: (_, row) => {
        if (row.expDate === null) return <span className="text-sm text-slate-400">Không có hạn</span>
        const text = daysLeftText(row.daysUntilExpiry)
        if (row.expired) return <span className="text-sm font-semibold text-red-600">{text}</span>
        if (row.expiryStatus === 'EXPIRING_SOON') return <span className="text-sm font-medium text-amber-600">{text}</span>
        return <span className="text-sm text-slate-600">{text}</span>
      },
    },
    {
      title: <span className="text-sm font-semibold text-slate-700 whitespace-nowrap">Vị trí (khu-kệ-ô)</span>,
      key: 'bin',
      width: 140,
      render: (_, row) => (
        <div>
          <code className="rounded bg-slate-100 px-1 py-0.5 text-xs font-medium text-slate-700">{row.binLocationCode ?? '—'}</code>
          {row.zone || row.rack || row.bin ? (
            <div className="mt-0.5 text-xs text-slate-500">
              {[row.zone, row.rack, row.bin].map((part) => part ?? '—').join(' - ')}
            </div>
          ) : null}
        </div>
      ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Số lượng</span>,
      key: 'quantity',
      width: 96,
      align: 'right',
      sorter: true,
      sortOrder: sortOrderOf('quantity'),
      render: (_, row) => quantityCell(row.quantity),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Đã giữ chỗ</span>,
      key: 'reserved',
      width: 96,
      align: 'right',
      render: (_, row) => quantityCell(row.reservedQuantity),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Khả dụng</span>,
      key: 'available',
      width: 96,
      align: 'right',
      render: (_, row) => (
        <span className="font-mono text-[15px] font-bold tabular-nums text-slate-900">{formatQuantity(row.availableQuantity)}</span>
      ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Trạng thái</span>,
      key: 'locked',
      width: 100,
      render: (_, row) =>
        row.locked ? (
          <Tooltip title={row.lockedReason ? `Lý do: ${row.lockedReason}` : 'Lô đang bị khóa, không được xuất'}>
            <Tag icon={<LockOutlined />} color="default">
              Đã khóa
            </Tag>
          </Tooltip>
        ) : (
          <Tag color="green">Đang dùng</Tag>
        ),
    },
    {
      title: <span className="text-sm font-semibold text-slate-700">Cảnh báo</span>,
      key: 'tags',
      width: 160,
      render: (_, row) => <AlertTags tags={row.tags} />,
    },
  ]

  const handleChange: TableProps<LotStockRow>['onChange'] = (pagination, _filters, sorter, extra) => {
    if (extra.action === 'sort') {
      const single = Array.isArray(sorter) ? sorter[0] : sorter
      if (single?.order) {
        setSortField(single.columnKey as LotSortField)
        setSortDirection(single.order === 'descend' ? 'desc' : 'asc')
      } else {
        setSortField('expDate')
        setSortDirection('asc')
      }
      setPage(1)
      return
    }
    setPage(pagination.current ?? 1)
    setPageSize(pagination.pageSize ?? 20)
  }

  if (error) {
    return (
      <Alert
        type={error.status === 403 ? 'warning' : 'error'}
        showIcon
        message={errorTitle(error.status)}
        description={error.message}
      />
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {tagNote ? <Alert type="info" showIcon message={tagNote} /> : null}
      <Table<LotStockRow>
        rowKey="key"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={dataSource}
        scroll={{ x: showWarehouse ? 1586 : 1446 }}
        rowClassName={(row) => (row.locked ? 'inventory-row-locked' : row.expired ? 'inventory-row-expired' : '')}
        onChange={handleChange}
        locale={{ emptyText: 'Không có lô hàng nào khớp bộ lọc' }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          showTotal: (count) => `${count} dòng lô theo vị trí`,
        }}
      />
    </div>
  )
}
