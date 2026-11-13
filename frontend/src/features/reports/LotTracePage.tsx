import { ArrowLeftOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Col, Descriptions, Empty, Input, Row, Space, Spin, Statistic, Table, Tag, Tooltip, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getStoredUser } from '../../utils/storage'
import { TRANSFER_STATUS_CONFIG } from '../transfers/statusConfig'
import type { StockTransferStatus } from '../transfers/types'
import LotTraceTree from './components/LotTraceTree'
import ReportErrorAlert from './components/ReportErrorAlert'
import ReportForbidden from './components/ReportForbidden'
import { fetchLotTrace, lotTraceCandidates } from './reportApi'
import { getReportAccess } from './reportAccess'
import {
  EMPTY_CELL,
  documentTypeLabel,
  formatDate,
  formatInstant,
  formatQuantity,
  movementTypeLabel,
  productLabel,
  toNumber,
  warehouseLabel,
} from './reportFormat'
import type { LotTraceFlow, LotTraceLocation, LotTraceParams, LotTraceTimelineEvent } from './types'
import { useReportQuery } from './useReportQuery'

const { Title, Text } = Typography

/** Số chứng từ kèm loại. Chưa có màn hình chi tiết theo ID hay số chứng từ để mở nên hiển thị dạng chữ. */
function DocumentRef({ type, number, id }: { type: string | null; number: string | null; id: number | null }) {
  if (!number && id === null) return <>{EMPTY_CELL}</>
  return (
    <div>
      {type && <div className="text-xs text-gray-500">{documentTypeLabel(type)}</div>}
      <div className="font-mono text-xs">{number ?? `ID ${id}`}</div>
    </div>
  )
}

const quantity = (v: number | null) => <span className="font-mono">{formatQuantity(v)}</span>

