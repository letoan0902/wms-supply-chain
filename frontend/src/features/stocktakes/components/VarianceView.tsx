import { RedoOutlined, SaveOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Col, Input, Result, Row, Statistic, Switch, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useMemo, useState } from 'react'
import { getApiErrorMessage, getErrorStatus } from '../../../services/api'
import { formatDateTime } from '../../../utils/format'
import ProductSelect from '../../reports/components/ProductSelect'
import type { ProductOption } from '../../reports/types'
import { useReportQuery } from '../../reports/useReportQuery'
import { fetchVariances, requestRecount, saveLineReason } from '../stocktakeApi'
import { formatQty, formatSignedQty, varianceTone, type StocktakeAbilities } from '../stocktakeLogic'
import type { StocktakeSession, VarianceLine, VarianceQuery } from '../types'
import { productLabel, useProductBriefs } from '../useProductBriefs'
import ActionErrorDetails from './ActionErrorDetails'

const { Text } = Typography

interface Props {
  session: StocktakeSession
  abilities: StocktakeAbilities
  reloadToken: number
  onSessionUpdated: (session: StocktakeSession) => void
}

const TONE_CLASS: Record<ReturnType<typeof varianceTone>, string> = {
  increase: 'text-emerald-600 font-semibold',
  decrease: 'text-red-600 font-semibold',
  zero: 'text-slate-500',
  none: 'text-slate-400',
}

/** Nhân viên kho (và vai trò khác không có quyền) không được xem tồn hệ thống: BR-12. */
function VarianceForbidden() {
  return (
    <Result
      status="403"
      title="Không có quyền xem chênh lệch"
      subTitle="Tồn hệ thống và chênh lệch kiểm kê chỉ dành cho Quản trị viên, Kế toán và Quản lý kho phụ trách kho. Nhân viên kho đếm mù nên không xem được số hệ thống."
    />
  )
}

/**
 * Đối soát chênh lệch (GET .../variances): thẻ tổng hợp, bảng dòng có tồn hệ thống, số đếm, chênh lệch (màu theo
 * dấu), cờ xung đột giữ chỗ, lý do (ghi được khi chờ Quản lý kho duyệt), chọn dòng để yêu cầu đếm lại.
 * Không có giá trị tiền (máy chủ chưa có giá vốn).
 */
