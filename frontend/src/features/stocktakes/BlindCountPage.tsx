import {
  AimOutlined,
  ArrowLeftOutlined,
  CameraOutlined,
  EnvironmentOutlined,
  PlusSquareOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Empty, Input, Progress, Result, Select, Spin, Switch, Tag, Typography } from 'antd'
import type { InputRef } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useBlocker, useParams } from 'react-router-dom'
import ForbiddenResult from '../../components/ForbiddenResult'
import { getApiErrorMessage, getErrorStatus } from '../../services/api'
import { playErrorBeep, playSuccessBeep } from '../../utils/sound'
import { useCan } from '../auth/useCan'
import { fetchProductBySku, fetchProducts } from '../products/productApi'
import type { Product } from '../products/types'
import BarcodeCameraScanner from '../purchases/components/BarcodeCameraScanner'
import { useReportQuery } from '../reports/useReportQuery'
import CountLineCard from './components/CountLineCard'
import QueueFailures from './components/QueueFailures'
import SetQuantityModal from './components/SetQuantityModal'
import StocktakeStatusTag from './components/StocktakeStatusTag'
import UnexpectedItemModal from './components/UnexpectedItemModal'
import { SerialCountQueue, type QueueItem } from './countQueue'
import {
  LookupFailure,
  ScanRejection,
  classifyCountError,
  queueStateByLine,
  type CountOp,
} from './countOps'
import { fetchCountLines, postCount } from './stocktakeApi'
import {
  findLocationByCode,
  formatQty,
  lineKeyOf,
  matchProductByCode,
  parseQuantityInput,
  parseScannedCode,
  progressPercent,
  resolveLotlessScanLine,
} from './stocktakeLogic'
import type { CountLinesQuery, StocktakeCountLine, StocktakeLocation } from './types'
import { productLabel, useProductBriefs } from './useProductBriefs'
import { useLocationLines } from './useLocationLines'
import { useStocktakeSession } from './useStocktakeSession'

const { Text } = Typography

/** Số dòng nhật ký quét giữ lại trên màn hình. */
const LOG_SIZE = 6

interface ScanLogEntry {
  id: number
  ok: boolean
  text: string
}

interface CountResult {
  line: StocktakeCountLine
  label: string
}

/**
 * Màn hình đếm mù (dùng trên điện thoại hoặc máy quét cầm tay, rộng từ 375 px): chọn hoặc quét mã vị trí, rồi quét
 * mã vạch, SKU hoặc tem lô của hàng hóa; mỗi lần quét cộng số lượng đã đặt (mặc định 1). Màn hình KHÔNG hiện và
 * không tìm cách lấy tồn hệ thống (BR-12), chỉ hiện số đã đếm trong đợt này. Các lần quét được xếp hàng, gửi lần
 * lượt; lần lỗi nằm lại để thử lại, không bao giờ tự gửi lại.
 */