/** Màn hình truy vết nguồn gốc theo số lô: lô từ đâu tới, đang ở đâu, đã đi đâu, diễn biến và cây truy vết. */
export default function LotTracePage() {
  const navigate = useNavigate()
  const access = getReportAccess(getStoredUser())
  const [searchParams, setSearchParams] = useSearchParams()
  const lotNo = (searchParams.get('lotNo') ?? '').trim()
  const productIdParam = Number(searchParams.get('productId'))
  const productId = Number.isInteger(productIdParam) && productIdParam > 0 ? productIdParam : undefined
  const [input, setInput] = useState(lotNo)
  const [reloadToken, setReloadToken] = useState(0)

  // Đồng bộ ô nhập khi số lô trên URL đổi (mở từ thẻ kho, nút quay lại của trình duyệt)
  const [syncedLot, setSyncedLot] = useState(lotNo)
  if (syncedLot !== lotNo) {
    setSyncedLot(lotNo)
    setInput(lotNo)
  }

  const params = useMemo<LotTraceParams | null>(
    () => (access.canView && lotNo ? { lotNo, productId } : null),
    [access.canView, lotNo, productId],
  )
  const { data, loading, error } = useReportQuery(fetchLotTrace, params, reloadToken)
  const candidates = lotTraceCandidates(error)
  const current = data && !error && data.lotNo === lotNo && (productId === undefined || data.productId === productId) ? data : null

  if (!access.canView) return <ReportForbidden />

  const search = (value: string) => {
    const lot = value.trim()
    if (!lot) {
      setSearchParams({})
      return
    }
    if (lot === lotNo && productId === undefined) {
      setReloadToken((t) => t + 1)
      return
    }
    setSearchParams({ lotNo: lot })
  }

  const chooseProduct = (id: number) => setSearchParams({ lotNo, productId: String(id) })

  const originColumns: TableColumnsType<LotTraceFlow> = [
    { title: 'Loại', key: 'type', render: (_, f) => movementTypeLabel(f.movementType, f.movementTypeLabel) },
    { title: 'Chứng từ', key: 'doc', render: (_, f) => <DocumentRef type={f.documentType} number={f.documentNumber} id={f.documentId} /> },
    {
      title: 'Đơn mua',
      key: 'ref',
      render: (_, f) =>
        f.referenceNumber || f.referenceId !== null ? (
          <DocumentRef type={f.referenceType} number={f.referenceNumber} id={f.referenceId} />
        ) : (
          EMPTY_CELL
        ),
    },
    { title: 'Nhà cung cấp', key: 'supplier', render: (_, f) => (f.supplierId !== null ? `ID ${f.supplierId}` : EMPTY_CELL) },
    { title: 'Kho', key: 'wh', render: (_, f) => warehouseLabel(f.warehouseCode, f.warehouseName, f.warehouseId) || EMPTY_CELL },
    { title: 'Vị trí', key: 'bins', render: (_, f) => <span className="font-mono text-xs">{(f.binLocationCodes ?? []).join(', ') || EMPTY_CELL}</span> },
    { title: 'Số lượng', key: 'qty', align: 'right', render: (_, f) => quantity(f.quantity) },
    { title: 'Thời điểm', key: 'at', render: (_, f) => formatInstant(f.occurredAt) },
    { title: 'Người thực hiện', key: 'by', render: (_, f) => f.performedBy ?? EMPTY_CELL },
  ]

  const locationColumns: TableColumnsType<LotTraceLocation> = [
    { title: 'Kho', key: 'wh', render: (_, l) => warehouseLabel(l.warehouseCode, l.warehouseName, l.warehouseId) || EMPTY_CELL },
    { title: 'Vị trí', key: 'bin', render: (_, l) => <span className="font-mono text-xs">{l.binLocationCode ?? EMPTY_CELL}</span> },
    { title: 'Số lượng', key: 'qty', align: 'right', render: (_, l) => quantity(l.quantity) },
    { title: 'Đang giữ chỗ', key: 'reserved', align: 'right', render: (_, l) => quantity(l.reservedQuantity) },
    { title: 'Khả dụng', key: 'available', align: 'right', render: (_, l) => quantity(l.availableQuantity) },
    { title: 'Hạn dùng', key: 'exp', render: (_, l) => formatDate(l.expDate) },
    {
      title: 'Tình trạng',
      key: 'status',
      render: (_, l) => (
        <Space size={4} wrap>
          {l.locked && (
            <Tooltip title={l.lockedReason ?? undefined}>
              <Tag color="red">Đang khóa</Tag>
            </Tooltip>
          )}
          {l.expired && <Tag color="volcano">Hết hạn</Tag>}
          {!l.expired && l.daysUntilExpiry !== null && (
            <Tag color={l.daysUntilExpiry <= 30 ? 'gold' : 'default'}>Còn {l.daysUntilExpiry} ngày</Tag>
          )}
          {!l.locked && !l.expired && l.daysUntilExpiry === null && <Tag color="green">Bình thường</Tag>}
        </Space>
      ),
    },
  ]

  const outflowColumns: TableColumnsType<LotTraceFlow> = [
    { title: 'Loại', key: 'type', render: (_, f) => movementTypeLabel(f.movementType, f.movementTypeLabel) },
    { title: 'Chứng từ', key: 'doc', render: (_, f) => <DocumentRef type={f.documentType} number={f.documentNumber} id={f.documentId} /> },
    { title: 'Từ kho', key: 'wh', render: (_, f) => warehouseLabel(f.warehouseCode, f.warehouseName, f.warehouseId) || EMPTY_CELL },
    { title: 'Số lượng', key: 'qty', align: 'right', render: (_, f) => quantity(f.quantity) },
    { title: 'Thời điểm', key: 'at', render: (_, f) => formatInstant(f.occurredAt) },
    {
      title: 'Nơi nhận',
      key: 'dest',
      render: (_, f) => {
        const t = f.transfer
        if (!t) return f.movementType === 'SALES_DISPATCH' ? 'Khách hàng của đơn bán' : EMPTY_CELL
        const status = t.status ? TRANSFER_STATUS_CONFIG[t.status as StockTransferStatus] : undefined
        return (
          <div className="text-xs">
            <div>
              <span className="font-medium">
                {warehouseLabel(t.destinationWarehouseCode, t.destinationWarehouseName, t.destinationWarehouseId)}
              </span>{' '}
              {status ? <Tag color={status.color}>{status.label}</Tag> : t.status && <Tag>{t.status}</Tag>}
            </div>
            {t.itemReceivedQuantity !== null && (
              <div className="text-gray-500">
                Đã nhận {formatQuantity(t.itemReceivedQuantity)}
                {toNumber(t.itemDamagedQuantity) ? `, thiếu hoặc hỏng ${formatQuantity(t.itemDamagedQuantity)}` : ''} (cả dòng sản phẩm của
                phiếu)
              </div>
            )}
            {t.receivedAt && (
              <div className="text-gray-500">
                Nhận lúc {formatInstant(t.receivedAt)}
                {t.receivedBy ? ` bởi ${t.receivedBy}` : ''}
                {t.destinationBinLocationCode ? `, vị trí ${t.destinationBinLocationCode}` : ''}
              </div>
            )}
            {!t.lotTrackedAtDestination && <div className="text-gray-400">Kho đích không giữ số lô</div>}
          </div>
        )
      },
    },
    { title: 'Người thực hiện', key: 'by', render: (_, f) => f.performedBy ?? EMPTY_CELL },
  ]

  const timelineColumns: TableColumnsType<LotTraceTimelineEvent> = [
    { title: 'Thời điểm', key: 'at', render: (_, e) => <span className="whitespace-nowrap">{formatInstant(e.occurredAt)}</span> },
    { title: 'Biến động', key: 'type', render: (_, e) => movementTypeLabel(e.movementType, e.movementTypeLabel) },
    {
      title: 'Chứng từ',
      key: 'doc',
      render: (_, e) => (
        <div>
          <DocumentRef type={e.documentType} number={e.documentNumber} id={e.documentId} />
          {e.referenceNumber && (
            <div className="text-xs text-gray-500">
              {documentTypeLabel(e.referenceType) || 'Tham chiếu'}: {e.referenceNumber}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Kho, vị trí',
      key: 'wh',
      render: (_, e) => (
        <div>
          <div>{warehouseLabel(e.warehouseCode, e.warehouseName, e.warehouseId) || EMPTY_CELL}</div>
          {e.binLocationCode && <div className="font-mono text-xs text-gray-500">{e.binLocationCode}</div>}
        </div>
      ),
    },
    { title: 'Nhập', key: 'in', align: 'right', render: (_, e) => <span className="font-mono text-green-700">{formatQuantity(e.quantityIn)}</span> },
    { title: 'Xuất', key: 'out', align: 'right', render: (_, e) => <span className="font-mono text-red-600">{formatQuantity(e.quantityOut)}</span> },
    {
      title: 'Tồn của lô sau',
      key: 'balance',
      align: 'right',
      render: (_, e) => <span className="font-mono font-semibold">{formatQuantity(e.lotBalanceAfter)}</span>,
    },
    { title: 'Người thực hiện', key: 'by', render: (_, e) => e.performedBy ?? EMPTY_CELL },
  ]

  const s = current?.summary
  const ledger = toNumber(s?.ledgerBalance)
  const onHand = toNumber(s?.currentOnHand)
  const mismatch = ledger !== null && onHand !== null && Math.abs(ledger - onHand) > 0.0005

  return (
    <div className="flex flex-col gap-4 w-full">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Title level={4} className="!mb-0">
            Truy vết nguồn gốc theo số lô
          </Title>
          <Text type="secondary" className="text-xs">
            Lô hàng nhập từ phiếu nhập, đơn mua, nhà cung cấp nào; đang nằm ở vị trí nào; đã điều chuyển hoặc xuất bán theo
            chứng từ nào
            {access.allWarehouses ? '.' : ', trong phạm vi các kho bạn được phân công.'}
          </Text>
        </div>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/reports')}>
          Về báo cáo kho
        </Button>
      </div>

      <Card size="small" className="border border-slate-200/80">
        <Input.Search
          className="max-w-xl"
          placeholder="Nhập số lô, ví dụ số lô in trên bao bì"
          enterButton="Truy vết"
          allowClear
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onSearch={search}
          loading={loading}
        />
      </Card>

      {!lotNo && (
        <Card className="border border-slate-200/80">
          <Empty description="Nhập số lô để xem nguồn gốc và đường đi của lô hàng" />
        </Card>
      )}

      {lotNo && candidates && (
        <Alert
          type="warning"
          showIcon
          message={`Số lô ${lotNo} được dùng cho nhiều sản phẩm`}
          description={
            <div className="flex flex-col gap-2">
              <span>Chọn sản phẩm cần truy vết:</span>
              <Space wrap>
                {candidates.map((c) => (
                  <Button key={c.productId} onClick={() => chooseProduct(c.productId)}>
                    {productLabel(c.skuCode, c.productName, c.productId)}
                  </Button>
                ))}
              </Space>
            </div>
          }
        />
      )}
      {lotNo && error && !candidates ? <ReportErrorAlert error={error} fallback="Không truy vết được số lô." onRetry={() => setReloadToken((t) => t + 1)} /> : null}

      {lotNo && loading && !current && (
        <Card className="border border-slate-200/80">
          <div className="flex justify-center py-10">
            <Spin tip="Đang truy vết..." >
              <div className="h-8 w-40" />
            </Spin>
          </div>
        </Card>
      )}

      {current && s && (
        <Spin spinning={loading}>
          <div className="flex flex-col gap-4">
            <Card size="small" className="border border-slate-200/80" title="Tóm tắt lô">
              <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }} className="mb-3">
                <Descriptions.Item label="Số lô">
                  <span className="font-mono font-semibold">{current.lotNo}</span>
                </Descriptions.Item>
                <Descriptions.Item label="Sản phẩm">{productLabel(current.skuCode, current.productName, current.productId)}</Descriptions.Item>
                <Descriptions.Item label="Ngày sản xuất">{formatDate(current.mfgDate)}</Descriptions.Item>
                <Descriptions.Item label="Hạn dùng">{formatDate(current.expDate)}</Descriptions.Item>
                <Descriptions.Item label="Đơn vị tính">{current.uom ?? EMPTY_CELL}</Descriptions.Item>
                <Descriptions.Item label="Sổ kho bắt đầu">
                  {current.ledgerStartedAt ? formatInstant(current.ledgerStartedAt) : 'Có từ đầu'}
                </Descriptions.Item>
                <Descriptions.Item label="Lập lúc">{formatInstant(current.generatedAt)}</Descriptions.Item>
              </Descriptions>
              <Row gutter={[16, 16]}>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Nhập theo phiếu nhập" value={formatQuantity(s.totalReceived)} />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Số dư mở sổ" value={formatQuantity(s.openingBalance)} />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Điều chỉnh tăng" value={formatQuantity(s.totalAdjustedIn)} />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Đã xuất bán" value={formatQuantity(s.totalSalesDispatched)} />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Đã điều chuyển đi" value={formatQuantity(s.totalTransferredOut)} />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Xuất khác, điều chỉnh giảm" value={formatQuantity(s.totalOtherOut)} />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Tồn theo sổ kho" value={formatQuantity(s.ledgerBalance)} />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Tồn thực tế" value={formatQuantity(s.currentOnHand)} />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Statistic title="Đang giữ chỗ" value={formatQuantity(s.currentReserved)} />
                </Col>
              </Row>
              {mismatch && (
                <Alert
                  className="mt-3"
                  type="warning"
                  showIcon
                  message="Tồn theo sổ kho khác tồn thực tế của lô"
                  description="Chênh lệch có thể do biến động chưa ghi sổ (ví dụ hàng nhận điều chuyển không giữ số lô) hoặc dữ liệu trước khi có sổ kho."
                />
              )}
            </Card>

            <Card size="small" className="border border-slate-200/80" title="Cây truy vết">
              {current.tree ? <LotTraceTree tree={current.tree} uom={current.uom} /> : <Empty description="Không có dữ liệu cây truy vết" />}
              {(current.notes ?? []).length > 0 && (
                <Alert
                  className="mt-3"
                  type="info"
                  showIcon
                  message="Giới hạn của dữ liệu truy vết"
                  description={
                    <ul className="list-disc pl-5 m-0">
                      {current.notes.map((note) => (
                        <li key={note}>{note}</li>
                      ))}
                    </ul>
                  }
                />
              )}
            </Card>

            <Card size="small" className="border border-slate-200/80" title="Nguồn gốc">
              <Table<LotTraceFlow>
                rowKey={(f) => `${f.movementType}-${f.documentType}-${f.documentId}-${f.warehouseId}`}
                size="small"
                columns={originColumns}
                dataSource={current.origins}
                pagination={false}
                scroll={{ x: 'max-content' }}
                locale={{ emptyText: 'Không có chứng từ nhập của lô trong phạm vi được xem' }}
              />
            </Card>

            <Card size="small" className="border border-slate-200/80" title="Đang ở đâu">
              <Table<LotTraceLocation>
                rowKey="stockItemId"
                size="small"
                columns={locationColumns}
                dataSource={current.currentLocations}
                pagination={false}
                scroll={{ x: 'max-content' }}
                locale={{ emptyText: 'Lô không còn tồn tại vị trí nào' }}
              />
            </Card>

            <Card size="small" className="border border-slate-200/80" title="Đã đi đâu">
              <Table<LotTraceFlow>
                rowKey={(f) => `${f.movementType}-${f.documentType}-${f.documentId}-${f.warehouseId}`}
                size="small"
                columns={outflowColumns}
                dataSource={current.outflows}
                pagination={false}
                scroll={{ x: 'max-content' }}
                locale={{ emptyText: 'Lô chưa được điều chuyển hay xuất đi' }}
              />
            </Card>

            <Card size="small" className="border border-slate-200/80" title="Diễn biến theo thời gian">
              <Table<LotTraceTimelineEvent>
                rowKey="movementId"
                size="small"
                columns={timelineColumns}
                dataSource={current.timeline}
                pagination={current.timeline.length > 50 ? { pageSize: 50, showSizeChanger: false } : false}
                scroll={{ x: 'max-content' }}
                locale={{ emptyText: 'Chưa có biến động nào của lô' }}
              />
            </Card>
          </div>
        </Spin>
      )}
    </div>
  )
}
