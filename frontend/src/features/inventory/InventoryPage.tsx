import { ClearOutlined, RadarChartOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import {
  Alert,
  App,
  Badge,
  Button,
  Card,
  Col,
  Input,
  Result,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { BadgeProps } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../../services/api'
import { getStoredUser } from '../../utils/storage'
import { useCan } from '../auth/useCan'
import { fetchCategories } from '../products/productApi'
import type { Category } from '../products/types'
import { useRealtimeStore } from '../realtime/realtimeStore'
import type { ConnectionStatus } from '../realtime/types'
import { fetchWarehouses } from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import AlertListView from './components/AlertListView'
import AlertSummaryBar from './components/AlertSummaryBar'
import LotStockView from './components/LotStockView'
import ProductPicker from './components/ProductPicker'
import ProductStockView from './components/ProductStockView'
import './inventory.css'
import { fetchAlertSummary, scanAlerts } from './inventoryApi'
import { formatClock, KIND_LABELS, KIND_ORDER, SEVERITY_LABELS, SEVERITY_ORDER } from './inventoryUtils'
import type { LotExpiryFilter, StockAlertKind, StockAlertScanResult, StockAlertSeverity, StockAlertSummary } from './types'
import { useDebouncedValue } from './useDebouncedValue'
import { useLiveRefresh } from './useLiveRefresh'
import { toViewError } from './viewError'
import type { ViewError } from './viewError'

type StockView = 'product' | 'lot'
type TriState = 'all' | 'true' | 'false'

const SEARCH_DEBOUNCE_MS = 400
/** Thời gian hiện nhãn "Vừa cập nhật" sau một lần tự làm mới. */
const LIVE_FLASH_MS = 4000

const CONNECTION_STATUS: Record<ConnectionStatus, { badge: BadgeProps['status']; label: string }> = {
  idle: { badge: 'default', label: 'Chưa kết nối cập nhật thời gian thực' },
  connecting: { badge: 'processing', label: 'Đang kết nối cập nhật thời gian thực...' },
  connected: { badge: 'success', label: 'Đang nhận cập nhật thời gian thực' },
  reconnecting: { badge: 'warning', label: 'Mất kết nối thời gian thực, đang thử lại...' },
  disconnected: { badge: 'error', label: 'Đã ngắt kết nối thời gian thực; bấm "Làm mới" để xem số liệu mới nhất' },
}

const SCAN_STATUS: Record<StockAlertScanResult['results'][number]['status'], { label: string; color: string }> = {
  PUBLISHED: { label: 'Đã gửi', color: 'green' },
  ALREADY_SENT: { label: 'Đã gửi hôm nay', color: 'default' },
  NOTHING_FOUND: { label: 'Không có cảnh báo', color: 'blue' },
  UNAVAILABLE: { label: 'Chưa tính được', color: 'orange' },
  FAILED: { label: 'Lỗi, thử lại sau', color: 'red' },
}

const EXPIRY_OPTIONS: { value: LotExpiryFilter; label: string }[] = [
  { value: 'EXPIRED', label: 'Đã hết hạn' },
  { value: 'EXPIRING_SOON', label: 'Cận hạn' },
  { value: 'GOOD', label: 'Chưa hết hạn' },
]

function parseKind(value: string | null): StockAlertKind | undefined {
  return value && (KIND_ORDER as readonly string[]).includes(value) ? (value as StockAlertKind) : undefined
}

function parseSeverity(value: string | null): StockAlertSeverity | undefined {
  return value && (SEVERITY_ORDER as readonly string[]).includes(value) ? (value as StockAlertSeverity) : undefined
}

function parseId(value: string | null): number | undefined {
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : undefined
}

function triToBoolean(value: TriState): boolean | undefined {
  return value === 'all' ? undefined : value === 'true'
}

/**
 * Màn hình Tồn kho: bảng tồn theo hàng hóa hoặc theo lô và vị trí, bộ lọc, thẻ màu cảnh báo, thanh tóm tắt cảnh báo
 * và tự làm mới khi có tin thời gian thực của kho đang xem. Có thể mở thẳng với `?kind=...&severity=...&warehouseId=...`
 * (ví dụ từ thông báo cảnh báo) để chỉ hiện các dòng có cảnh báo đó.
 */
export default function InventoryPage() {
  const { message, modal } = App.useApp()
  const [searchParams] = useSearchParams()
  const [currentUser] = useState(() => getStoredUser())
  const roles = currentUser?.roles ?? []
  const isAdmin = roles.includes('ADMIN')
  // Chạy quét cảnh báo ngay (POST /api/inventory/alerts/scan): chỉ ADMIN
  const canScan = useCan()('alert.scan')
  /** ADMIN, ACCOUNTANT xem mọi kho của doanh nghiệp; vai trò khác chỉ xem kho được phân công */
  const isGlobalScope = isAdmin || roles.includes('ACCOUNTANT')

  const [initialKind] = useState(() => parseKind(searchParams.get('kind')))
  const [initialSeverity] = useState(() => parseSeverity(searchParams.get('severity')))

  // Kho
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [warehousesLoading, setWarehousesLoading] = useState(true)
  const [warehouseError, setWarehouseError] = useState<ViewError | null>(null)
  const [warehouseId, setWarehouseId] = useState<number | undefined>(() => parseId(searchParams.get('warehouseId')))

  // Chế độ xem và bộ lọc cảnh báo
  const [view, setView] = useState<StockView>('product')
  const [alertOnly, setAlertOnly] = useState(Boolean(initialKind || initialSeverity))
  const [kind, setKind] = useState<StockAlertKind | undefined>(initialKind)
  const [severity, setSeverity] = useState<StockAlertSeverity | undefined>(initialSeverity)

  // Bộ lọc theo hàng hóa (và từ khóa của danh sách cảnh báo)
  const [keywordInput, setKeywordInput] = useState('')
  const keyword = useDebouncedValue(keywordInput.trim(), SEARCH_DEBOUNCE_MS) || undefined
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined)
  const [categories, setCategories] = useState<Category[]>([])

  // Bộ lọc theo lô
  const [productId, setProductId] = useState<number | undefined>(undefined)
  const [lotNoInput, setLotNoInput] = useState('')
  const lotNo = useDebouncedValue(lotNoInput.trim(), SEARCH_DEBOUNCE_MS) || undefined
  const [hasStock, setHasStock] = useState<TriState>('true')
  const [locked, setLocked] = useState<TriState>('all')
  const [expiryStatus, setExpiryStatus] = useState<LotExpiryFilter | undefined>(undefined)

  // Làm mới
  const [refreshKey, setRefreshKey] = useState(0)
  const [lastRefreshAt, setLastRefreshAt] = useState<Date | null>(null)
  const [liveFlash, setLiveFlash] = useState(false)
  const flashTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const connectionStatus = useRealtimeStore((state) => state.status)

  // Tóm tắt cảnh báo
  const [summary, setSummary] = useState<StockAlertSummary | null>(null)
  const [summaryLoading, setSummaryLoading] = useState(false)
  const [summaryError, setSummaryError] = useState<ViewError | null>(null)

  const [scanning, setScanning] = useState(false)

  // Danh sách kho trong phạm vi của người dùng (backend tự giới hạn theo kho được phân công)
  useEffect(() => {
    let ignore = false
    fetchWarehouses()
      .then((list) => {
        if (ignore) return
        setWarehouses(list)
        if (!isGlobalScope && list.length === 0) {
          setWarehouseError({ status: 403, message: 'Tài khoản chưa được phân công phụ trách kho nào.' })
          return
        }
        // Vai trò bị giới hạn theo kho luôn xem đúng một kho được phân công (API lô không tự lọc theo phạm vi)
        setWarehouseId((current) => {
          if (current !== undefined && list.some((w) => w.id === current)) return current
          return isGlobalScope ? undefined : list[0]?.id
        })
      })
      .catch((err: unknown) => {
        if (!ignore) setWarehouseError(toViewError(err, 'Không tải được danh sách kho'))
      })
      .finally(() => {
        if (!ignore) setWarehousesLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [isGlobalScope])

  useEffect(() => {
    let ignore = false
    fetchCategories().then((list) => {
      if (!ignore) setCategories(list)
    })
    return () => {
      ignore = true
    }
  }, [])

  const ready = !warehousesLoading && !warehouseError && (isGlobalScope || warehouseId !== undefined)

  // Tóm tắt cảnh báo của kho đang xem
  useEffect(() => {
    if (!ready) return
    let ignore = false
    const load = async () => {
      setSummaryLoading(true)
      try {
        const data = await fetchAlertSummary(warehouseId)
        if (ignore) return
        setSummary(data)
        setSummaryError(null)
      } catch (err) {
        if (!ignore) setSummaryError(toViewError(err, 'Không tải được tóm tắt cảnh báo'))
      } finally {
        if (!ignore) setSummaryLoading(false)
      }
    }
    void load()
    return () => {
      ignore = true
    }
  }, [ready, warehouseId, refreshKey])

  useEffect(
    () => () => {
      if (flashTimerRef.current) clearTimeout(flashTimerRef.current)
    },
    [],
  )

  const handleLiveRefresh = useCallback(() => {
    setRefreshKey((key) => key + 1)
    setLiveFlash(true)
    if (flashTimerRef.current) clearTimeout(flashTimerRef.current)
    flashTimerRef.current = setTimeout(() => setLiveFlash(false), LIVE_FLASH_MS)
  }, [])

  const visibleWarehouseIds = useMemo(() => (warehouseId !== undefined ? [warehouseId] : null), [warehouseId])
  useLiveRefresh({ visibleWarehouseIds, onRefresh: handleLiveRefresh, enabled: ready })

  const handleLoaded = useCallback(() => setLastRefreshAt(new Date()), [])

  const handleManualRefresh = () => setRefreshKey((key) => key + 1)

  const selectKind = (next: StockAlertKind | undefined) => {
    setKind(next)
    if (next) setAlertOnly(true)
  }

  const selectSeverity = (next: StockAlertSeverity | undefined) => {
    setSeverity(next)
    if (next) setAlertOnly(true)
  }

  const toggleAlertOnly = (checked: boolean) => {
    setAlertOnly(checked)
    if (!checked) {
      setKind(undefined)
      setSeverity(undefined)
    }
  }

  const resetFilters = () => {
    setKeywordInput('')
    setCategoryId(undefined)
    setProductId(undefined)
    setLotNoInput('')
    setHasStock('true')
    setLocked('all')
    setExpiryStatus(undefined)
    toggleAlertOnly(false)
  }

  const handleScan = () => {
    modal.confirm({
      title: 'Quét cảnh báo tồn kho ngay?',
      content:
        'Hệ thống chạy ngay tác vụ quét hằng ngày cho toàn doanh nghiệp: phát thông báo và gửi email cảnh báo cho quản lý kho. '
        + 'Mỗi loại cảnh báo chỉ được gửi một lần mỗi ngày, kể cả khi tác vụ 00:00 đã chạy.',
      okText: 'Quét ngay',
      cancelText: 'Hủy',
      onOk: async () => {
        setScanning(true)
        try {
          const result = await scanAlerts()
          modal.info({
            title: `Kết quả quét cảnh báo ngày ${result.alertDate.split('-').reverse().join('/')}`,
            width: 600,
            content: (
              <ul className="m-0 mt-2 flex list-none flex-col gap-2 p-0">
                {result.results.map((item) => (
                  <li key={item.kind}>
                    <Space size={6} wrap>
                      <Typography.Text strong>{KIND_LABELS[item.kind]}</Typography.Text>
                      <Tag color={SCAN_STATUS[item.status]?.color}>{SCAN_STATUS[item.status]?.label ?? item.status}</Tag>
                    </Space>
                    <div className="text-sm">{item.message}</div>
                  </li>
                ))}
              </ul>
            ),
          })
          setRefreshKey((key) => key + 1)
        } catch (err) {
          message.error(getApiErrorMessage(err, 'Không quét được cảnh báo tồn kho'))
        } finally {
          setScanning(false)
        }
      },
    })
  }

  if (warehousesLoading) {
    return (
      <Card>
        <div className="flex flex-col items-center gap-3 py-16">
          <Spin />
          <Typography.Text type="secondary">Đang tải danh sách kho...</Typography.Text>
        </div>
      </Card>
    )
  }

  if (warehouseError) {
    return (
      <Card>
        <Result
          status={warehouseError.status === 403 ? '403' : 'error'}
          title={warehouseError.status === 403 ? 'Không có quyền xem tồn kho' : 'Không tải được danh sách kho'}
          subTitle={warehouseError.message}
        />
      </Card>
    )
  }

  const status = CONNECTION_STATUS[connectionStatus]
  const showWarehouseColumn = warehouseId === undefined
  const warehouseOptions = warehouses.map((w) => ({ value: w.id, label: `${w.code} - ${w.name}${w.active ? '' : ' (ngừng hoạt động)'}` }))

  const productFilterKey = `${warehouseId}|${keyword}|${categoryId}`
  const lotFilterKey = `${warehouseId}|${productId}|${lotNo}|${hasStock}|${locked}|${expiryStatus}`
  const alertFilterKey = `${warehouseId}|${kind}|${severity}|${keyword}`

  return (
    <div className="flex flex-col gap-4">
      <Card
        title="Tồn kho"
        extra={
          <Space wrap size={12}>
            <Tooltip title={status.label}>
              <Badge status={status.badge} text={<span className="text-sm">{status.label}</span>} />
            </Tooltip>
            {lastRefreshAt ? (
              <Typography.Text type="secondary" className="text-sm">
                Cập nhật lúc {formatClock(lastRefreshAt)}
              </Typography.Text>
            ) : null}
            {liveFlash ? <Tag color="green">Vừa cập nhật</Tag> : null}
            <Button icon={<ReloadOutlined />} onClick={handleManualRefresh}>
              Làm mới
            </Button>
            {canScan ? (
              <Button
                data-write-action
                icon={<RadarChartOutlined />}
                loading={scanning}
                disabled={scanning}
                onClick={handleScan}
              >
                Quét cảnh báo ngay
              </Button>
            ) : null}
          </Space>
        }
      >
        <div className="flex flex-col gap-4">
          <AlertSummaryBar
            summary={summary}
            loading={summaryLoading}
            error={summaryError}
            activeKind={alertOnly ? kind : undefined}
            activeSeverity={alertOnly ? severity : undefined}
            onSelectKind={selectKind}
            onSelectSeverity={selectSeverity}
          />

          <Row gutter={[12, 12]} align="middle">
            <Col xs={24} md={12} lg={6}>
              <Select<number>
                value={warehouseId}
                onChange={(value) => setWarehouseId(value ?? undefined)}
                allowClear={isGlobalScope}
                placeholder={isGlobalScope ? 'Tất cả kho' : 'Chọn kho'}
                options={warehouseOptions}
                style={{ width: '100%' }}
              />
            </Col>
            {!alertOnly ? (
              <Col xs={24} md={12} lg={6}>
                <Segmented<StockView>
                  block
                  value={view}
                  onChange={(value) => setView(value)}
                  options={[
                    { value: 'product', label: 'Theo hàng hóa' },
                    { value: 'lot', label: 'Theo lô và vị trí' },
                  ]}
                />
              </Col>
            ) : null}
            <Col xs={24} md={12} lg={6}>
              <Space>
                <Switch checked={alertOnly} onChange={toggleAlertOnly} />
                <span>Chỉ hiện dòng có cảnh báo</span>
              </Space>
            </Col>
            <Col xs={24} md={12} lg={6} className="text-right">
              <Button icon={<ClearOutlined />} onClick={resetFilters}>
                Xóa bộ lọc
              </Button>
            </Col>
          </Row>

          <Row gutter={[12, 12]}>
            {alertOnly || view === 'product' ? (
              <Col xs={24} md={12} lg={6}>
                <Input
                  allowClear
                  prefix={<SearchOutlined />}
                  value={keywordInput}
                  onChange={(event) => setKeywordInput(event.target.value)}
                  placeholder={alertOnly ? 'SKU, tên hàng, số lô, mã ô kệ' : 'SKU hoặc tên hàng hóa'}
                />
              </Col>
            ) : null}
            {alertOnly ? (
              <>
                <Col xs={24} md={12} lg={6}>
                  <Select<StockAlertKind>
                    allowClear
                    value={kind}
                    onChange={(value) => setKind(value ?? undefined)}
                    placeholder="Loại cảnh báo"
                    options={KIND_ORDER.map((k) => ({ value: k, label: KIND_LABELS[k] }))}
                    style={{ width: '100%' }}
                  />
                </Col>
                <Col xs={24} md={12} lg={6}>
                  <Select<StockAlertSeverity>
                    allowClear
                    value={severity}
                    onChange={(value) => setSeverity(value ?? undefined)}
                    placeholder="Mức độ"
                    options={SEVERITY_ORDER.map((s) => ({ value: s, label: SEVERITY_LABELS[s] }))}
                    style={{ width: '100%' }}
                  />
                </Col>
              </>
            ) : view === 'product' ? (
              <Col xs={24} md={12} lg={6}>
                <Select<number>
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  value={categoryId}
                  onChange={(value) => setCategoryId(value ?? undefined)}
                  placeholder="Danh mục hàng hóa"
                  options={categories.map((c) => ({ value: c.id, label: c.name }))}
                  style={{ width: '100%' }}
                />
              </Col>
            ) : (
              <>
                <Col xs={24} md={12} lg={6}>
                  <ProductPicker value={productId} onChange={setProductId} />
                </Col>
                <Col xs={24} md={12} lg={4}>
                  <Input
                    allowClear
                    value={lotNoInput}
                    onChange={(event) => setLotNoInput(event.target.value)}
                    placeholder="Số lô"
                  />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Select<TriState>
                    value={hasStock}
                    onChange={setHasStock}
                    options={[
                      { value: 'true', label: 'Còn hàng' },
                      { value: 'false', label: 'Đã hết hàng' },
                      { value: 'all', label: 'Còn và hết hàng' },
                    ]}
                    style={{ width: '100%' }}
                  />
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Select<TriState>
                    value={locked}
                    onChange={setLocked}
                    options={[
                      { value: 'all', label: 'Khóa và chưa khóa' },
                      { value: 'true', label: 'Đang khóa' },
                      { value: 'false', label: 'Không khóa' },
                    ]}
                    style={{ width: '100%' }}
                  />
                </Col>
                <Col xs={24} md={8} lg={6}>
                  <Select<LotExpiryFilter>
                    allowClear
                    value={expiryStatus}
                    onChange={(value) => setExpiryStatus(value ?? undefined)}
                    placeholder="Tình trạng hạn dùng"
                    options={EXPIRY_OPTIONS}
                    style={{ width: '100%' }}
                  />
                </Col>
              </>
            )}
          </Row>

          {alertOnly ? (
            <Alert
              type="info"
              showIcon
              message="Đang hiện danh sách cảnh báo: mỗi dòng là một cảnh báo (một lô hết hạn hoặc cận hạn, hoặc một mặt hàng tồn thấp, tồn lâu tại một kho). Tắt công tắc để xem toàn bộ tồn kho."
            />
          ) : null}
        </div>
      </Card>

      <Card>
        {alertOnly ? (
          <AlertListView
            key={alertFilterKey}
            warehouseId={warehouseId}
            kind={kind}
            severity={severity}
            keyword={keyword}
            showWarehouse={showWarehouseColumn}
            refreshKey={refreshKey}
            onLoaded={handleLoaded}
          />
        ) : view === 'product' ? (
          <ProductStockView
            key={productFilterKey}
            warehouseId={warehouseId}
            keyword={keyword}
            categoryId={categoryId}
            refreshKey={refreshKey}
            onLoaded={handleLoaded}
          />
        ) : (
          <LotStockView
            key={lotFilterKey}
            warehouseId={warehouseId}
            productId={productId}
            lotNo={lotNo}
            hasStock={triToBoolean(hasStock)}
            locked={triToBoolean(locked)}
            expiryStatus={expiryStatus}
            nearExpiryDays={expiryStatus === 'EXPIRING_SOON' ? summary?.nearExpiryDays : undefined}
            showWarehouse={showWarehouseColumn}
            refreshKey={refreshKey}
            onLoaded={handleLoaded}
          />
        )}
      </Card>
    </div>
  )
}
