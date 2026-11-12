import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Descriptions, Progress, Result, Space, Spin, Table, Tabs, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import ForbiddenResult from '../../components/ForbiddenResult'
import { getApiErrorMessage, getErrorStatus } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { useCan } from '../auth/useCan'
import { formatLocalDate } from '../inventory/inventoryUtils'
import CountLinesTable from './components/CountLinesTable'
import StocktakeActionBar from './components/StocktakeActionBar'
import StocktakeStatusTag from './components/StocktakeStatusTag'
import { SCOPE_LABELS, isFrozenStatus, progressPercent, type StocktakeAbilities } from './stocktakeLogic'
import type { StocktakeLocation, StocktakeSession } from './types'
import { productLabel, useProductBriefs } from './useProductBriefs'
import { useStocktakeSession } from './useStocktakeSession'

const { Title, Text } = Typography

function personAt(by: string | null, at: string | null): string {
  if (!by) return '—'
  return at ? `${by}, ${formatDateTime(at)}` : by
}

/** Thông báo theo trạng thái đợt ở đầu trang. */
function StatusNotice({ session }: { session: StocktakeSession }) {
  switch (session.status) {
    case 'DRAFT':
      return (
        <Alert
          type="info"
          showIcon
          message="Đợt mới lập, chưa đóng băng vị trí. Bấm Bắt đầu kiểm kê khi sẵn sàng đếm."
        />
      )
    case 'COUNTING':
      return (
        <Alert
          type="warning"
          showIcon
          message="Đang đếm: các vị trí trong phạm vi đang đóng băng"
          description="Nhập kho, xuất kho chỉ định ô, xuất và nhận điều chuyển qua các vị trí này bị chặn cho tới khi đợt kết thúc."
        />
      )
    case 'COUNTED':
      return (
        <Alert
          type="warning"
          showIcon
          message="Đã kết thúc đếm, chênh lệch đã được tính, chờ Quản lý kho duyệt. Vị trí vẫn đóng băng."
        />
      )
    case 'PENDING_ACCOUNTING':
      return (
        <Alert
          type="warning"
          showIcon
          message={`Quản lý kho đã duyệt; phiếu điều chỉnh ${session.adjustmentCode ?? ''} chờ Kế toán duyệt. Vị trí vẫn đóng băng.`}
        />
      )
    case 'COMPLETED':
      return (
        <Alert
          type="success"
          showIcon
          message={
            session.adjustmentCode
              ? `Đã hoàn tất: tồn kho đã được điều chỉnh theo phiếu ${session.adjustmentCode}, vị trí đã mở đóng băng.`
              : 'Đã hoàn tất: không có chênh lệch, tồn kho giữ nguyên, vị trí đã mở đóng băng.'
          }
        />
      )
    case 'REJECTED':
      return (
        <Alert
          type="error"
          showIcon
          message={`Bị từ chối bởi ${session.closedBy ?? '—'}: tồn kho giữ nguyên, vị trí đã mở đóng băng.`}
          description={session.closeReason ? `Lý do: ${session.closeReason}` : undefined}
        />
      )
    case 'CANCELLED':
      return (
        <Alert
          type="info"
          showIcon
          message={`Đã hủy bởi ${session.closedBy ?? '—'}: tồn kho giữ nguyên, vị trí đã mở đóng băng.`}
          description={session.closeReason ? `Lý do: ${session.closeReason}` : undefined}
        />
      )
    default:
      return null
  }
}

