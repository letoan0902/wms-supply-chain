import { FileExcelOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Card, DatePicker, Input, Select, Space, Table, Typography } from 'antd'
import type { TableColumnsType, TableProps } from 'antd'
import type { SorterResult, SortOrder } from 'antd/es/table/interface'
import dayjs, { type Dayjs } from 'dayjs'
import { useMemo, useState, type ReactNode } from 'react'
import type { Category } from '../../products/types'
import type { Warehouse } from '../../warehouses/types'
import { fetchInOutBalance } from '../reportApi'
import { hasMoneyValues } from '../reportExcel'
import { exportInOutBalance } from '../reportExport'
import { formatDate, formatInstant, formatQuantity, productLabel, validateRange, warehouseLabel } from '../reportFormat'
import type {
  GroupBy,
  InOutBalanceParams,
  InOutBalanceRow,
  InOutBalanceSortField,
  ProductOption,
  SortDirection,
} from '../types'
import { useDebouncedValue } from '../useDebouncedValue'
import { useExcelExport } from '../useExcelExport'
import { useReportQuery } from '../useReportQuery'
import ExportProgressModal from './ExportProgressModal'
import ProductSelect from './ProductSelect'
import ReportErrorAlert from './ReportErrorAlert'

const { RangePicker } = DatePicker
const { Text } = Typography

const API_DATE = 'YYYY-MM-DD'
const DEFAULT_PAGE_SIZE = 20

interface Props {
  warehouses: Warehouse[]
  warehousesLoading: boolean
  categories: Category[]
  /** true: Admin, Kế toán xem mọi kho; false: chỉ các kho được gán */
  allWarehouses: boolean
}

interface SortState {
  field: InOutBalanceSortField
  direction: SortDirection
}

const quantityCell = (value: number | null) => <span className="font-mono">{formatQuantity(value)}</span>

