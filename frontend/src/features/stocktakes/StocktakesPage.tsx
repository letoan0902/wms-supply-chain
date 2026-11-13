import { ClearOutlined, PlusOutlined, ReloadOutlined, ScanOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Col, DatePicker, Input, Progress, Row, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import ForbiddenResult from '../../components/ForbiddenResult'
import { getApiErrorMessage } from '../../services/api'
import { useCan } from '../auth/useCan'
import { formatInstantDate, formatLocalDate } from '../inventory/inventoryUtils'
import { useReportQuery } from '../reports/useReportQuery'
import { fetchWarehouses } from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import CreateStocktakeModal from './components/CreateStocktakeModal'
import StocktakeStatusTag from './components/StocktakeStatusTag'
import { fetchStocktakes } from './stocktakeApi'
import { SCOPE_LABELS, STATUS_LABELS, STATUS_ORDER, progressPercent } from './stocktakeLogic'
import type { StocktakeListQuery, StocktakeSession, StocktakeStatus } from './types'

const { Title, Text } = Typography
const { RangePicker } = DatePicker

/** Danh sách đợt kiểm kê: lọc theo kho, trạng thái, khoảng ngày dự kiến, mã đợt; phân trang trên máy chủ. */
export default function StocktakesPage() {
  const navigate = useNavigate()
  const can = useCan()
  const canRead = can('stocktake.read')
  const canCreateAny = can('stocktake.manage')

  const [warehouseState, setWarehouseState] = useState<{ items: Warehouse[]; error: unknown; loaded: boolean }>({
    items: [],
    error: null,
    loaded: false,
  })
  const [warehouseId, setWarehouseId] = useState<number | undefined>(undefined)
  const [status, setStatus] = useState<StocktakeStatus | undefined>(undefined)
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [reloadToken, setReloadToken] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)

  useEffect(() => {
    if (!canRead) return
    let ignore = false
    fetchWarehouses()
      .then((items) => {
        if (!ignore) setWarehouseState({ items: items ?? [], error: null, loaded: true })
      })
      .catch((error: unknown) => {
        if (!ignore) setWarehouseState({ items: [], error, loaded: true })
      })
    return () => {
      ignore = true
    }
  }, [canRead])

  // Kho được chọn trong bộ lọc: Quản trị viên, Kế toán thấy mọi kho; vai trò khác chỉ kho được phân công
  const filterWarehouses = useMemo(
    () =>
      can.global('stocktake.read')
        ? warehouseState.items
        : warehouseState.items.filter((w) => can.warehouseIds.includes(w.id)),
    [can, warehouseState.items],
  )
  // Kho được lập đợt kiểm kê: đang hoạt động và người dùng quản lý được
  const creatableWarehouses = useMemo(
    () => warehouseState.items.filter((w) => w.active && can('stocktake.manage', w.id)),
    [can, warehouseState.items],
  )

  const query = useMemo<StocktakeListQuery | null>(
    () =>
      canRead
        ? {
            warehouseId,
            status,
            fromDate: range?.[0]?.format('YYYY-MM-DD'),
            toDate: range?.[1]?.format('YYYY-MM-DD'),
            keyword: keyword.trim() || undefined,
            page: page - 1,
            size: pageSize,
          }
        : null,
    [canRead, warehouseId, status, range, keyword, page, pageSize],
  )
  const { data, loading, error } = useReportQuery(fetchStocktakes, query, reloadToken)

  const warehouseLabel = (s: StocktakeSession) =>
    s.warehouseName ? `${s.warehouseName}${s.warehouseCode ? ` (${s.warehouseCode})` : ''}` : `Kho ID ${s.warehouseId}`

  const columns: ColumnsType<StocktakeSession> = [
    {
      title: 'Mã đợt',
      dataIndex: 'code',
      key: 'code',
      fixed: 'left',
      width: 150,
      render: (code: string, s) => (
        <Link to={`/stocktakes/${s.id}`} className="font-mono font-semibold">
          {code}
        </Link>
      ),
    },
    { title: 'Kho', key: 'warehouse', width: 180, render: (_, s) => warehouseLabel(s) },
    {
      title: 'Phạm vi',
      key: 'scope',
      width: 170,
      render: (_, s) => (
        <div className="flex flex-col">
          <span>{SCOPE_LABELS[s.scopeType] ?? s.scopeType}</span>
          {s.scopeType === 'ZONES' && s.zones && s.zones.length > 0 && (
            <span className="text-xs text-slate-500">Khu: {s.zones.join(', ')}</span>
          )}
          {s.productIds && s.productIds.length > 0 && (
            <span className="text-xs text-slate-500">Giới hạn {s.productIds.length} hàng hóa</span>
          )}
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 170,
      render: (value: StocktakeStatus) => <StocktakeStatusTag status={value} />,
    },
    {
      title: 'Ngày dự kiến',
      dataIndex: 'plannedDate',
      key: 'plannedDate',
      width: 120,
      render: (value: string | null) => formatLocalDate(value),
    },
    {
      title: 'Tiến độ đếm',
      key: 'progress',
      width: 180,
      render: (_, s) =>
        s.status === 'DRAFT' ? (
          <Text type="secondary" className="text-xs">
            Chưa bắt đầu
          </Text>
        ) : (
          <div className="flex flex-col">
            <span className="text-xs">
              {s.countedLines}/{s.totalLines} dòng
            </span>
            <Progress percent={progressPercent(s.countedLines, s.totalLines)} size="small" showInfo={false} />
          </div>
        ),
    },
    {
      title: 'Người lập',
      key: 'createdBy',
      width: 150,
      render: (_, s) => (
        <div className="flex flex-col">
          <span>{s.createdBy || '—'}</span>
          <span className="text-xs text-slate-500">{formatInstantDate(s.createdAt)}</span>
        </div>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 170,
      render: (_, s) => (
        <Space size="small" wrap>
          <Button size="small" onClick={() => navigate(`/stocktakes/${s.id}`)}>
            Chi tiết
          </Button>
          {s.status === 'COUNTING' && can('stocktake.count', s.warehouseId) && (
            <Button
              size="small"
              type="primary"
              icon={<ScanOutlined />}
              onClick={() => navigate(`/stocktakes/${s.id}/count`)}
              data-write-action
            >
              Đếm
            </Button>
          )}
        </Space>
      ),
    },
  ]

  if (!canRead) {
    return (
      <ForbiddenResult description="Kiểm kê dành cho Quản trị viên, Quản lý kho, Nhân viên kho và Kế toán (Quản lý kho, Nhân viên kho cần được phân công kho)." />
    )
  }

  const resetFilters = () => {
    setWarehouseId(undefined)
    setStatus(undefined)
    setRange(null)
    setKeywordInput('')
    setKeyword('')
    setPage(1)
  }

  return (
    <div className="flex flex-col gap-4 w-full">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div>
          <Title level={4} className="!mb-0">
            Kiểm kê kho
          </Title>
          <Text type="secondary" className="text-xs">
            Lập đợt kiểm kê, đóng băng vị trí, đếm mù bằng máy quét, đối soát chênh lệch và duyệt phiếu điều chỉnh.
          </Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => setReloadToken((t) => t + 1)} loading={loading}>
            Làm mới
          </Button>
          {canCreateAny && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              Tạo đợt kiểm kê
            </Button>
          )}
        </Space>
      </div>

      {warehouseState.error ? (
        <Alert
          type="warning"
          showIcon
          message={getApiErrorMessage(warehouseState.error, 'Không tải được danh sách kho cho bộ lọc')}
        />
      ) : null}

      <Card variant="borderless" className="shadow-xs">
        <Row gutter={[12, 12]}>
          <Col xs={24} md={6}>
            <Input
              allowClear
              prefix={<SearchOutlined className="text-gray-400" />}
              placeholder="Mã đợt (ví dụ ST-202611)"
              value={keywordInput}
              onChange={(e) => {
                setKeywordInput(e.target.value)
                if (!e.target.value) {
                  setKeyword('')
                  setPage(1)
                }
              }}
              onPressEnter={() => {
                setKeyword(keywordInput)
                setPage(1)
              }}
            />
          </Col>
          <Col xs={24} sm={12} md={5}>
            <Select
              allowClear
              className="w-full"
              placeholder="Kho"
              value={warehouseId}
              onChange={(v) => {
                setWarehouseId(v)
                setPage(1)
              }}
              loading={!warehouseState.loaded}
              showSearch
              optionFilterProp="label"
              options={filterWarehouses.map((w) => ({ value: w.id, label: `${w.name} (${w.code})` }))}
            />
          </Col>
          <Col xs={24} sm={12} md={4}>
            <Select
              allowClear
              className="w-full"
              placeholder="Trạng thái"
              value={status}
              onChange={(v) => {
                setStatus(v)
                setPage(1)
              }}
              options={STATUS_ORDER.map((s) => ({ value: s, label: STATUS_LABELS[s] }))}
            />
          </Col>
          <Col xs={24} sm={16} md={6}>
            <RangePicker
              className="w-full"
              format="DD/MM/YYYY"
              placeholder={['Dự kiến từ ngày', 'Đến ngày']}
              value={range}
              onChange={(value) => {
                setRange(value)
                setPage(1)
              }}
            />
          </Col>
          <Col xs={24} sm={8} md={3}>
            <Button block icon={<ClearOutlined />} onClick={resetFilters}>
              Xóa lọc
            </Button>
          </Col>
        </Row>
      </Card>

      {error ? (
        <Alert type="error" showIcon message={getApiErrorMessage(error, 'Không tải được danh sách đợt kiểm kê')} />
      ) : null}

      <Table<StocktakeSession>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={data?.content ?? []}
        loading={loading}
        scroll={{ x: 1250 }}
        locale={{ emptyText: 'Chưa có đợt kiểm kê nào phù hợp bộ lọc' }}
        pagination={{
          current: page,
          pageSize,
          total: data?.totalElements ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          showTotal: (total) => `${total} đợt kiểm kê`,
          onChange: (nextPage, nextSize) => {
            setPage(nextSize !== pageSize ? 1 : nextPage)
            setPageSize(nextSize)
          },
        }}
      />

      {createOpen && (
        <CreateStocktakeModal
          open={createOpen}
          warehouses={creatableWarehouses}
          onClose={() => setCreateOpen(false)}
          onCreated={(session) => {
            setCreateOpen(false)
            navigate(`/stocktakes/${session.id}`)
          }}
        />
      )}
    </div>
  )
}
