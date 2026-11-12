import { Alert, Table, Tooltip, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  fetchAlertsForTags,
  fetchBatchAtp,
  fetchProductBriefs,
  fetchStockBalance,
  TAG_ALERT_MAX_PAGES,
  TAG_ALERT_PAGE_SIZE,
} from '../inventoryApi'
import {
  formatQuantity,
  hasMatchingTag,
  indexAlerts,
  productKey,
  tagsForProductRow,
  vietnamIsoDate,
} from '../inventoryUtils'
import type { AlertIndex, RowTag } from '../inventoryUtils'
import type { AtpItem, BalanceRow, BalanceSortField, ProductBrief } from '../types'
import { errorTitle, toViewError } from '../viewError'
import type { ViewError } from '../viewError'
import AlertTags from './AlertTags'

interface ProductStockViewProps {
  warehouseId?: number
  keyword?: string
  categoryId?: number
  refreshKey: number
  onLoaded: () => void
}

interface ProductStockRow extends BalanceRow {
  key: string
  product: ProductBrief | null
  atp: AtpItem | null
  tags: RowTag[]
}

const EMPTY_INDEX: AlertIndex = indexAlerts([])

function quantityCell(value: number | null | undefined) {
  return <span className="tabular-nums">{formatQuantity(value)}</span>
}

/** Ngưỡng bằng 0 nghĩa là chưa cấu hình (cùng quy ước với backend), không hiển thị như một ngưỡng thật. */
function thresholdCell(value: number | null | undefined) {
  if (value === null || value === undefined || Number(value) === 0) {
    return <Typography.Text type="secondary">Chưa đặt</Typography.Text>
  }
  return quantityCell(value)
}

/**
 * Bảng tồn theo hàng hóa và kho: tồn thực tế (sổ kho, đến hôm nay), tồn dùng được, đã giữ chỗ, khả dụng, ngưỡng tồn
 * và thẻ cảnh báo. Phân trang và sắp xếp ở máy chủ.
 */