/** Tab "Xuất - Nhập - Tồn": tồn đầu, nhập, xuất, tồn cuối theo kỳ, phân trang và sắp xếp trên máy chủ. */
export default function InOutBalanceTab({ warehouses, warehousesLoading, categories, allWarehouses }: Props) {
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().startOf('month'), dayjs()])
  const [warehouseId, setWarehouseId] = useState<number | undefined>(undefined)
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined)
  const [product, setProduct] = useState<ProductOption | null>(null)
  const [keywordInput, setKeywordInput] = useState('')
  const keyword = useDebouncedValue(keywordInput.trim(), 400)
  const [groupBy, setGroupBy] = useState<GroupBy>('PRODUCT_WAREHOUSE')
  const [sort, setSort] = useState<SortState | null>(null)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [pageState, setPageState] = useState<{ filterKey: string; page: number }>({ filterKey: '', page: 0 })
  const [reloadToken, setReloadToken] = useState(0)
  const excel = useExcelExport()

  const fromDate = range[0].format(API_DATE)
  const toDate = range[1].format(API_DATE)
  const rangeError = validateRange(fromDate, toDate)

  // Bộ lọc không gồm trang: đổi bộ lọc thì tự về trang đầu (suy ra, không cần effect)
  const filterParams = useMemo<InOutBalanceParams>(
    () => ({
      fromDate,
      toDate,
      warehouseId,
      categoryId,
      productId: product?.id,
      keyword: keyword || undefined,
      groupBy,
      sort: sort?.field,
      direction: sort?.direction,
    }),
    [fromDate, toDate, warehouseId, categoryId, product?.id, keyword, groupBy, sort],
  )
  const filterKey = JSON.stringify(filterParams)
  const page = pageState.filterKey === filterKey ? pageState.page : 0

  const requestParams = useMemo<InOutBalanceParams | null>(
    () => (rangeError ? null : { ...filterParams, page, size: pageSize }),
    [rangeError, filterParams, page, pageSize],
  )
  const { data, loading, error } = useReportQuery(fetchInOutBalance, requestParams, reloadToken)

  const byWarehouse = (data?.groupBy ?? groupBy) === 'PRODUCT_WAREHOUSE'
  const rows = useMemo(() => data?.rows.content ?? [], [data])
  const showMoney = data ? hasMoneyValues(rows, data.totals) : false

  const warehouseOptions = useMemo(
    () => [
      {
        value: 0,
        label: allWarehouses ? 'Tất cả kho của doanh nghiệp' : 'Tất cả kho được phân công',
      },
      ...warehouses.map((w) => ({ value: w.id, label: `${w.code} - ${w.name}${w.active ? '' : ' (ngừng hoạt động)'}` })),
    ],
    [warehouses, allWarehouses],
  )

  const sortOrderOf = (field: InOutBalanceSortField): SortOrder =>
    sort?.field === field ? (sort.direction === 'asc' ? 'ascend' : 'descend') : null

  const columns: TableColumnsType<InOutBalanceRow> = [
    {
      title: 'Sản phẩm',
      key: 'productId',
      sorter: true,
      sortOrder: sortOrderOf('productId'),
      render: (_, r) => (
        <div>
          <div className="font-mono text-xs font-semibold text-blue-700">{r.skuCode ?? `ID ${r.productId}`}</div>
          <div className="text-sm text-gray-900">{r.productName ?? productLabel(r.skuCode, r.productName, r.productId)}</div>
          {r.uom && <div className="text-xs text-gray-500">ĐVT: {r.uom}</div>}
        </div>
      ),
    },
    ...(byWarehouse
      ? [
          {
            title: 'Kho',
            key: 'warehouseId',
            sorter: true,
            sortOrder: sortOrderOf('warehouseId'),
            render: (_: unknown, r: InOutBalanceRow) => warehouseLabel(r.warehouseCode, r.warehouseName, r.warehouseId),
          },
        ]
      : []),
    {
      title: 'Tồn đầu kỳ',
      key: 'openingQuantity',
      dataIndex: 'openingQuantity',
      align: 'right',
      sorter: true,
      sortOrder: sortOrderOf('openingQuantity'),
      render: quantityCell,
    },
    {
      title: 'Nhập trong kỳ',
      key: 'quantityIn',
      dataIndex: 'quantityIn',
      align: 'right',
      sorter: true,
      sortOrder: sortOrderOf('quantityIn'),
      render: quantityCell,
    },
    {
      title: 'Xuất trong kỳ',
      key: 'quantityOut',
      dataIndex: 'quantityOut',
      align: 'right',
      sorter: true,
      sortOrder: sortOrderOf('quantityOut'),
      render: quantityCell,
    },
    {
      title: 'Tồn cuối kỳ',
      key: 'closingQuantity',
      dataIndex: 'closingQuantity',
      align: 'right',
      sorter: true,
      sortOrder: sortOrderOf('closingQuantity'),
      render: (v: number | null) => <span className="font-mono font-semibold">{formatQuantity(v)}</span>,
    },
    ...(showMoney
      ? (['openingValue', 'valueIn', 'valueOut', 'closingValue'] as const).map((k, i) => ({
          title: ['Giá trị tồn đầu', 'Giá trị nhập', 'Giá trị xuất', 'Giá trị tồn cuối'][i],
          key: k,
          dataIndex: k,
          align: 'right' as const,
          render: quantityCell,
        }))
      : []),
  ]

  const handleTableChange: TableProps<InOutBalanceRow>['onChange'] = (pagination, _filters, sorter, extra) => {
    if (extra.action === 'sort') {
      const s = (Array.isArray(sorter) ? sorter[0] : sorter) as SorterResult<InOutBalanceRow> | undefined
      if (s?.order && s.columnKey) {
        setSort({ field: s.columnKey as InOutBalanceSortField, direction: s.order === 'ascend' ? 'asc' : 'desc' })
      } else {
        setSort(null)
      }
      return
    }
    const nextSize = pagination.pageSize ?? pageSize
    if (nextSize !== pageSize) {
      setPageSize(nextSize)
      setPageState({ filterKey, page: 0 })
    } else {
      setPageState({ filterKey, page: (pagination.current ?? 1) - 1 })
    }
  }

  const handleGroupByChange = (value: GroupBy) => {
    setGroupBy(value)
    // Gộp mọi kho thì không sắp xếp theo kho được (backend trả 400)
    if (value === 'PRODUCT' && sort?.field === 'warehouseId') setSort(null)
  }

  const handleExport = () => {
    const warehouse = warehouses.find((w) => w.id === warehouseId)
    const category = categories.find((c) => c.id === categoryId)
    void excel.run((onProgress, signal) =>
      exportInOutBalance(
        filterParams,
        {
          warehouse: warehouse
            ? `${warehouse.code} - ${warehouse.name}`
            : allWarehouses
              ? 'Tất cả kho của doanh nghiệp'
              : 'Tất cả kho được phân công',
          category: category?.name,
          product: product ? `${product.sku} - ${product.name}` : undefined,
          keyword: keyword || undefined,
        },
        onProgress,
        signal,
      ),
    )
  }

  const totals = data?.totals
  const summary = () => {
    if (error || !data || rows.length === 0 || !totals) return null
    let index = 0
    const cell = (content: ReactNode, align: 'left' | 'right' = 'right') => (
      <Table.Summary.Cell index={index++} align={align}>
        {content}
      </Table.Summary.Cell>
    )
    return (
      <Table.Summary fixed>
        <Table.Summary.Row className="bg-slate-50 font-semibold">
          {cell(`Tổng cộng (${formatQuantity(data.rows.totalElements)} dòng)`, 'left')}
          {byWarehouse && cell('', 'left')}
          {cell(<span className="font-mono">{formatQuantity(totals.openingQuantity)}</span>)}
          {cell(<span className="font-mono">{formatQuantity(totals.quantityIn)}</span>)}
          {cell(<span className="font-mono">{formatQuantity(totals.quantityOut)}</span>)}
          {cell(<span className="font-mono">{formatQuantity(totals.closingQuantity)}</span>)}
          {showMoney && cell(<span className="font-mono">{formatQuantity(totals.openingValue)}</span>)}
          {showMoney && cell(<span className="font-mono">{formatQuantity(totals.valueIn)}</span>)}
          {showMoney && cell(<span className="font-mono">{formatQuantity(totals.valueOut)}</span>)}
          {showMoney && cell(<span className="font-mono">{formatQuantity(totals.closingValue)}</span>)}
        </Table.Summary.Row>
      </Table.Summary>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <Card size="small" className="border border-slate-200/80">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Text type="secondary" className="text-xs">
              Kỳ báo cáo
            </Text>
            <RangePicker
              value={range}
              format="DD/MM/YYYY"
              allowClear={false}
              status={rangeError ? 'error' : undefined}
              onChange={(values) => {
                if (values?.[0] && values[1]) setRange([values[0], values[1]])
              }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <Text type="secondary" className="text-xs">
              Kho
            </Text>
            <Select<number>
              className="w-64"
              value={warehouseId ?? 0}
              loading={warehousesLoading}
              options={warehouseOptions}
              onChange={(v) => setWarehouseId(v === 0 ? undefined : v)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <Text type="secondary" className="text-xs">
              Danh mục
            </Text>
            <Select<number>
              className="w-52"
              allowClear
              placeholder="Tất cả danh mục"
              value={categoryId}
              options={categories.map((c) => ({ value: c.id, label: c.name }))}
              onChange={(v) => setCategoryId(v ?? undefined)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <Text type="secondary" className="text-xs">
              Sản phẩm
            </Text>
            <ProductSelect className="w-72" value={product?.id} selected={product} onChange={setProduct} placeholder="Tất cả sản phẩm" />
          </div>
          <div className="flex flex-col gap-1">
            <Text type="secondary" className="text-xs">
              Từ khóa
            </Text>
            <Input
              className="w-56"
              allowClear
              prefix={<SearchOutlined className="text-gray-400" />}
              placeholder="Mã SKU hoặc tên sản phẩm"
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <Text type="secondary" className="text-xs">
              Gom dòng
            </Text>
            <Select<GroupBy>
              className="w-52"
              value={groupBy}
              onChange={handleGroupByChange}
              options={[
                { value: 'PRODUCT_WAREHOUSE', label: 'Theo sản phẩm và kho' },
                { value: 'PRODUCT', label: 'Theo sản phẩm (gộp các kho)' },
              ]}
            />
          </div>
          <Space className="ml-auto">
            <Button icon={<ReloadOutlined />} onClick={() => setReloadToken((t) => t + 1)} disabled={Boolean(rangeError)}>
              Tải lại
            </Button>
            <Button
              type="primary"
              icon={<FileExcelOutlined />}
              onClick={handleExport}
              loading={excel.running}
              disabled={Boolean(rangeError)}
            >
              Tải Excel
            </Button>
          </Space>
        </div>
      </Card>

      {rangeError && <Alert type="error" showIcon message={rangeError} />}
      {error ? <ReportErrorAlert error={error} onRetry={() => setReloadToken((t) => t + 1)} /> : null}
      {data && !error && !data.quantitiesComplete && data.dataNote && (
        <Alert type="warning" showIcon message="Số liệu của kỳ chưa đầy đủ" description={data.dataNote} />
      )}
      {data && !error && data.quantitiesComplete && data.dataNote && <Alert type="info" showIcon message={data.dataNote} />}
      {data && !error && !showMoney && data.valuationNote && (
        <Alert type="info" showIcon message="Chưa có giá vốn nên báo cáo chưa có cột giá trị tiền" description={data.valuationNote} />
      )}

      <Card size="small" className="border border-slate-200/80">
        {data && !error && (
          <div className="mb-2 flex flex-wrap justify-between gap-2 text-xs text-gray-500">
            <span>
              Kỳ {formatDate(data.fromDate)} - {formatDate(data.toDate)} (giờ Việt Nam)
              {!allWarehouses && ', trong phạm vi các kho được phân công'}
            </span>
            <span>Lập lúc {formatInstant(data.generatedAt)}</span>
          </div>
        )}
        <Table<InOutBalanceRow>
          rowKey={(r) => `${r.productId}-${r.warehouseId ?? 'all'}`}
          size="middle"
          columns={columns}
          dataSource={error ? [] : rows}
          loading={loading}
          onChange={handleTableChange}
          summary={summary}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: rangeError ? 'Chọn kỳ báo cáo hợp lệ' : 'Không có sản phẩm nào có tồn hoặc phát sinh trong kỳ' }}
          pagination={{
            current: page + 1,
            pageSize,
            total: error ? 0 : (data?.rows.totalElements ?? 0),
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100, 200],
            showTotal: (total) => `${formatQuantity(total)} dòng`,
          }}
        />
      </Card>

      <ExportProgressModal open={excel.running} progress={excel.progress} onCancel={excel.cancel} />
    </div>
  )
}