export default function BlindCountPage() {
  const params = useParams()
  const id = Number(params.id)
  const sessionId = Number.isInteger(id) && id > 0 ? id : null
  const { message, modal } = App.useApp()
  const can = useCan()
  const canRead = can('stocktake.read')
  const { session, error: sessionError, reload: reloadSession } = useStocktakeSession(canRead ? sessionId : null)

  const [locationId, setLocationId] = useState<number | null>(null)
  const [scanText, setScanText] = useState('')
  const [lotText, setLotText] = useState('')
  const [stepText, setStepText] = useState('1')
  const [onlyUncounted, setOnlyUncounted] = useState(false)
  const [hideKeyboard, setHideKeyboard] = useState(false)
  const [cameraOpen, setCameraOpen] = useState(false)
  const [setTarget, setSetTarget] = useState<StocktakeCountLine | null>(null)
  const [unexpectedOpen, setUnexpectedOpen] = useState(false)
  const [queueItems, setQueueItems] = useState<readonly QueueItem<CountOp>[]>([])
  const [log, setLog] = useState<ScanLogEntry[]>([])

  const scanInputRef = useRef<InputRef>(null)
  const queueRef = useRef<SerialCountQueue<CountOp, CountResult> | null>(null)
  const productCacheRef = useRef(new Map<string, Product>())
  const lotlessLineRef = useRef(new Map<string, StocktakeCountLine | null | 'ambiguous'>())
  const logIdRef = useRef(0)
  const dirtyRef = useRef(false)

  const locations = useMemo(() => session?.locations ?? [], [session])
  const location = useMemo(
    () => locations.find((l) => l.binLocationId === locationId) ?? null,
    [locations, locationId],
  )
  const {
    lines,
    loading: linesLoading,
    error: linesError,
    truncated,
    reload: reloadLines,
    applyLine,
  } = useLocationLines(session ? session.id : null, location)

  const canCount = session ? can('stocktake.count', session.warehouseId) : false
  const counting = session?.status === 'COUNTING'
  const editable = canCount && counting
  const stepParsed = parseQuantityInput(stepText, false)
  const step = stepParsed.ok ? stepParsed.value : 1

  const productIds = useMemo(() => lines.map((l) => l.productId), [lines])
  const products = useProductBriefs(productIds)
  const byLine = useMemo(() => queueStateByLine(queueItems), [queueItems])
  const unsent = queueItems.length
  const pendingCount = queueItems.filter((i) => i.status === 'queued' || i.status === 'sending').length

  const focusScan = useCallback(() => {
    setTimeout(() => scanInputRef.current?.focus({ preventScroll: true }), 0)
  }, [])

  const pushLog = useCallback((ok: boolean, text: string) => {
    logIdRef.current += 1
    const entry = { id: logIdRef.current, ok, text }
    setLog((prev) => [entry, ...prev].slice(0, LOG_SIZE))
  }, [])

  // ------------------------------------------------------------------------------------------------
  // Hàng đợi gửi số đếm (một hàng đợi cho mỗi đợt)
  // ------------------------------------------------------------------------------------------------

  /** Tra hàng hóa theo mã quét: tìm gần đúng trên product-service rồi chọn bản khớp đúng mã vạch hoặc SKU. */
  const resolveProduct = useCallback(async (code: string): Promise<Product | null> => {
    const cacheKey = code.trim().toLowerCase()
    const cached = productCacheRef.current.get(cacheKey)
    if (cached) return cached
    let found: Product | null
    try {
      const page = await fetchProducts({ keyword: code, page: 0, size: 20 })
      found = matchProductByCode(page.content ?? [], code)
      if (!found && (page.totalElements ?? 0) > (page.content ?? []).length) {
        // Quá nhiều kết quả gần đúng: thử đúng SKU
        try {
          found = await fetchProductBySku(encodeURIComponent(code))
        } catch (err) {
          if (getErrorStatus(err) !== 404) throw err
        }
      }
    } catch (err) {
      throw new LookupFailure(getApiErrorMessage(err, 'Không tra được hàng hóa') || 'Không tra được hàng hóa')
    }
    if (found) productCacheRef.current.set(cacheKey, found)
    return found
  }, [])

  /**
   * Dòng cần đếm của một hàng không theo lô tại một vị trí, hỏi máy chủ (danh sách đang hiện có thể thuộc vị trí khác
   * hoặc bị cắt bớt) và nhớ lại theo vị trí và sản phẩm: dòng cần đếm được chốt lúc bắt đầu đợt nên không đổi.
   */
  const findLotlessLine = useCallback(
    async (
      binLocationId: number,
      binLocationCode: string,
      productId: number,
    ): Promise<StocktakeCountLine | null | 'ambiguous'> => {
      if (sessionId === null) return null
      const key = `${binLocationId}|${productId}`
      const cached = lotlessLineRef.current.get(key)
      if (cached !== undefined) return cached
      let page
      try {
        page = await fetchCountLines(sessionId, { binCode: binLocationCode, productId, page: 0, size: 50 })
      } catch (err) {
        throw new LookupFailure(
          getApiErrorMessage(err, 'Không tra được dòng cần đếm') || 'Không tra được dòng cần đếm',
        )
      }
      const found = resolveLotlessScanLine(page.content ?? [], binLocationId, productId)
      lotlessLineRef.current.set(key, found)
      return found
    },
    [sessionId],
  )

  const sessionRef = useRef(session)
  const handlersRef = useRef({ applyLine, reloadLines, reloadSession, pushLog, resolveProduct, findLotlessLine })
  useEffect(() => {
    sessionRef.current = session
    handlersRef.current = { applyLine, reloadLines, reloadSession, pushLog, resolveProduct, findLotlessLine }
  })

  useEffect(() => {
    if (sessionId === null) return
    const send = async (op: CountOp): Promise<CountResult> => {
      if (op.kind === 'scan') {
        const product = await handlersRef.current.resolveProduct(op.code)
        if (!product) {
          throw new ScanRejection(`Không tìm thấy hàng hóa hoặc vị trí trong đợt có mã "${op.code}"`)
        }
        const label = `${product.sku} - ${product.name}`
        const scope = sessionRef.current?.productIds ?? []
        if (scope.length > 0 && !scope.includes(product.id)) {
          throw new ScanRejection(`${label} không thuộc phạm vi hàng hóa của đợt kiểm kê`)
        }
        let lotNo: string | null = null
        if (product.manageByLot) {
          lotNo = op.labelLotNo ?? (op.typedLotNo.trim() || null)
          if (!lotNo) {
            throw new ScanRejection(`${label} quản lý theo lô: nhập số lô ở ô "Số lô" hoặc quét tem lô rồi quét lại`)
          }
        }
        op.resolvedProductId = product.id
        op.resolvedLotNo = lotNo
        op.resolvedLabel = label
        if (!product.manageByLot) {
          // Hàng không theo lô: cộng vào dòng cần đếm của vị trí (dòng mang số lô hệ thống tự đặt), không tạo hàng phát sinh
          const target = await handlersRef.current.findLotlessLine(op.binLocationId, op.binLocationCode, product.id)
          if (target === 'ambiguous') {
            throw new ScanRejection(
              `${label} có nhiều dòng cần đếm tại ${op.binLocationCode}: bấm +1 hoặc Nhập số trên đúng dòng`,
            )
          }
          if (target) {
            op.resolvedLotNo = target.lotNo
            const line = await postCount(sessionId, { lineId: target.lineId, quantity: op.quantity, mode: 'ADD' })
            return { line, label }
          }
        }
        const line = await postCount(sessionId, {
          binLocationId: op.binLocationId,
          productId: product.id,
          lotNo: lotNo ?? undefined,
          quantity: op.quantity,
          mode: 'ADD',
        })
        return { line, label }
      }
      if (op.kind === 'line') {
        const line = await postCount(sessionId, { lineId: op.lineId, quantity: op.quantity, mode: op.mode })
        return { line, label: op.label }
      }
      const line = await postCount(sessionId, {
        binLocationId: op.binLocationId,
        productId: op.productId,
        lotNo: op.lotNo ?? undefined,
        quantity: op.quantity,
        mode: op.mode,
      })
      return { line, label: op.label }
    }

    const queue = new SerialCountQueue<CountOp, CountResult>({
      send,
      classify: classifyCountError,
      describe: (error) =>
        error instanceof ScanRejection || error instanceof LookupFailure
          ? error.message
          : getApiErrorMessage(error, 'Máy chủ không ghi được số đếm') || 'Máy chủ không ghi được số đếm',
      onSuccess: (item, result) => {
        playSuccessBeep()
        dirtyRef.current = true
        handlersRef.current.applyLine(result.line)
        const op = item.payload
        const lot = result.line.lotNo ? ` lô ${result.line.lotNo}` : ''
        const action = op.kind !== 'scan' && op.mode === 'SET' ? `ghi ${formatQty(op.quantity)}` : `+${formatQty(op.quantity)}`
        handlersRef.current.pushLog(
          true,
          `${result.label}${lot} ${action} tại ${result.line.binLocationCode}: đã đếm ${formatQty(result.line.countedQuantity)}${
            result.line.unexpected && result.line.countTimes === 1 ? ' (hàng phát sinh)' : ''
          }`,
        )
      },
      onFailure: (item, kind) => {
        playErrorBeep()
        handlersRef.current.pushLog(false, item.error ?? 'Không ghi được số đếm')
        if (kind === 'unknown') {
          // Không rõ đã ghi chưa: tải lại để người dùng thấy số thật trên máy chủ trước khi thử lại
          handlersRef.current.reloadLines()
          handlersRef.current.reloadSession()
        }
      },
      onChange: (items) => {
        setQueueItems(items)
        if (items.length === 0 && dirtyRef.current) {
          // Gửi xong một loạt: cập nhật tiến độ của cả đợt một lần (không gọi lại sau mỗi lần quét)
          dirtyRef.current = false
          handlersRef.current.reloadSession()
        }
      },
    })
    queueRef.current = queue
    return () => {
      queue.dispose()
      if (queueRef.current === queue) queueRef.current = null
    }
  }, [sessionId])

  // Còn lần quét chưa gửi hoặc lỗi: hỏi trước khi rời trang
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => unsent > 0 && currentLocation.pathname !== nextLocation.pathname,
  )
  const blockerRef = useRef({ blocker, unsent })
  useEffect(() => {
    blockerRef.current = { blocker, unsent }
  })
  useEffect(() => {
    if (blocker.state !== 'blocked') return
    modal.confirm({
      title: 'Còn lần ghi số đếm chưa xong',
      content: `Còn ${blockerRef.current.unsent} lần ghi đang chờ gửi hoặc bị lỗi. Rời trang thì các lần chưa gửi sẽ bị bỏ.`,
      okText: 'Vẫn rời trang',
      cancelText: 'Ở lại',
      okButtonProps: { danger: true },
      onOk: () => blockerRef.current.blocker.proceed?.(),
      onCancel: () => blockerRef.current.blocker.reset?.(),
    })
  }, [blocker.state, modal])
  useEffect(() => {
    if (unsent === 0) return
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [unsent])

  // ------------------------------------------------------------------------------------------------
  // Quét và thao tác
  // ------------------------------------------------------------------------------------------------

  const selectLocation = (loc: StocktakeLocation | null) => {
    setLocationId(loc ? loc.binLocationId : null)
    focusScan()
  }

  const handleScan = (raw: string) => {
    const text = raw.trim()
    if (!text || !session) return
    const loc = findLocationByCode(locations, text)
    if (loc) {
      playSuccessBeep()
      selectLocation(loc)
      pushLog(true, `Đã chọn vị trí ${loc.binLocationCode}`)
      return
    }
    if (!location) {
      playErrorBeep()
      pushLog(false, `Chưa chọn vị trí: quét mã vị trí trước khi quét hàng ("${text}" không phải vị trí của đợt)`)
      return
    }
    if (!editable) {
      playErrorBeep()
      pushLog(false, counting ? 'Bạn không có quyền kiểm đếm ở kho này' : 'Đợt kiểm kê không ở trạng thái đang đếm')
      return
    }
    if (!stepParsed.ok) {
      playErrorBeep()
      pushLog(false, `Số lượng mỗi lần quét không hợp lệ: ${stepParsed.error}`)
      return
    }
    const parsed = parseScannedCode(text)
    queueRef.current?.enqueue({
      kind: 'scan',
      code: parsed.code,
      labelLotNo: parsed.lotNo,
      typedLotNo: lotText,
      binLocationId: location.binLocationId,
      binLocationCode: location.binLocationCode,
      quantity: stepParsed.value,
    })
  }

  const lineLabel = (line: StocktakeCountLine) => productLabel(products, line.productId)

  const addToLine = (line: StocktakeCountLine) => {
    if (!stepParsed.ok) {
      playErrorBeep()
      message.error(`Số lượng mỗi lần quét không hợp lệ: ${stepParsed.error}`)
      return
    }
    queueRef.current?.enqueue({
      kind: 'line',
      lineId: line.lineId,
      productId: line.productId,
      lotNo: line.lotNo,
      mode: 'ADD',
      label: lineLabel(line),
      binLocationId: line.binLocationId,
      binLocationCode: line.binLocationCode,
      quantity: stepParsed.value,
    })
    focusScan()
  }

  const submitSet = (quantity: number) => {
    const line = setTarget
    if (!line) return
    queueRef.current?.enqueue({
      kind: 'line',
      lineId: line.lineId,
      productId: line.productId,
      lotNo: line.lotNo,
      mode: 'SET',
      label: lineLabel(line),
      binLocationId: line.binLocationId,
      binLocationCode: line.binLocationCode,
      quantity,
    })
    setSetTarget(null)
    focusScan()
  }

  const submitUnexpected = (value: { product: Product; lotNo: string | null; quantity: number }) => {
    if (!location) return
    queueRef.current?.enqueue({
      kind: 'product',
      productId: value.product.id,
      lotNo: value.lotNo,
      mode: 'ADD',
      label: `${value.product.sku} - ${value.product.name}`,
      binLocationId: location.binLocationId,
      binLocationCode: location.binLocationCode,
      quantity: value.quantity,
    })
    setUnexpectedOpen(false)
    focusScan()
  }

  const retryItem = (item: QueueItem<CountOp>) => {
    if (item.status === 'unknown') {
      modal.confirm({
        title: 'Thử lại lần ghi không rõ kết quả?',
        content:
          'Máy chủ có thể đã ghi lần này. Hãy xem số đã đếm của dòng (đã tải lại) trước. Nếu số đã tăng thì bấm Bỏ thay vì thử lại.',
        okText: 'Vẫn thử lại',
        cancelText: 'Đóng',
        onOk: () => {
          queueRef.current?.retry(item.id)
        },
      })
      return
    }
    queueRef.current?.retry(item.id)
  }

  // ------------------------------------------------------------------------------------------------
  // Dòng chưa đếm của cả đợt (khi chưa chọn vị trí)
  // ------------------------------------------------------------------------------------------------

  const [uncountedPage, setUncountedPage] = useState(0)
  const uncountedFetcher = useCallback(
    (query: CountLinesQuery, signal: AbortSignal) =>
      sessionId === null ? Promise.reject(new Error('no session')) : fetchCountLines(sessionId, query, signal),
    [sessionId],
  )
  const uncountedQuery = useMemo<CountLinesQuery | null>(
    () => (session && !location && session.status !== 'DRAFT' ? { counted: false, page: uncountedPage, size: 50 } : null),
    [session, location, uncountedPage],
  )
  // Tải lại khi tiến độ của đợt đổi (sau mỗi loạt ghi số đếm) hoặc khi quay lại từ một vị trí
  const uncounted = useReportQuery(uncountedFetcher, uncountedQuery, session?.countedLines ?? 0)
  const uncountedProducts = useProductBriefs(
    useMemo(() => (uncounted.data?.content ?? []).map((l) => l.productId), [uncounted.data]),
  )

  // ------------------------------------------------------------------------------------------------
  // Hiển thị
  // ------------------------------------------------------------------------------------------------

  if (!canRead) {
    return <ForbiddenResult description="Kiểm đếm dành cho Quản trị viên, Quản lý kho và Nhân viên kho của kho." />
  }
  if (sessionId === null) {
    return <Result status="404" title="Không tìm thấy đợt kiểm kê" extra={<Link to="/stocktakes">Về danh sách</Link>} />
  }
  if (!session) {
    return sessionError ? (
      <Result
        status={getErrorStatus(sessionError) === 404 ? '404' : 'error'}
        title="Không tải được đợt kiểm kê"
        subTitle={getApiErrorMessage(sessionError, 'Vui lòng thử lại sau.')}
        extra={<Button onClick={reloadSession}>Thử lại</Button>}
      />
    ) : (
      <div className="flex justify-center py-16">
        <Spin size="large" />
      </div>
    )
  }
  if (!canCount) {
    return (
      <ForbiddenResult description="Chỉ Quản trị viên, Quản lý kho hoặc Nhân viên kho được phân công kho của đợt này mới được kiểm đếm." />
    )
  }

  const visibleLines = onlyUncounted ? lines.filter((l) => !l.counted) : lines
  const countedHere = lines.filter((l) => l.counted).length

  return (
    <div className="flex flex-col gap-3 w-full max-w-3xl mx-auto">
      {/* Đầu trang: đợt, tiến độ */}
      <div className="flex flex-col gap-2 rounded-lg bg-white border border-slate-200 p-3">
        <div className="flex items-center justify-between gap-2">
          <Link to={`/stocktakes/${session.id}`} className="text-sm">
            <ArrowLeftOutlined /> Chi tiết đợt
          </Link>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              reloadSession()
              reloadLines()
            }}
          >
            Làm mới
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono font-semibold text-lg">{session.code}</span>
          <StocktakeStatusTag status={session.status} />
        </div>
        <div className="text-xs text-slate-500">{session.warehouseName ?? `Kho ID ${session.warehouseId}`}</div>
        <div>
          <div className="flex justify-between text-sm">
            <span>Tiến độ cả đợt</span>
            <span className="font-semibold">
              {session.countedLines}/{session.totalLines} dòng
            </span>
          </div>
          <Progress percent={progressPercent(session.countedLines, session.totalLines)} showInfo={false} />
        </div>
        {pendingCount > 0 && <Text type="secondary">Đang gửi {pendingCount} lần ghi...</Text>}
      </div>

      {!counting && (
        <Alert
          type="warning"
          showIcon
          message="Đợt không ở trạng thái đang đếm nên không ghi số đếm được."
          description="Chỉ xem được số đã đếm. Quay lại chi tiết đợt để xem trạng thái."
        />
      )}

      <QueueFailures
        items={queueItems}
        onRetry={retryItem}
        onDiscard={(item) => {
          queueRef.current?.discard(item.id)
          focusScan()
        }}
      />

      {/* Ô quét và vị trí */}
      <div className="flex flex-col gap-2 rounded-lg bg-white border border-slate-200 p-3">
        <Select<number>
          size="large"
          showSearch
          allowClear
          placeholder="Chọn vị trí hoặc quét mã vị trí"
          value={location?.binLocationId}
          optionFilterProp="label"
          suffixIcon={<EnvironmentOutlined />}
          onChange={(value) => selectLocation(locations.find((l) => l.binLocationId === value) ?? null)}
          options={locations.map((l) => ({ value: l.binLocationId, label: l.binLocationCode }))}
          className="w-full"
        />
        <Input
          ref={scanInputRef}
          size="large"
          autoFocus
          allowClear
          inputMode={hideKeyboard ? 'none' : 'text'}
          enterKeyHint="go"
          autoComplete="off"
          placeholder={location ? 'Quét mã vạch, SKU hoặc tem lô rồi Enter' : 'Quét mã vị trí rồi Enter'}
          value={scanText}
          disabled={!counting}
          onChange={(e) => setScanText(e.target.value)}
          onPressEnter={(e) => {
            const value = e.currentTarget.value
            setScanText('')
            handleScan(value)
          }}
          onBlur={(e) => {
            // Giữ con trỏ ở ô quét để súng bắn mã luôn nhập vào đây, trừ khi người dùng chủ động bấm ô khác
            if (!e.relatedTarget && !setTarget && !unexpectedOpen && !cameraOpen) focusScan()
          }}
          className="!text-lg"
          prefix={<AimOutlined />}
        />
        <div className="grid grid-cols-2 gap-2">
          <Input
            size="large"
            placeholder="Số lô (hàng theo lô)"
            value={lotText}
            maxLength={50}
            allowClear
            onChange={(e) => setLotText(e.target.value)}
            onPressEnter={focusScan}
          />
          <Input
            size="large"
            inputMode="decimal"
            addonBefore="Mỗi lần +"
            value={stepText}
            status={stepParsed.ok ? undefined : 'error'}
            onChange={(e) => setStepText(e.target.value)}
            onPressEnter={focusScan}
          />
        </div>
        {!stepParsed.ok && <Text type="danger" className="text-xs">{stepParsed.error}</Text>}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          <Button
            size="large"
            icon={<CameraOutlined />}
            onClick={() => setCameraOpen(true)}
            disabled={!counting}
            className="!h-12"
          >
            Quét bằng camera
          </Button>
          <Button
            size="large"
            icon={<PlusSquareOutlined />}
            onClick={() => setUnexpectedOpen(true)}
            disabled={!editable || !location}
            className="!h-12"
            data-write-action
          >
            Hàng ngoài danh sách
          </Button>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <Switch size="small" checked={hideKeyboard} onChange={setHideKeyboard} />
          Chỉ dùng máy quét (ẩn bàn phím ảo)
        </label>
      </div>

      {/* Nhật ký quét gần nhất */}
      {log.length > 0 && (
        <div className="flex flex-col gap-1 rounded-lg bg-white border border-slate-200 p-3">
          {log.map((entry, index) => (
            <div
              key={entry.id}
              className={`text-sm break-words ${entry.ok ? 'text-emerald-700' : 'text-red-600'} ${
                index === 0 ? 'font-semibold' : 'opacity-70'
              }`}
            >
              {entry.text}
            </div>
          ))}
        </div>
      )}

      {/* Dòng của vị trí đang chọn */}
      {location ? (
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <span className="font-mono font-semibold text-base">{location.binLocationCode}</span>
              <span className="text-sm text-slate-500">
                {' '}
                · đã đếm {countedHere}/{lines.length} dòng
              </span>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <Switch checked={onlyUncounted} onChange={setOnlyUncounted} />
              Chưa đếm
            </label>
          </div>
          {linesError ? (
            <Alert
              type="error"
              showIcon
              message={getApiErrorMessage(linesError, 'Không tải được dòng đếm của vị trí')}
              action={<Button onClick={reloadLines}>Thử lại</Button>}
            />
          ) : null}
          {truncated && (
            <Alert type="warning" showIcon message="Vị trí có quá nhiều dòng, chỉ hiện 2000 dòng đầu." />
          )}
          {linesLoading && lines.length === 0 ? (
            <div className="flex justify-center py-8">
              <Spin />
            </div>
          ) : visibleLines.length === 0 ? (
            <Empty
              description={
                lines.length === 0
                  ? 'Vị trí này không có hàng trong ảnh chụp tồn. Nếu thấy hàng thực tế, quét mã hoặc bấm "Hàng ngoài danh sách".'
                  : 'Mọi dòng của vị trí đã được đếm.'
              }
            />
          ) : (
            visibleLines.map((line) => {
              const brief = products.get(line.productId)
              return (
                <CountLineCard
                  key={line.lineId}
                  line={line}
                  productText={productLabel(products, line.productId)}
                  uomText={brief?.uomName ?? brief?.uomCode ?? null}
                  queue={byLine.get(lineKeyOf(line.binLocationId, line.productId, line.lotNo))}
                  editable={editable}
                  step={step}
                  onAdd={() => addToLine(line)}
                  onSet={() => setSetTarget(line)}
                />
              )
            })
          )}
        </div>
      ) : (
        <div className="flex flex-col gap-2 rounded-lg bg-white border border-slate-200 p-3">
          <div className="font-medium">Dòng chưa đếm của cả đợt</div>
          <Text type="secondary" className="text-xs">
            Bấm một dòng để chuyển tới vị trí đó.
          </Text>
          {uncounted.error ? (
            <Alert type="error" showIcon message={getApiErrorMessage(uncounted.error, 'Không tải được dòng chưa đếm')} />
          ) : null}
          {uncounted.loading && !uncounted.data ? (
            <Spin />
          ) : (uncounted.data?.content ?? []).length === 0 ? (
            <Empty description="Không còn dòng nào chưa đếm" />
          ) : (
            <>
              {(uncounted.data?.content ?? []).map((l) => (
                <button
                  key={l.lineId}
                  type="button"
                  className="text-left rounded-md border border-slate-200 p-3 min-h-12 hover:bg-slate-50"
                  onClick={() => selectLocation(locations.find((loc) => loc.binLocationId === l.binLocationId) ?? null)}
                >
                  <div className="font-mono text-sm font-semibold">{l.binLocationCode}</div>
                  <div className="text-sm break-words">
                    {productLabel(uncountedProducts, l.productId)}
                    {l.lotNo ? ` · Lô ${l.lotNo}` : ''}
                  </div>
                  {l.recountRequested && <Tag color="orange">Đếm lại</Tag>}
                </button>
              ))}
              {(uncounted.data?.totalPages ?? 0) > 1 && (
                <div className="flex items-center justify-between">
                  <Button disabled={uncountedPage === 0} onClick={() => setUncountedPage((p) => Math.max(0, p - 1))}>
                    Trang trước
                  </Button>
                  <span className="text-sm">
                    {uncountedPage + 1}/{uncounted.data?.totalPages}
                  </span>
                  <Button
                    disabled={uncounted.data?.last ?? true}
                    onClick={() => setUncountedPage((p) => p + 1)}
                  >
                    Trang sau
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      )}

      <BarcodeCameraScanner
        open={cameraOpen}
        onClose={() => {
          setCameraOpen(false)
          focusScan()
        }}
        onScan={handleScan}
      />
      {setTarget && (
        <SetQuantityModal
          open
          title={`${setTarget.binLocationCode} · ${lineLabel(setTarget)}${setTarget.lotNo ? ` · Lô ${setTarget.lotNo}` : ''}`}
          currentCounted={setTarget.countedQuantity}
          onCancel={() => {
            setSetTarget(null)
            focusScan()
          }}
          onSubmit={submitSet}
        />
      )}
      {unexpectedOpen && location && (
        <UnexpectedItemModal
          open
          binLocationCode={location.binLocationCode}
          productScope={session.productIds ?? []}
          onCancel={() => {
            setUnexpectedOpen(false)
            focusScan()
          }}
          onSubmit={submitUnexpected}
        />
      )}
    </div>
  )
}