/** Chi tiết đợt kiểm kê: thông tin, tiến độ, vị trí, dòng đếm và các thao tác theo trạng thái, vai trò. */
export default function StocktakeDetailPage() {
  const params = useParams()
  const id = Number(params.id)
  const validId = Number.isInteger(id) && id > 0 ? id : null
  const can = useCan()
  const canRead = can('stocktake.read')
  const [searchParams, setSearchParams] = useSearchParams()
  const { session, loading, error, reload, replace } = useStocktakeSession(canRead ? validId : null)
  const [linesToken, setLinesToken] = useState(0)

  const productIds = useMemo(() => session?.productIds ?? [], [session])
  const products = useProductBriefs(productIds)

  if (!canRead) {
    return <ForbiddenResult description="Kiểm kê dành cho Quản trị viên, Quản lý kho, Nhân viên kho và Kế toán." />
  }
  if (validId === null) {
    return <Result status="404" title="Không tìm thấy đợt kiểm kê" extra={<Link to="/stocktakes">Về danh sách</Link>} />
  }
  if (!session) {
    if (error) {
      const status = getErrorStatus(error)
      return (
        <Result
          status={status === 403 ? '403' : status === 404 ? '404' : 'error'}
          title={status === 404 ? 'Không tìm thấy đợt kiểm kê' : 'Không tải được đợt kiểm kê'}
          subTitle={getApiErrorMessage(error, 'Vui lòng thử lại sau.')}
          extra={
            <Space>
              <Link to="/stocktakes">Về danh sách</Link>
              <Button onClick={reload}>Thử lại</Button>
            </Space>
          }
        />
      )
    }
    return (
      <div className="flex justify-center py-16">
        <Spin tip="Đang tải đợt kiểm kê..." size="large">
          <div className="p-8" />
        </Spin>
      </div>
    )
  }

  const abilities: StocktakeAbilities = {
    manage: can('stocktake.manage', session.warehouseId),
    count: can('stocktake.count', session.warehouseId),
    variance: can('stocktake.variance', session.warehouseId),
    approveAdjustment: can('stocktake.approveAdjustment'),
  }

  const handleUpdated = (updated: StocktakeSession) => {
    replace(updated)
    setLinesToken((t) => t + 1)
  }

  const locations = session.locations ?? []
  const locationColumns = [
    {
      title: 'Mã vị trí',
      dataIndex: 'binLocationCode',
      key: 'code',
      render: (code: string) => <span className="font-mono">{code}</span>,
    },
    { title: 'Khu', dataIndex: 'zone', key: 'zone', render: (v: string | null) => v || '—' },
    { title: 'Kệ', dataIndex: 'rack', key: 'rack', render: (v: string | null) => v || '—' },
    { title: 'Ô', dataIndex: 'bin', key: 'bin', render: (v: string | null) => v || '—' },
    {
      title: 'Trạng thái',
      key: 'frozen',
      render: (_: unknown, l: StocktakeLocation) =>
        l.frozen ? <Tag color="red">Khóa kiểm kê</Tag> : <Tag>Không khóa</Tag>,
    },
  ]

  const activeTab = searchParams.get('tab') ?? 'lines'
  const tabs = [
    {
      key: 'lines',
      label: 'Dòng đếm',
      children:
        session.status === 'DRAFT' ? (
          <Alert type="info" showIcon message="Dòng cần đếm được tạo khi bắt đầu kiểm kê (chụp tồn hệ thống)." />
        ) : (
          <CountLinesTable sessionId={session.id} reloadToken={linesToken} />
        ),
    },
    {
      key: 'locations',
      label: `Vị trí (${locations.length})`,
      children:
        locations.length === 0 ? (
          <Alert
            type="info"
            showIcon
            message={
              session.status === 'DRAFT'
                ? 'Với phạm vi toàn kho hoặc theo khu, danh sách vị trí được chốt khi bắt đầu kiểm kê.'
                : 'Đợt không có vị trí nào.'
            }
          />
        ) : (
          <Table<StocktakeLocation>
            rowKey="binLocationId"
            size="small"
            columns={locationColumns}
            dataSource={locations}
            pagination={{ pageSize: 50, showSizeChanger: false, hideOnSinglePage: true }}
            scroll={{ x: 560 }}
          />
        ),
    },
  ]

  return (
    <div className="flex flex-col gap-4 w-full">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex flex-col gap-1">
          <Link to="/stocktakes" className="text-sm">
            <ArrowLeftOutlined /> Danh sách đợt kiểm kê
          </Link>
          <div className="flex flex-wrap items-center gap-2">
            <Title level={4} className="!mb-0 font-mono">
              {session.code}
            </Title>
            <StocktakeStatusTag status={session.status} />
            {isFrozenStatus(session.status) && <Tag color="red">Khóa kiểm kê</Tag>}
          </div>
          <Text type="secondary" className="text-xs">
            {session.warehouseName ?? `Kho ID ${session.warehouseId}`} · {SCOPE_LABELS[session.scopeType]}
          </Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => {
            reload()
            setLinesToken((t) => t + 1)
          }}>
            Làm mới
          </Button>
          <StocktakeActionBar session={session} abilities={abilities} onUpdated={handleUpdated} onReload={reload} />
        </Space>
      </div>

      <StatusNotice session={session} />
      {error ? <Alert type="error" showIcon message={getApiErrorMessage(error, 'Không tải lại được đợt kiểm kê')} /> : null}

      <Card variant="borderless" className="shadow-xs">
        <div className="flex flex-col gap-4 md:flex-row">
          <div className="md:w-64 flex flex-col gap-1">
            <Text type="secondary">Tiến độ đếm</Text>
            <span className="text-2xl font-semibold">
              {session.countedLines}/{session.totalLines}
              <span className="text-sm font-normal text-slate-500"> dòng</span>
            </span>
            <Progress percent={progressPercent(session.countedLines, session.totalLines)} />
            <Text type="secondary" className="text-xs">
              {session.totalLocations} vị trí · còn {session.uncountedLines} dòng chưa đếm
            </Text>
          </div>
          <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} className="flex-1">
            <Descriptions.Item label="Kho">
              {session.warehouseName ?? '—'} {session.warehouseCode ? `(${session.warehouseCode})` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="Phạm vi">
              {SCOPE_LABELS[session.scopeType]}
              {session.scopeType === 'ZONES' && session.zones?.length ? `: ${session.zones.join(', ')}` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="Ngày dự kiến">{formatLocalDate(session.plannedDate)}</Descriptions.Item>
            <Descriptions.Item label="Hàng hóa">
              {productIds.length === 0
                ? 'Mọi hàng hóa'
                : productIds.length <= 5
                  ? productIds.map((pid) => productLabel(products, pid)).join('; ')
                  : `Giới hạn ${productIds.length} hàng hóa`}
            </Descriptions.Item>
            <Descriptions.Item label="Người lập">{personAt(session.createdBy, session.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="Bắt đầu">{personAt(session.startedBy, session.startedAt)}</Descriptions.Item>
            <Descriptions.Item label="Kết thúc đếm">{personAt(session.finishedBy, session.finishedAt)}</Descriptions.Item>
            <Descriptions.Item label="Quản lý kho duyệt">
              {personAt(session.managerApprovedBy, session.managerApprovedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="Duyệt hoàn tất">{personAt(session.approvedBy, session.approvedAt)}</Descriptions.Item>
            {session.closedBy && (
              <Descriptions.Item label={session.status === 'REJECTED' ? 'Từ chối' : 'Đóng đợt'}>
                {personAt(session.closedBy, session.closedAt)}
              </Descriptions.Item>
            )}
            {session.adjustmentCode && (
              <Descriptions.Item label="Phiếu điều chỉnh">
                <span className="font-mono">{session.adjustmentCode}</span>
              </Descriptions.Item>
            )}
            {session.notes && <Descriptions.Item label="Ghi chú">{session.notes}</Descriptions.Item>}
          </Descriptions>
        </div>
      </Card>

      <Card variant="borderless" className="shadow-xs">
        <Tabs
          activeKey={tabs.some((t) => t.key === activeTab) ? activeTab : 'lines'}
          onChange={(key) => setSearchParams(key === 'lines' ? {} : { tab: key }, { replace: true })}
          items={tabs}
        />
      </Card>
    </div>
  )
}