export default function VarianceView({ session, abilities, reloadToken, onSessionUpdated }: Props) {
  const { modal, message } = App.useApp()
  const [onlyDifferences, setOnlyDifferences] = useState(true)
  const [binInput, setBinInput] = useState('')
  const [binCode, setBinCode] = useState('')
  const [product, setProduct] = useState<ProductOption | null>(null)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(50)
  const [localToken, setLocalToken] = useState(0)
  const [selected, setSelected] = useState<number[]>([])
  const [reasonDrafts, setReasonDrafts] = useState<Record<number, string>>({})
  const [savedReasons, setSavedReasons] = useState<Record<number, string | null>>({})
  const [savingLine, setSavingLine] = useState<number | null>(null)
  const [recounting, setRecounting] = useState(false)

  const available = abilities.variance && session.finishedAt !== null
  const fetcher = useCallback(
    (query: VarianceQuery, signal: AbortSignal) => fetchVariances(session.id, query, signal),
    [session.id],
  )
  const query = useMemo<VarianceQuery | null>(
    () =>
      available
        ? {
            onlyDifferences,
            binCode: binCode.trim() || undefined,
            productId: product?.id,
            page: page - 1,
            size: pageSize,
          }
        : null,
    [available, onlyDifferences, binCode, product, page, pageSize],
  )
  const { data, loading, error } = useReportQuery(fetcher, query, reloadToken * 1000 + localToken)
  const lines = useMemo(() => data?.lines.content ?? [], [data])
  const products = useProductBriefs(useMemo(() => lines.map((l) => l.productId), [lines]))

  const canEditReason = session.status === 'COUNTED' && abilities.manage
  const canRecount = session.status === 'COUNTED' && abilities.manage

  if (!abilities.variance) return <VarianceForbidden />
  if (session.finishedAt === null) {
    return (
      <Alert
        type="info"
        showIcon
        message={
          session.status === 'COUNTING' && session.startedAt
            ? 'Đợt đang đếm (hoặc đang đếm lại): chênh lệch được tính khi kết thúc đếm.'
            : 'Đợt chưa kết thúc đếm nên chưa có chênh lệch.'
        }
      />
    )
  }
  if (error && getErrorStatus(error) === 403) return <VarianceForbidden />

  const reasonOf = (line: VarianceLine): string | null =>
    line.lineId in savedReasons ? savedReasons[line.lineId] : line.reason

  const saveReason = async (line: VarianceLine) => {
    const draft = reasonDrafts[line.lineId] ?? reasonOf(line) ?? ''
    if (draft.trim().length > 255) {
      message.error('Lý do chênh lệch tối đa 255 ký tự')
      return
    }
    setSavingLine(line.lineId)
    try {
      const updated = await saveLineReason(session.id, line.lineId, draft)
      setSavedReasons((prev) => ({ ...prev, [line.lineId]: updated.reason }))
      setReasonDrafts((prev) => {
        const next = { ...prev }
        delete next[line.lineId]
        return next
      })
      message.success(`Đã lưu lý do cho dòng ${line.binLocationCode}`)
    } catch (err) {
      modal.error({
        title: 'Không lưu được lý do',
        content: <ActionErrorDetails error={err} fallback="Không lưu được lý do chênh lệch" />,
      })
    } finally {
      setSavingLine(null)
    }
  }

  const handleRecount = () => {
    if (selected.length === 0) return
    modal.confirm({
      title: `Yêu cầu đếm lại ${selected.length} dòng?`,
      width: 520,
      okText: 'Yêu cầu đếm lại',
      cancelText: 'Đóng',
      okButtonProps: { 'data-write-action': 'true' },
      content:
        'Số đếm của các dòng này bị xóa, đợt quay lại Đang đếm (vị trí vẫn đóng băng). Nhân viên đếm lại các dòng có thẻ "Đếm lại", sau đó Quản lý kho bấm Kết thúc đếm để tính lại chênh lệch.',
      onOk: async () => {
        setRecounting(true)
        try {
          const updated = await requestRecount(session.id, selected)
          setSelected([])
          onSessionUpdated(updated)
          message.success(`Đã yêu cầu đếm lại ${selected.length} dòng, đợt quay lại Đang đếm`)
        } catch (err) {
          modal.error({
            title: 'Không yêu cầu đếm lại được',
            content: <ActionErrorDetails error={err} fallback="Không yêu cầu đếm lại được" />,
          })
          setLocalToken((t) => t + 1)
        } finally {
          setRecounting(false)
        }
      },
    })
  }

  const columns: ColumnsType<VarianceLine> = [
    {
      title: 'Vị trí',
      dataIndex: 'binLocationCode',
      key: 'bin',
      width: 150,
      fixed: 'left',
      render: (code: string) => <span className="font-mono">{code}</span>,
    },
    {
      title: 'Hàng hóa',
      key: 'product',
      width: 240,
      render: (_, l) => (
        <div className="flex flex-col">
          <span>{productLabel(products, l.productId)}</span>
          {l.lotNo && <span className="text-xs text-slate-500">Lô {l.lotNo}</span>}
        </div>
      ),
    },
    {
      title: 'Tồn hệ thống',
      dataIndex: 'systemQuantity',
      key: 'system',
      width: 110,
      align: 'right',
      render: (v: number) => formatQty(v),
    },
    {
      title: 'Đang giữ chỗ',
      dataIndex: 'systemReservedQuantity',
      key: 'reserved',
      width: 110,
      align: 'right',
      render: (v: number) => formatQty(v),
    },
    {
      title: 'Thực đếm',
      dataIndex: 'countedQuantity',
      key: 'counted',
      width: 100,
      align: 'right',
      render: (v: number | null, l) => (l.counted ? formatQty(v) : <Tag className="!m-0">Chưa đếm</Tag>),
    },
    {
      title: 'Chênh lệch',
      dataIndex: 'varianceQuantity',
      key: 'variance',
      width: 110,
      align: 'right',
      render: (v: number | null) => <span className={TONE_CLASS[varianceTone(v)]}>{formatSignedQty(v)}</span>,
    },
    {
      title: 'Cờ',
      key: 'flags',
      width: 170,
      render: (_, l) => (
        <div className="flex flex-wrap gap-1">
          {l.reservedConflict && (
            <Tag color="red" className="!m-0">
              Nhỏ hơn giữ chỗ
            </Tag>
          )}
          {l.unexpected && (
            <Tag color="purple" className="!m-0">
              Hàng phát sinh
            </Tag>
          )}
          {l.recountRequested && (
            <Tag color="orange" className="!m-0">
              Đã đếm lại
            </Tag>
          )}
        </div>
      ),
    },
    {
      title: 'Người đếm cuối',
      key: 'last',
      width: 150,
      render: (_, l) =>
        l.lastCountedBy ? (
          <div className="flex flex-col">
            <span>{l.lastCountedBy}</span>
            <span className="text-xs text-slate-500">
              {formatDateTime(l.lastCountedAt)} · {l.countTimes} lần
            </span>
          </div>
        ) : (
          '—'
        ),
    },
    {
      title: 'Lý do chênh lệch',
      key: 'reason',
      width: 280,
      render: (_, l) => {
        const current = reasonOf(l)
        if (!canEditReason) return current || <Text type="secondary">—</Text>
        const draft = reasonDrafts[l.lineId]
        const value = draft ?? current ?? ''
        const dirty = draft !== undefined && draft.trim() !== (current ?? '')
        return (
          <div className="flex gap-1">
            <Input
              size="small"
              value={value}
              maxLength={255}
              placeholder="Ví dụ: Hàng vỡ"
              onChange={(e) => setReasonDrafts((prev) => ({ ...prev, [l.lineId]: e.target.value }))}
              onPressEnter={() => {
                if (dirty) void saveReason(l)
              }}
            />
            <Button
              size="small"
              icon={<SaveOutlined />}
              disabled={!dirty}
              loading={savingLine === l.lineId}
              onClick={() => void saveReason(l)}
              title="Lưu lý do"
            />
          </div>
        )
      },
    },
  ]

  const summary = data?.summary

  return (
    <div className="flex flex-col gap-3">
      {error ? (
        <Alert type="error" showIcon message={getApiErrorMessage(error, 'Không tải được báo cáo chênh lệch')} />
      ) : null}

      {summary && (
        <Row gutter={[12, 12]}>
          <Col xs={12} md={6} xl={3}>
            <Card size="small">
              <Statistic title="Tổng dòng" value={summary.totalLines} />
            </Card>
          </Col>
          <Col xs={12} md={6} xl={3}>
            <Card size="small">
              <Statistic title="Đã đếm" value={summary.countedLines} />
            </Card>
          </Col>
          <Col xs={12} md={6} xl={3}>
            <Card size="small">
              <Statistic title="Dòng có lệch" value={summary.linesWithVariance} />
            </Card>
          </Col>
          <Col xs={12} md={6} xl={4}>
            <Card size="small">
              <Statistic
                title={`Thừa (${summary.increaseLines} dòng)`}
                value={formatSignedQty(summary.totalIncreaseQuantity)}
                valueStyle={{ color: '#059669' }}
              />
            </Card>
          </Col>
          <Col xs={12} md={6} xl={4}>
            <Card size="small">
              <Statistic
                title={`Thiếu (${summary.decreaseLines} dòng)`}
                value={formatSignedQty(-Number(summary.totalDecreaseQuantity))}
                valueStyle={{ color: '#dc2626' }}
              />
            </Card>
          </Col>
          <Col xs={12} md={6} xl={3}>
            <Card size="small">
              <Statistic
                title="Chênh lệch ròng"
                value={formatSignedQty(summary.netVarianceQuantity)}
                valueStyle={{ color: varianceTone(summary.netVarianceQuantity) === 'decrease' ? '#dc2626' : undefined }}
              />
            </Card>
          </Col>
          <Col xs={24} md={12} xl={4}>
            <Card size="small">
              <Statistic
                title="Dòng xung đột giữ chỗ"
                value={summary.reservedConflictLines}
                valueStyle={{ color: summary.reservedConflictLines > 0 ? '#dc2626' : undefined }}
              />
            </Card>
          </Col>
        </Row>
      )}

      {summary && summary.reservedConflictLines > 0 && (
        <Alert
          type="error"
          showIcon
          message={`Có ${summary.reservedConflictLines} dòng có số đếm nhỏ hơn số đang giữ chỗ cho đơn bán`}
          description="Duyệt sẽ bị máy chủ từ chối cho tới khi hủy hoặc chuyển giữ chỗ của các đơn liên quan, hoặc đếm lại các dòng này."
        />
      )}

      <Row gutter={[12, 12]} align="middle">
        <Col xs={24} md={6}>
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
        <Col xs={24} md={8}>
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
        <Col xs={24} md={5}>
          <label className="flex items-center gap-2 cursor-pointer">
            <Switch
              checked={onlyDifferences}
              onChange={(checked) => {
                setOnlyDifferences(checked)
                setPage(1)
              }}
            />
            <Text>Chỉ dòng có lệch</Text>
          </label>
        </Col>
        {canRecount && (
          <Col xs={24} md={5} className="flex md:justify-end">
            <Button
              icon={<RedoOutlined />}
              disabled={selected.length === 0}
              loading={recounting}
              onClick={handleRecount}
              data-write-action
            >
              Yêu cầu đếm lại ({selected.length})
            </Button>
          </Col>
        )}
      </Row>

      <Table<VarianceLine>
        rowKey="lineId"
        size="small"
        columns={columns}
        dataSource={lines}
        loading={loading}
        scroll={{ x: 1450 }}
        rowSelection={
          canRecount
            ? {
                selectedRowKeys: selected,
                preserveSelectedRowKeys: true,
                onChange: (keys) => setSelected(keys.map(Number)),
              }
            : undefined
        }
        locale={{ emptyText: onlyDifferences ? 'Không có dòng nào chênh lệch' : 'Không có dòng phù hợp' }}
        pagination={{
          current: page,
          pageSize,
          total: data?.lines.totalElements ?? 0,
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
