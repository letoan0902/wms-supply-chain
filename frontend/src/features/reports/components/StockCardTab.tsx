import { FileExcelOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Col, DatePicker, Empty, Row, Select, Space, Statistic, Table, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { Warehouse } from '../../warehouses/types'
import { fetchStockCard } from '../reportApi'
import { exportStockCard } from '../reportExport'
import {
  EMPTY_CELL,
  documentTypeLabel,
  formatDate,
  formatInstant,
  formatQuantity,
  movementTypeLabel,
  productLabel,
  validateRange,
  warehouseLabel,
} from '../reportFormat'
import type { ProductOption, StockCardLine, StockCardParams } from '../types'
import { useExcelExport } from '../useExcelExport'
import { useReportQuery } from '../useReportQuery'
import ExportProgressModal from './ExportProgressModal'
import ProductSelect from './ProductSelect'
import ReportErrorAlert from './ReportErrorAlert'

const { RangePicker } = DatePicker
const { Text } = Typography

const API_DATE = 'YYYY-MM-DD'
const DEFAULT_PAGE_SIZE = 50

interface Props {
  warehouses: Warehouse[]
  warehousesLoading: boolean
}

/** Tab "Thẻ kho": một sản phẩm tại một kho trong kỳ, tồn đầu, từng biến động theo thời gian, tồn cuối. */
export default function StockCardTab({ warehouses, warehousesLoading }: Props) {
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().startOf('month'), dayjs()])
  const [product, setProduct] = useState<ProductOption | null>(null)
  const [chosenWarehouseId, setChosenWarehouseId] = useState<number | undefined>(undefined)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [pageState, setPageState] = useState<{ filterKey: string; page: number }>({ filterKey: '', page: 0 })
  const [reloadToken, setReloadToken] = useState(0)
  const excel = useExcelExport()

  // Người dùng chỉ được gán một kho thì chọn sẵn kho đó
  const warehouseId = chosenWarehouseId ?? (warehouses.length === 1 ? warehouses[0].id : undefined)
  const fromDate = range[0].format(API_DATE)
  const toDate = range[1].format(API_DATE)
  const rangeError = validateRange(fromDate, toDate)
  const ready = product !== null && warehouseId !== undefined && !rangeError

  const filterKey = `${product?.id}|${warehouseId}|${fromDate}|${toDate}`
  const page = pageState.filterKey === filterKey ? pageState.page : 0

  const requestParams = useMemo<StockCardParams | null>(
    () =>
      product && warehouseId !== undefined && !rangeError
        ? { productId: product.id, warehouseId, fromDate, toDate, page, size: pageSize }
        : null,
    [product, warehouseId, fromDate, toDate, rangeError, page, pageSize],
  )
  const { data, loading, error } = useReportQuery(fetchStockCard, requestParams, reloadToken)
  const lines = data?.lines.content ?? []

  const warehouseNames = useMemo(() => new Map(warehouses.map((w) => [w.id, `${w.code} - ${w.name}`])), [warehouses])

  const columns: TableColumnsType<StockCardLine> = [
    {
      title: 'Thời điểm',
      key: 'occurredAt',
      width: 140,
      render: (_, l) => <span className="whitespace-nowrap">{formatInstant(l.occurredAt)}</span>,
    },
    {
      title: 'Loại biến động',
      key: 'movementType',
      render: (_, l) => movementTypeLabel(l.movementType, l.movementTypeLabel),
    },
    {
      title: 'Chứng từ',
      key: 'document',
      render: (_, l) => (
        <div>
          {l.documentType && <div className="text-xs text-gray-500">{documentTypeLabel(l.documentType)}</div>}
          <div className="font-mono text-xs">{l.documentNumber ?? (l.documentId !== null ? `ID ${l.documentId}` : EMPTY_CELL)}</div>
          {(l.referenceNumber || l.referenceId !== null) && (
            <div className="text-xs text-gray-500">
              {documentTypeLabel(l.referenceType) || 'Tham chiếu'}: {l.referenceNumber ?? `ID ${l.referenceId}`}
            </div>
          )}
          {l.partnerId !== null && <div className="text-xs text-gray-500">Nhà cung cấp ID {l.partnerId}</div>}
          {l.counterpartWarehouseId !== null && (
            <div className="text-xs text-gray-500">
              Kho bên kia: {warehouseNames.get(l.counterpartWarehouseId) ?? `Kho ID ${l.counterpartWarehouseId}`}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Số lô',
      key: 'lotNo',
      render: (_, l) =>
        l.lotNo ? (
          <div>
            <Link
              className="font-mono text-xs"
              to={`/reports/lot-trace?${new URLSearchParams({ lotNo: l.lotNo, productId: String(data?.productId ?? product?.id ?? '') })}`}
              title="Truy vết nguồn gốc lô này"
            >
              {l.lotNo}
            </Link>
            {l.expDate && <div className="text-xs text-gray-500">HSD {formatDate(l.expDate)}</div>}
          </div>
        ) : (
          EMPTY_CELL
        ),
    },
    {
      title: 'Vị trí',
      key: 'binLocationCode',
      render: (_, l) => <span className="font-mono text-xs">{l.binLocationCode ?? EMPTY_CELL}</span>,
    },
    {
      title: 'Nhập',
      key: 'quantityIn',
      align: 'right',
      render: (_, l) => <span className="font-mono text-green-700">{formatQuantity(l.quantityIn)}</span>,
    },
    {
      title: 'Xuất',
      key: 'quantityOut',
      align: 'right',
      render: (_, l) => <span className="font-mono text-red-600">{formatQuantity(l.quantityOut)}</span>,
    },
    {
      title: 'Tồn sau',
      key: 'balanceAfter',
      align: 'right',
      render: (_, l) => <span className="font-mono font-semibold">{formatQuantity(l.balanceAfter)}</span>,
    },
    {
      title: 'Người thực hiện',
      key: 'performedBy',
      render: (_, l) => l.performedBy ?? EMPTY_CELL,
    },
    {
      title: 'Diễn giải',
      key: 'reason',
      render: (_, l) => l.reason ?? '',
    },
  ]

  const handleExport = () => {
    if (!product || warehouseId === undefined) return
    void excel.run((onProgress, signal) =>
      exportStockCard({ productId: product.id, warehouseId, fromDate, toDate }, onProgress, signal),
    )
  }

  const summary = () => {
    if (!data) return null
    return (
      <Table.Summary>
        <Table.Summary.Row className="bg-slate-50 font-semibold">
          <Table.Summary.Cell index={0} colSpan={5}>
            Cộng phát sinh trong kỳ, tồn cuối kỳ
          </Table.Summary.Cell>
          <Table.Summary.Cell index={5} align="right">
            <span className="font-mono">{formatQuantity(data.totalIn)}</span>
          </Table.Summary.Cell>
          <Table.Summary.Cell index={6} align="right">
            <span className="font-mono">{formatQuantity(data.totalOut)}</span>
          </Table.Summary.Cell>
          <Table.Summary.Cell index={7} align="right">
            <span className="font-mono">{formatQuantity(data.closingQuantity)}</span>
          </Table.Summary.Cell>
          <Table.Summary.Cell index={8} colSpan={2} />
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
              Sản phẩm (bắt buộc)
            </Text>
            <ProductSelect className="w-80" value={product?.id} selected={product} onChange={setProduct} placeholder="Chọn sản phẩm" />
          </div>
          <div className="flex flex-col gap-1">
            <Text type="secondary" className="text-xs">
              Kho (bắt buộc)
            </Text>
            <Select<number>
              className="w-64"
              placeholder="Chọn kho"
              value={warehouseId}
              loading={warehousesLoading}
              onChange={setChosenWarehouseId}
              options={warehouses.map((w) => ({ value: w.id, label: `${w.code} - ${w.name}` }))}
            />
          </div>
          <div className="flex flex-col gap-1">
            <Text type="secondary" className="text-xs">
              Kỳ
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
          <Space className="ml-auto">
            <Button icon={<ReloadOutlined />} onClick={() => setReloadToken((t) => t + 1)} disabled={!ready}>
              Tải lại
            </Button>
            <Button type="primary" icon={<FileExcelOutlined />} onClick={handleExport} loading={excel.running} disabled={!ready}>
              Tải Excel
            </Button>
          </Space>
        </div>
      </Card>

      {rangeError && <Alert type="error" showIcon message={rangeError} />}

      {!product || warehouseId === undefined ? (
        <Card className="border border-slate-200/80">
          <Empty description="Chọn một sản phẩm và một kho để xem thẻ kho" />
        </Card>
      ) : (
        <>
          {error ? <ReportErrorAlert error={error} onRetry={() => setReloadToken((t) => t + 1)} /> : null}
          {data && !error && data.dataNote && (
            <Alert type="warning" showIcon message="Số liệu của kỳ chưa đầy đủ" description={data.dataNote} />
          )}
          {data && !error && !loading && (
            <Card size="small" className="border border-slate-200/80">
              <div className="mb-3 text-sm">
                <span className="font-semibold">{productLabel(data.skuCode, data.productName, data.productId)}</span>
                {data.uom && <span className="text-gray-500"> (ĐVT: {data.uom})</span>}
                <span className="text-gray-500">
                  {' '}
                  tại {warehouseLabel(data.warehouseCode, data.warehouseName, data.warehouseId)}, kỳ {formatDate(data.fromDate)} -{' '}
                  {formatDate(data.toDate)} (giờ Việt Nam)
                </span>
              </div>
              <Row gutter={[16, 16]}>
                <Col xs={12} md={6}>
                  <Statistic title="Tồn đầu kỳ" value={formatQuantity(data.openingQuantity)} />
                </Col>
                <Col xs={12} md={6}>
                  <Statistic title="Tổng nhập" value={formatQuantity(data.totalIn)} valueStyle={{ color: '#15803d' }} />
                </Col>
                <Col xs={12} md={6}>
                  <Statistic title="Tổng xuất" value={formatQuantity(data.totalOut)} valueStyle={{ color: '#dc2626' }} />
                </Col>
                <Col xs={12} md={6}>
                  <Statistic title="Tồn cuối kỳ" value={formatQuantity(data.closingQuantity)} />
                </Col>
              </Row>
            </Card>
          )}
          <Card size="small" className="border border-slate-200/80">
            <Table<StockCardLine>
              rowKey="movementId"
              size="middle"
              columns={columns}
              dataSource={error ? [] : lines}
              loading={loading}
              scroll={{ x: 'max-content' }}
              title={
                data && !error
                  ? () => (
                      <span className="text-sm">
                        Tồn đầu kỳ: <span className="font-mono font-semibold">{formatQuantity(data.openingQuantity)}</span>
                      </span>
                    )
                  : undefined
              }
              summary={error ? undefined : summary}
              locale={{ emptyText: 'Không có biến động nào trong kỳ' }}
              onChange={(pagination) => {
                const nextSize = pagination.pageSize ?? pageSize
                if (nextSize !== pageSize) {
                  setPageSize(nextSize)
                  setPageState({ filterKey, page: 0 })
                } else {
                  setPageState({ filterKey, page: (pagination.current ?? 1) - 1 })
                }
              }}
              pagination={{
                current: page + 1,
                pageSize,
                total: error ? 0 : (data?.lines.totalElements ?? 0),
                showSizeChanger: true,
                pageSizeOptions: [20, 50, 100, 200],
                showTotal: (total) => `${formatQuantity(total)} dòng`,
              }}
            />
          </Card>
        </>
      )}

      <ExportProgressModal open={excel.running} progress={excel.progress} onCancel={excel.cancel} />
    </div>
  )
}
