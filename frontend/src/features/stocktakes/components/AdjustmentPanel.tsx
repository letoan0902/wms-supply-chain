import { Alert, Descriptions, Result, Spin, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo } from 'react'
import { getApiErrorMessage, getErrorStatus } from '../../../services/api'
import { formatDateTime } from '../../../utils/format'
import { useReportQuery } from '../../reports/useReportQuery'
import { fetchAdjustment } from '../stocktakeApi'
import {
  ADJUSTMENT_STATUS_COLORS,
  ADJUSTMENT_STATUS_LABELS,
  formatQty,
  formatSignedQty,
  varianceTone,
} from '../stocktakeLogic'
import type { StockAdjustmentLine } from '../types'
import { productLabel, useProductBriefs } from '../useProductBriefs'

const { Text } = Typography

interface Props {
  adjustmentId: number
  reloadToken: number
}

const loadAdjustment = (id: number, signal: AbortSignal) => fetchAdjustment(id, signal)

function personAt(by: string | null, at: string | null): string {
  if (!by) return '—'
  return at ? `${by}, ${formatDateTime(at)}` : by
}

/**
 * Phiếu điều chỉnh tồn kho sinh từ đợt kiểm kê (GET /api/inventory/stock-adjustments/{id}): trạng thái, người đề
 * nghị, người duyệt hoặc từ chối, các dòng với tồn hệ thống, thực đếm, chênh lệch, tồn trước và sau khi duyệt.
 */
export default function AdjustmentPanel({ adjustmentId, reloadToken }: Props) {
  const { data: adjustment, loading, error } = useReportQuery(loadAdjustment, adjustmentId, reloadToken)
  const lines = useMemo(() => adjustment?.lines ?? [], [adjustment])
  const products = useProductBriefs(useMemo(() => lines.map((l) => l.productId), [lines]))

  if (error && getErrorStatus(error) === 403) {
    return (
      <Result
        status="403"
        title="Không có quyền xem phiếu điều chỉnh"
        subTitle="Phiếu điều chỉnh có tồn hệ thống nên chỉ dành cho Quản trị viên, Kế toán và Quản lý kho phụ trách kho."
      />
    )
  }
  if (error) {
    return <Alert type="error" showIcon message={getApiErrorMessage(error, 'Không tải được phiếu điều chỉnh')} />
  }
  if (!adjustment) {
    return loading ? (
      <div className="flex justify-center py-8">
        <Spin />
      </div>
    ) : null
  }

  const approved = adjustment.status === 'APPROVED'
  const columns: ColumnsType<StockAdjustmentLine> = [
    {
      title: 'Vị trí',
      dataIndex: 'binLocationCode',
      key: 'bin',
      width: 150,
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
    { title: 'Tồn hệ thống', dataIndex: 'systemQuantity', key: 'system', width: 110, align: 'right', render: formatQty },
    { title: 'Thực đếm', dataIndex: 'countedQuantity', key: 'counted', width: 100, align: 'right', render: formatQty },
    {
      title: 'Chênh lệch',
      dataIndex: 'differenceQuantity',
      key: 'diff',
      width: 110,
      align: 'right',
      render: (v: number) => (
        <span
          className={
            varianceTone(v) === 'increase'
              ? 'text-emerald-600 font-semibold'
              : varianceTone(v) === 'decrease'
                ? 'text-red-600 font-semibold'
                : ''
          }
        >
          {formatSignedQty(v)}
        </span>
      ),
    },
    {
      title: 'Tồn trước',
      dataIndex: 'quantityBefore',
      key: 'before',
      width: 100,
      align: 'right',
      render: (v: number | null) => (v === null ? <Text type="secondary">—</Text> : formatQty(v)),
    },
    {
      title: 'Tồn sau',
      dataIndex: 'quantityAfter',
      key: 'after',
      width: 100,
      align: 'right',
      render: (v: number | null) => (v === null ? <Text type="secondary">—</Text> : formatQty(v)),
    },
    { title: 'Lý do', dataIndex: 'reason', key: 'reason', width: 200, render: (v: string | null) => v || '—' },
  ]

  return (
    <div className="flex flex-col gap-3">
      <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} bordered>
        <Descriptions.Item label="Mã phiếu">
          <span className="font-mono font-semibold">{adjustment.code}</span>
        </Descriptions.Item>
        <Descriptions.Item label="Trạng thái">
          <Tag color={ADJUSTMENT_STATUS_COLORS[adjustment.status]} className="!m-0">
            {ADJUSTMENT_STATUS_LABELS[adjustment.status] ?? adjustment.status}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Số dòng">{adjustment.totalLines}</Descriptions.Item>
        <Descriptions.Item label="Tổng thừa">
          <span className="text-emerald-600">{formatSignedQty(adjustment.totalIncreaseQuantity)}</span>
        </Descriptions.Item>
        <Descriptions.Item label="Tổng thiếu">
          <span className="text-red-600">{formatSignedQty(-Number(adjustment.totalDecreaseQuantity))}</span>
        </Descriptions.Item>
        <Descriptions.Item label="Quản lý kho đề nghị">
          {personAt(adjustment.requestedBy, adjustment.requestedAt)}
        </Descriptions.Item>
        <Descriptions.Item label="Kế toán duyệt">{personAt(adjustment.approvedBy, adjustment.approvedAt)}</Descriptions.Item>
        {adjustment.rejectedBy && (
          <Descriptions.Item label="Từ chối">
            {personAt(adjustment.rejectedBy, adjustment.rejectedAt)}
            {adjustment.rejectReason ? `: ${adjustment.rejectReason}` : ''}
          </Descriptions.Item>
        )}
        {adjustment.notes && <Descriptions.Item label="Ghi chú">{adjustment.notes}</Descriptions.Item>}
      </Descriptions>

      <Table<StockAdjustmentLine>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={lines}
        loading={loading}
        scroll={{ x: 1110 }}
        pagination={{ pageSize: 50, hideOnSinglePage: true, showSizeChanger: false }}
      />

      {approved ? (
        <Text type="secondary" className="text-xs">
          Tồn trước và tồn sau là tồn của đúng vị trí và số lô lúc Kế toán duyệt. Lịch sử biến động của từng mặt hàng xem
          ở menu Báo cáo, tab Thẻ kho: chọn hàng hóa và kho {adjustment.warehouseName ?? ''}, chứng từ là mã phiếu{' '}
          {adjustment.code}.
        </Text>
      ) : (
        <Text type="secondary" className="text-xs">
          Tồn trước và tồn sau có giá trị sau khi Kế toán duyệt phiếu.
        </Text>
      )}
    </div>
  )
}
