import { RedoOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Col, Input, Row, Switch, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatDateTime } from '../../../utils/format'
import ProductSelect from '../../reports/components/ProductSelect'
import type { ProductOption } from '../../reports/types'
import { useReportQuery } from '../../reports/useReportQuery'
import { fetchCountLines } from '../stocktakeApi'
import { formatQty } from '../stocktakeLogic'
import type { CountLinesQuery, StocktakeCountLine } from '../types'
import { productLabel, useProductBriefs } from '../useProductBriefs'

const { Text } = Typography

interface Props {
  sessionId: number
  /** Tăng lên để tải lại (sau khi đổi trạng thái đợt) */
  reloadToken: number
  /**
   * Cho chọn dòng để yêu cầu đếm lại (Quản lý kho khi đợt đang đếm). Hàm trả true nếu đã gửi thành công để bỏ chọn.
   */
  onRecount?: (lineIds: number[]) => Promise<boolean>
}

/**
 * Dòng đếm của đợt (GET .../count-lines): số đã đếm, số lần đếm, người đếm cuối; KHÔNG có tồn hệ thống. Lọc chưa
 * đếm, mã vị trí, hàng hóa; phân trang trên máy chủ.
 */
export default function CountLinesTable({ sessionId, reloadToken, onRecount }: Props) {
  const [onlyUncounted, setOnlyUncounted] = useState(false)
  const [binInput, setBinInput] = useState('')
  const [binCode, setBinCode] = useState('')
  const [product, setProduct] = useState<ProductOption | null>(null)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(50)
  const [selected, setSelected] = useState<number[]>([])
  const [recounting, setRecounting] = useState(false)

  const fetcher = useCallback(
    (query: CountLinesQuery, signal: AbortSignal) => fetchCountLines(sessionId, query, signal),
    [sessionId],
  )
  const query = useMemo<CountLinesQuery>(
    () => ({
      counted: onlyUncounted ? false : undefined,
      binCode: binCode.trim() || undefined,
      productId: product?.id,
      page: page - 1,
      size: pageSize,
    }),
    [onlyUncounted, binCode, product, page, pageSize],
  )
  const { data, loading, error } = useReportQuery(fetcher, query, reloadToken)
  const lines = useMemo(() => data?.content ?? [], [data])
  const products = useProductBriefs(useMemo(() => lines.map((l) => l.productId), [lines]))

  const columns: ColumnsType<StocktakeCountLine> = [
    {
      title: 'Vị trí',
      dataIndex: 'binLocationCode',
      key: 'bin',
      width: 160,
      render: (code: string) => <span className="font-mono">{code}</span>,
    },
    {
      title: 'Hàng hóa',
      key: 'product',
      render: (_, l) => (
        <div className="flex flex-col">
          <span>{productLabel(products, l.productId)}</span>
          <span className="flex flex-wrap gap-1">
            {l.unexpected && <Tag color="purple">Hàng phát sinh</Tag>}
            {l.recountRequested && <Tag color="orange">Đếm lại</Tag>}
          </span>
        </div>
      ),
    },
    { title: 'Số lô', dataIndex: 'lotNo', key: 'lot', width: 120, render: (v: string | null) => v || '—' },
    {
      title: 'Đã đếm',
      key: 'counted',
      width: 120,
      align: 'right',
      render: (_, l) =>
        l.counted ? (
          <span className="font-semibold">{formatQty(l.countedQuantity)}</span>
        ) : (
          <Tag className="!m-0">Chưa đếm</Tag>
        ),
    },
    { title: 'Số lần ghi', dataIndex: 'countTimes', key: 'times', width: 100, align: 'right' },
    {
      title: 'Người ghi cuối',
      key: 'last',
      width: 170,
      render: (_, l) =>
        l.lastCountedBy ? (
          <div className="flex flex-col">
            <span>{l.lastCountedBy}</span>
            <span className="text-xs text-slate-500">{formatDateTime(l.lastCountedAt)}</span>
          </div>
        ) : (
          '—'
        ),
    },
  ]

  return (
    <div className="flex flex-col gap-3">
      <Row gutter={[12, 12]} align="middle">
        <Col xs={24} md={7}>
          <Input
            allowClear
            prefix={<SearchOutlined className="text-gray-400" />}
            placeholder="Mã vị trí (Enter để lọc)"
            value={binInput}
            onChange={(e) => {
              setBinInput(e.target.value)
              if (!e.target.value) {
                setBinCode('')
                setPage(1)
              }
            }}
            onPressEnter={() => {
              setBinCode(binInput)
              setPage(1)
            }}
          />
        </Col>
        <Col xs={24} md={9}>
          <ProductSelect
            value={product?.id}
            selected={product}
            onChange={(p) => {
              setProduct(p)
              setPage(1)
            }}
            placeholder="Lọc theo hàng hóa"
            className="w-full"
          />
        </Col>
        <Col xs={24} md={onRecount ? 4 : 8}>
          <label className="flex items-center gap-2 cursor-pointer">
            <Switch
              checked={onlyUncounted}
              onChange={(checked) => {
                setOnlyUncounted(checked)
                setPage(1)
              }}
            />
            <Text>Chỉ dòng chưa đếm</Text>
          </label>
        </Col>
        {onRecount && (
          <Col xs={24} md={4} className="flex md:justify-end">
            <Button
              icon={<RedoOutlined />}
              disabled={selected.length === 0}
              loading={recounting}
              data-write-action
              onClick={async () => {
                setRecounting(true)
                try {
                  if (await onRecount(selected)) setSelected([])
                } finally {
                  setRecounting(false)
                }
              }}
            >
              Đếm lại ({selected.length})
            </Button>
          </Col>
        )}
      </Row>
      {error ? <Alert type="error" showIcon message={getApiErrorMessage(error, 'Không tải được dòng đếm')} /> : null}
      <Table<StocktakeCountLine>
        rowKey="lineId"
        size="small"
        columns={columns}
        dataSource={lines}
        loading={loading}
        scroll={{ x: 860 }}
        rowSelection={
          onRecount
            ? {
                selectedRowKeys: selected,
                preserveSelectedRowKeys: true,
                onChange: (keys) => setSelected(keys.map(Number)),
              }
            : undefined
        }
        locale={{ emptyText: 'Không có dòng đếm phù hợp' }}
        pagination={{
          current: page,
          pageSize,
          total: data?.totalElements ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          showTotal: (total) => `${total} dòng`,
          onChange: (nextPage, nextSize) => {
            setPage(nextSize !== pageSize ? 1 : nextPage)
            setPageSize(nextSize)
          },
        }}
      />
    </div>
  )
}