export default function ProductStockView({ warehouseId, keyword, categoryId, refreshKey, onLoaded }: ProductStockViewProps) {
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [sortField, setSortField] = useState<BalanceSortField>('productId')
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc')

  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<BalanceRow[]>([])
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<ViewError | null>(null)
  const [dataNote, setDataNote] = useState<string | null>(null)
  const [atpMap, setAtpMap] = useState<Map<string, AtpItem>>(new Map())
  const [atpError, setAtpError] = useState<string | null>(null)
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
      // Cảnh báo để gắn thẻ chạy song song với bảng chính; lỗi của nó không làm hỏng bảng
      const tagsPromise = fetchAlertsForTags({ warehouseId, keyword })
        .then((result) => ({ ...result, error: null as ViewError | null }))
        .catch((err: unknown) => ({ alerts: [], truncated: false, error: toViewError(err, 'Không tải được cảnh báo') }))
      try {
        const report = await fetchStockBalance(
          { warehouseId, keyword, categoryId, sort: sortField, direction: sortDirection, page: page - 1, size: pageSize },
          vietnamIsoDate(new Date()),
        )
        const content = report.rows?.content ?? []

        const idsByWarehouse = new Map<number, number[]>()
        content.forEach((row) => {
          if (row.warehouseId === null) return
          const ids = idsByWarehouse.get(row.warehouseId) ?? []
          ids.push(row.productId)
          idsByWarehouse.set(row.warehouseId, ids)
        })
        const [atpResults, productMap, tagResult] = await Promise.all([
          Promise.allSettled(
            [...idsByWarehouse].map(([wid, ids]) => fetchBatchAtp(wid, ids).then((items) => ({ wid, items }))),
          ),
          fetchProductBriefs(content.map((row) => row.productId)),
          tagsPromise,
        ])
        if (ignore) return

        const nextAtp = new Map<string, AtpItem>()
        let atpFailure: string | null = null
        atpResults.forEach((result) => {
          if (result.status === 'fulfilled') {
            result.value.items.forEach((item) => nextAtp.set(productKey(result.value.wid, item.productId), item))
          } else {
            atpFailure = toViewError(result.reason, 'Không tải được tồn khả dụng').message
          }
        })

        setRows(content)
        setTotal(report.rows?.totalElements ?? 0)
        setDataNote(content.some((row) => row.closingQuantity === null) ? report.dataNote : null)
        setAtpMap(nextAtp)
        setAtpError(atpFailure)
        setProducts(productMap)
        setAlertIndex(indexAlerts(tagResult.alerts))
        setTagNote(
          tagResult.error
            ? `Không tải được nhãn cảnh báo (${tagResult.error.message}); bảng vẫn hiển thị số tồn.`
            : tagResult.truncated
              ? `Có hơn ${TAG_ALERT_MAX_PAGES * TAG_ALERT_PAGE_SIZE} cảnh báo; chỉ gắn nhãn cho các cảnh báo nghiêm trọng nhất. Chọn một kho hoặc lọc từ khóa để xem đủ.`
              : null,
        )
        setError(null)
        onLoadedRef.current()
      } catch (err) {
        if (ignore) return
        setRows([])
        setTotal(0)
        setError(toViewError(err, 'Không tải được tồn kho theo hàng hóa'))
      } finally {
        if (!ignore) setLoading(false)
      }
    }
    void load()
    return () => {
      ignore = true
    }
  }, [warehouseId, keyword, categoryId, sortField, sortDirection, page, pageSize, refreshKey])

  const dataSource = useMemo<ProductStockRow[]>(
    () =>
      rows.map((row) => ({
        ...row,
        key: productKey(row.warehouseId, row.productId),
        product: products.get(row.productId) ?? null,
        atp: atpMap.get(productKey(row.warehouseId, row.productId)) ?? null,
        tags: tagsForProductRow(alertIndex, row),
      })),
    [rows, products, atpMap, alertIndex],
  )

  const sortOrderOf = (field: BalanceSortField) =>
    sortField === field ? (sortDirection === 'asc' ? 'ascend' : 'descend') : null

  const columns: TableProps<ProductStockRow>['columns'] = [
    {
      title: 'Mã SKU',
      key: 'sku',
      width: 140,
      fixed: 'left',
      render: (_, row) => (
        <Typography.Text strong>{row.skuCode ?? row.product?.sku ?? `#${row.productId}`}</Typography.Text>
      ),
    },
    {
      title: 'Tên hàng hóa',
      key: 'name',
      width: 220,
      render: (_, row) => row.productName ?? row.product?.name ?? <Typography.Text type="secondary">Không rõ tên</Typography.Text>,
    },
    {
      title: 'ĐVT',
      key: 'uom',
      width: 80,
      render: (_, row) => row.uom ?? row.product?.uomName ?? row.product?.uomCode ?? '—',
    },
    {
      title: 'Kho',
      key: 'warehouseId',
      width: 180,
      sorter: true,
      sortOrder: sortOrderOf('warehouseId'),
      render: (_, row) => (
        <span>
          {row.warehouseCode ? <Typography.Text code>{row.warehouseCode}</Typography.Text> : null} {row.warehouseName ?? ''}
        </span>
      ),
    },
    {
      title: <Tooltip title="Tổng tồn thực tế trong kho tính từ sổ kho đến hôm nay, gồm cả lô bị khóa và lô hết hạn">Tồn thực tế</Tooltip>,
      key: 'closingQuantity',
      width: 130,
      align: 'right',
      sorter: true,
      sortOrder: sortOrderOf('closingQuantity'),
      render: (_, row) => {
        const blocked =
          row.closingQuantity !== null && row.atp ? Number(row.closingQuantity) - Number(row.atp.onHandQuantity) : 0
        return (
          <div>
            {quantityCell(row.closingQuantity)}
            {blocked > 0 ? (
              <div className="text-xs text-red-500">Khóa hoặc hết hạn: {formatQuantity(blocked)}</div>
            ) : null}
          </div>
        )
      },
    },
    {
      title: <Tooltip title="Tồn của các lô không bị khóa và chưa hết hạn">Tồn dùng được</Tooltip>,
      key: 'onHand',
      width: 120,
      align: 'right',
      render: (_, row) => quantityCell(row.atp?.onHandQuantity),
    },
    {
      title: 'Đã giữ chỗ',
      key: 'reserved',
      width: 110,
      align: 'right',
      render: (_, row) => quantityCell(row.atp?.reservedQuantity),
    },
    {
      title: <Tooltip title="Tồn dùng được trừ phần đã giữ chỗ cho đơn bán (ATP)">Khả dụng</Tooltip>,
      key: 'available',
      width: 110,
      align: 'right',
      render: (_, row) => <Typography.Text strong>{formatQuantity(row.atp?.atpQuantity)}</Typography.Text>,
    },
    {
      title: 'Mức an toàn',
      key: 'safetyStock',
      width: 110,
      align: 'right',
      render: (_, row) => thresholdCell(row.product?.safetyStock),
    },
    {
      title: 'Điểm đặt hàng lại',
      key: 'reorderPoint',
      width: 130,
      align: 'right',
      render: (_, row) => thresholdCell(row.product?.reorderPoint),
    },
    {
      title: 'Cảnh báo',
      key: 'tags',
      width: 220,
      render: (_, row) => <AlertTags tags={row.tags} />,
    },
  ]

  const handleChange: TableProps<ProductStockRow>['onChange'] = (pagination, _filters, sorter, extra) => {
    if (extra.action === 'sort') {
      const single = Array.isArray(sorter) ? sorter[0] : sorter
      const field = single?.order ? (single.columnKey as BalanceSortField) : 'productId'
      setSortField(field)
      setSortDirection(single?.order === 'descend' ? 'desc' : 'asc')
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
        description={
          error.status === 403
            ? `${error.message} Bạn vẫn có thể xem tồn theo lô và vị trí của kho được phân công.`
            : error.message
        }
      />
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {atpError ? (
        <Alert type="warning" showIcon message={`Không tải được tồn dùng được và đã giữ chỗ: ${atpError}`} />
      ) : null}
      {tagNote ? <Alert type="info" showIcon message={tagNote} /> : null}
      {dataNote ? <Alert type="info" showIcon message={dataNote} /> : null}
      <Table<ProductStockRow>
        rowKey="key"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={dataSource}
        scroll={{ x: 1500 }}
        rowClassName={(row) =>
          row.tags.some((tag) => tag.kind === 'EXPIRED')
            ? 'inventory-row-expired'
            : hasMatchingTag(row.tags, 'LOW_STOCK', 'CRITICAL')
              ? 'inventory-row-critical'
              : ''
        }
        onChange={handleChange}
        locale={{ emptyText: 'Không có mặt hàng nào khớp bộ lọc' }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          showTotal: (count) => `${count} dòng hàng hóa theo kho`,
        }}
      />
    </div>
  )
}
