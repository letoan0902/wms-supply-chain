import { CloudUploadOutlined, FileTextOutlined, RobotOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Col, Modal, Radio, Row, Spin, Steps, Tag, Typography, Upload } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useBlocker, useSearchParams } from 'react-router-dom'
import ForbiddenResult from '../../components/ForbiddenResult'
import { getApiErrorMessage, getErrorStatus } from '../../services/api'
import { useCan } from '../auth/useCan'
import { extractDocument, fetchAiUsage, fetchExtraction, importExtraction } from './aiApi'
import {
  describeExtractionError,
  getAiErrorCode,
  getAiErrorData,
  isCanceledError,
  isTimeoutError,
} from './aiErrors'
import { formatUsage } from './aiFormat'
import {
  ACCEPT_ATTRIBUTE,
  applyCellEdit,
  buildImportRows,
  checkFile,
  DATA_TYPE_ORDER,
  DATA_TYPES,
  formatClock,
  formatFileSize,
  formatRemaining,
  isDataType,
  LOW_CONFIDENCE_THRESHOLD,
  mapImportResults,
  mapImportValidationErrors,
  MAX_IMPORT_ROWS,
  remainingMs,
  toPreviewRows,
  validateRow,
} from './aiImport'
import type { PreviewRow } from './aiImport'
import {
  assessRows,
  blockingRowNumbers,
  emptyOptionalColumns,
  formatRowList,
  IDENTITY_FIELD,
  matchesFilter,
  ROW_STATUS_META,
  ROW_STATUS_ORDER,
  rowsToImport,
  summarize,
} from './aiImportStatus'
import type { RowFilter, RowStatus } from './aiImportStatus'
import './ai.css'
import AiImportPreviewTable from './components/AiImportPreviewTable'
import AiImportResultView from './components/AiImportResultView'
import { ProportionBar, StatTile } from './components/AiImportStatusParts'
import type { ImportOutcome } from './components/AiImportResultView'
import type { AiUsage, CellValue, Extraction, ExtractionDataType } from './types'

const { Title, Text } = Typography

type Stage = 'upload' | 'preview' | 'result'

const METHOD_LABELS: Record<string, string> = {
  COLUMN_MAPPING: 'AI ghép cột của bảng tính',
  HEADER_MATCHING: 'Chế độ giả lập: ghép cột theo tên tiêu đề, không dùng AI thật',
  AI_DOCUMENT: 'AI đọc trực tiếp tài liệu',
}

/**
 * Màn hình "Nhập dữ liệu bằng AI" (/ai-import): tải file, AI trích xuất thành bảng xem trước, người dùng sửa và chọn
 * dòng, bấm nhập rồi xem kết quả từng dòng. Không có gì được ghi vào danh mục trước bước nhập. Hợp đồng API ở
 * docs/SAAS_SPEC.md, mục WMS-24.2; `?type=PRODUCT` chọn sẵn loại dữ liệu, `?id=...` khôi phục bảng xem trước khi tải
 * lại trang.
 */
export default function AiImportPage() {
  const { message, modal } = App.useApp()
  const can = useCan()
  const [searchParams, setSearchParams] = useSearchParams()
  const allowedTypes = DATA_TYPE_ORDER.filter((type) => can(DATA_TYPES[type].capability))

  const [initialId] = useState(() => searchParams.get('id'))
  const [dataType, setDataType] = useState<ExtractionDataType | null>(() => {
    const preset = searchParams.get('type')
    if (isDataType(preset) && allowedTypes.includes(preset)) return preset
    return allowedTypes[0] ?? null
  })

  // Bước 1: tải file
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [extracting, setExtracting] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [usage, setUsage] = useState<AiUsage | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  // Bước 2, 3: xem trước, nhập
  const [stage, setStage] = useState<Stage>('upload')
  const [extraction, setExtraction] = useState<Extraction | null>(null)
  const [rows, setRows] = useState<PreviewRow[]>([])
  const [deselected, setDeselected] = useState<Set<number>>(() => new Set())
  const [serverCellErrors, setServerCellErrors] = useState<Record<number, Record<string, string>>>({})
  const [filter, setFilter] = useState<RowFilter>('all')
  const [showEmptyColumns, setShowEmptyColumns] = useState(false)
  const [extractSeconds, setExtractSeconds] = useState<number | null>(null)
  const [dirty, setDirty] = useState(false)
  const [restoring, setRestoring] = useState(Boolean(initialId))
  const [restored, setRestored] = useState(false)
  const [restoreError, setRestoreError] = useState<string | null>(null)
  const [expiredByServer, setExpiredByServer] = useState(false)
  const [importing, setImporting] = useState(false)
  const [outcome, setOutcome] = useState<ImportOutcome | null>(null)
  const [now, setNow] = useState(0)

  const loadExtraction = (data: Extraction, fromReload: boolean) => {
    setExtraction(data)
    setRows(toPreviewRows(data.rows ?? []))
    setDeselected(new Set())
    setServerCellErrors({})
    setFilter('all')
    setShowEmptyColumns(false)
    setDirty(false)
    setOutcome(null)
    setExpiredByServer(false)
    setRestored(fromReload)
    setDataType(data.dataType)
    setStage('preview')
    if (data.usage) setUsage(data.usage)
  }

  // Số lượt AI của tháng (một lần khi mở trang); lỗi thì không hiện số
  useEffect(() => {
    fetchAiUsage()
      .then((info) => setUsage({ used: info.used, max: info.max }))
      .catch(() => undefined)
  }, [])

  // Tải lại trang có ?id=...: lấy lại bảng xem trước từ máy chủ (chỉnh sửa trước đó chỉ nằm trên trình duyệt nên mất)
  useEffect(() => {
    if (!initialId) return
    let active = true
    fetchExtraction(initialId)
      .then((data) => {
        if (active) {
          loadExtraction(data, true)
          setExtractSeconds(null)
        }
      })
      .catch((error: unknown) => {
        if (!active) return
        setRestoreError(getApiErrorMessage(error, 'Không lấy lại được kết quả trích xuất.') || null)
        setSearchParams({}, { replace: true })
      })
      .finally(() => {
        if (active) setRestoring(false)
      })
    return () => {
      active = false
    }
    // Chỉ chạy một lần với mã trích xuất có trong đường dẫn lúc mở trang
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialId])

  // Đếm giây khi chờ trích xuất
  useEffect(() => {
    if (!extracting) return
    const started = Date.now()
    const timer = window.setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 1000)
    return () => window.clearInterval(timer)
  }, [extracting])

  // Đồng hồ cho thời hạn của kết quả trích xuất
  useEffect(() => {
    if (!extraction) return
    const tick = () => setNow(Date.now())
    const first = window.setTimeout(tick, 0)
    const timer = window.setInterval(tick, 15_000)
    return () => {
      window.clearTimeout(first)
      window.clearInterval(timer)
    }
  }, [extraction])

  // Rời trang khi đang chờ trích xuất thì hủy request
  useEffect(() => () => abortRef.current?.abort(), [])

  // Hỏi lại khi rời trang (đổi trang trong ứng dụng, đóng hoặc tải lại tab) lúc còn chỉnh sửa chưa nhập
  const shouldBlock = extracting || (stage === 'preview' && dirty)
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => shouldBlock && currentLocation.pathname !== nextLocation.pathname,
  )
  useEffect(() => {
    if (!shouldBlock) return
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [shouldBlock])

  const columns = useMemo(() => extraction?.columns ?? [], [extraction])

  const errorsByRow = useMemo(() => {
    const map = new Map<number, Record<string, string>>()
    for (const row of rows) {
      const errors = { ...validateRow(columns, row.values), ...(serverCellErrors[row.row] ?? {}) }
      map.set(row.row, errors)
    }
    return map
  }, [rows, columns, serverCellErrors])

  const identityField = dataType ? IDENTITY_FIELD[dataType] : (columns[0]?.field ?? '')
  const assessmentList = useMemo(
    () => assessRows(rows, columns, errorsByRow, deselected, identityField),
    [rows, columns, errorsByRow, deselected, identityField],
  )
  const assessments = useMemo(() => new Map(assessmentList.map((item) => [item.row, item])), [assessmentList])
  const summary = summarize(assessmentList)
  const selectedRows = rowsToImport(rows, assessmentList)
  const blockingRows = blockingRowNumbers(assessmentList)
  const visibleRows = rows.filter((row) => {
    const item = assessments.get(row.row)
    return item ? matchesFilter(item, filter) : true
  })
  // Cột không bắt buộc mà AI không đọc được gì (thường là trường không có trong file): ẩn cho bảng gọn
  const emptyFields = useMemo(() => emptyOptionalColumns(columns, extraction?.rows ?? []), [columns, extraction])
  const hiddenFields = useMemo(() => new Set(showEmptyColumns ? [] : emptyFields), [showEmptyColumns, emptyFields])

  const remaining = remainingMs(extraction?.expiresAt, now)
  const expired = expiredByServer || (now > 0 && remaining !== null && remaining <= 0)

  if (allowedTypes.length === 0) {
    return (
      <ForbiddenResult description="Nhập dữ liệu bằng AI dành cho Quản trị hệ thống, Quản lý kho, Mua hàng (hàng hóa, nhà cung cấp) và Bán hàng (khách hàng)." />
    )
  }

  // ----- Bước 1 -----
  const pickFile = (picked: File) => {
    const error = checkFile(picked)
    setFileError(error)
    setUploadError(null)
    setFile(error ? null : picked)
  }

  const startExtraction = async () => {
    if (!file || !dataType || extracting) return
    const error = checkFile(file)
    if (error) {
      setFileError(error)
      return
    }
    const controller = new AbortController()
    abortRef.current = controller
    setExtracting(true)
    setElapsed(0)
    const started = Date.now()
    setUploadError(null)
    setRestoreError(null)
    try {
      const data = await extractDocument(file, dataType, controller.signal)
      loadExtraction(data, false)
      setExtractSeconds(Math.max(1, Math.round((Date.now() - started) / 1000)))
      setSearchParams({ id: data.extractionId }, { replace: true })
      message.success(`Đã đọc ${data.rows.length} dòng từ file ${data.fileName}. Hãy xem lại trước khi nhập.`)
    } catch (err: unknown) {
      if (isCanceledError(err)) {
        message.info(
          'Đã hủy chờ. Nếu máy chủ đã nhận file, lần trích xuất vẫn có thể chạy xong và được tính một lượt AI.',
        )
        fetchAiUsage()
          .then((info) => setUsage({ used: info.used, max: info.max }))
          .catch(() => undefined)
      } else {
        // Hết lượt AI, gói hết hạn: hộp thoại dùng chung đã báo, không báo lần hai
        setUploadError(describeExtractionError(err))
      }
    } finally {
      abortRef.current = null
      setExtracting(false)
    }
  }

  const cancelExtraction = () => abortRef.current?.abort()

  const resetToUpload = () => {
    setStage('upload')
    setExtraction(null)
    setRows([])
    setOutcome(null)
    setDirty(false)
    setRestored(false)
    setFile(null)
    setFileError(null)
    setUploadError(null)
    setSearchParams(dataType ? { type: dataType } : {}, { replace: true })
  }

  const confirmNewFile = () => {
    if (!dirty || stage !== 'preview') {
      resetToUpload()
      return
    }
    modal.confirm({
      title: 'Bỏ bảng xem trước hiện tại?',
      content: 'Các chỉnh sửa chưa nhập sẽ mất.',
      okText: 'Bỏ và tải file khác',
      cancelText: 'Ở lại',
      onOk: resetToUpload,
    })
  }

  // ----- Bước 2 -----
  const changeCell = (rowNumber: number, field: string, value: CellValue) => {
    setRows((prev) => prev.map((row) => (row.row === rowNumber ? applyCellEdit(row, field, value) : row)))
    setServerCellErrors((prev) => {
      if (!prev[rowNumber]?.[field]) return prev
      const rest = { ...prev[rowNumber] }
      delete rest[field]
      return { ...prev, [rowNumber]: rest }
    })
    setDirty(true)
  }

  const deleteRow = (rowNumber: number) => {
    setRows((prev) => prev.filter((row) => row.row !== rowNumber))
    setDirty(true)
    message.info(`Đã xóa dòng ${rowNumber} khỏi bảng xem trước.`)
  }

  const changeSelection = (keys: number[]) => {
    const chosen = new Set(keys)
    setDeselected((prev) => {
      const next = new Set(prev)
      for (const row of visibleRows) {
        if (chosen.has(row.row)) next.delete(row.row)
        else next.add(row.row)
      }
      return next
    })
  }

  const selectAll = (select: boolean) => {
    setDeselected(select ? new Set() : new Set(rows.map((row) => row.row)))
  }

  /** Bỏ chọn mọi dòng còn lỗi hoặc trùng mã: dòng vẫn ở trong bảng, tích lại được sau khi sửa. */
  const excludeBlocking = () => {
    if (blockingRows.length === 0) return
    setDeselected((prev) => new Set([...prev, ...blockingRows]))
    message.info(`Đã bỏ chọn ${blockingRows.length} dòng lỗi hoặc trùng mã. Các dòng này vẫn nằm trong bảng.`)
  }

  const toggleFilter = (next: RowFilter) => setFilter((prev) => (prev === next && next !== 'all' ? 'all' : next))

  // ----- Bước 3 -----
  const runImport = async (toSend: PreviewRow[]) => {
    if (!extraction || importing || toSend.length === 0) return
    const submitted = toSend.map((row) => row.row)
    setImporting(true)
    setServerCellErrors({})
    try {
      const result = await importExtraction(extraction.extractionId, buildImportRows(columns, toSend))
      const mapped = mapImportResults(submitted, result)
      const failedMessages = new Map<number, string>()
      const done = new Set<number>()
      for (const item of mapped) {
        if (item.previewRow === null) continue
        if (item.status === 'FAILED') failedMessages.set(item.previewRow, item.message ?? 'Lỗi không rõ lý do')
        else done.add(item.previewRow)
      }
      const left = rows
        .filter((row) => !done.has(row.row))
        .map((row) => (failedMessages.has(row.row) ? { ...row, lastError: failedMessages.get(row.row) ?? null } : row))
      setOutcome({ result, mapped, submitted: toSend, remaining: left })
      // Giữ nguyên các dòng người dùng đã bỏ chọn để lần nhập tiếp không tự gửi chúng
      setRows(left)
      setDirty(false)
      setFilter('all')
      setStage('result')
      if (result.failed > 0) {
        message.warning(`Đã nhập xong: ${result.created} tạo mới, ${result.skipped} bỏ qua, ${result.failed} lỗi.`)
      } else {
        message.success(`Đã nhập xong: ${result.created} tạo mới, ${result.skipped} bỏ qua.`)
      }
    } catch (error: unknown) {
      const status = getErrorStatus(error)
      if (status === 409) {
        // Một lần bấm khác (tab khác) đang nhập đúng kết quả trích xuất này
        message.info(getApiErrorMessage(error, 'Dữ liệu đang được nhập, vui lòng chờ kết quả.'))
      } else if (status === 404) {
        setExpiredByServer(true)
        message.error(getApiErrorMessage(error, 'Kết quả trích xuất đã hết hạn, hãy tải file lên lại.'))
      } else if (status === 400 && getAiErrorCode(error) === 'VALIDATION_ERROR') {
        setServerCellErrors(mapImportValidationErrors(submitted, getAiErrorData(error)))
        message.error(getApiErrorMessage(error, 'Có ô không đúng kiểu dữ liệu, hãy sửa rồi nhập lại.'))
      } else if (isTimeoutError(error)) {
        message.warning(
          'Quá thời gian chờ kết quả nhập, dữ liệu có thể đã vào danh mục một phần. Bấm nhập lại: mã đã có sẽ được bỏ qua, không tạo trùng.',
          8,
        )
      } else {
        const text = getApiErrorMessage(error, 'Nhập dữ liệu thất bại.')
        if (text) message.error(text)
      }
    } finally {
      setImporting(false)
    }
  }

  const confirmImport = () => {
    if (!dataType || selectedRows.length === 0 || summary.blocking > 0) return
    if (selectedRows.length > MAX_IMPORT_ROWS) {
      message.error(`Mỗi lần nhập tối đa ${MAX_IMPORT_ROWS} dòng.`)
      return
    }
    const toSend = [...selectedRows]
    const info = DATA_TYPES[dataType]
    const identityColumnLabel = columns.find((column) => column.field === identityField)?.label ?? 'Mã'
    // "Mã SKU" thành "mã SKU" để ghép giữa câu
    const identityLabel = identityColumnLabel.charAt(0).toLowerCase() + identityColumnLabel.slice(1)
    const reviewRowNumbers = assessmentList.filter((item) => item.status === 'review').map((item) => item.row)
    const excluded = summary.counts.excluded
    const excludedParts = [
      summary.excludedByProblem.error > 0 ? `${summary.excludedByProblem.error} dòng lỗi` : null,
      summary.excludedByProblem.duplicate > 0 ? `${summary.excludedByProblem.duplicate} dòng trùng mã` : null,
      summary.excludedByProblem.review > 0 ? `${summary.excludedByProblem.review} dòng cần kiểm tra` : null,
      summary.excludedByProblem.ready > 0 ? `${summary.excludedByProblem.ready} dòng sẵn sàng` : null,
    ].filter((part): part is string => part !== null)
    modal.confirm({
      title: `Ghi ${toSend.length} dòng vào ${info.catalogLabel}?`,
      width: 580,
      content: (
        <div className="space-y-3 pt-1 text-sm text-slate-700">
          <ProportionBar
            label="Dòng sẽ gửi và dòng không gửi"
            total={summary.total}
            segments={(['ready', 'review', 'excluded'] as const).map((status) => ({
              key: status,
              label: status === 'excluded' ? 'Không gửi' : ROW_STATUS_META[status].title,
              count: summary.counts[status],
              color: ROW_STATUS_META[status].barColor,
            }))}
          />
          <ul className="m-0 list-disc space-y-1.5 pl-5">
            <li>
              <strong>{toSend.length} dòng</strong> được gửi để tạo mới trong {info.catalogLabel}
              {reviewRowNumbers.length > 0
                ? `; trong đó ${reviewRowNumbers.length} dòng còn ô AI chưa chắc chắn mà bạn chưa sửa (${formatRowList(reviewRowNumbers)}).`
                : '.'}
            </li>
            <li>
              Dòng nào có {identityLabel} đã có trong {info.catalogLabel} sẽ được{' '}
              <strong>bỏ qua, giữ nguyên dữ liệu đang có, không ghi đè</strong>.
            </li>
            {dataType === 'PRODUCT' && <li>Danh mục và đơn vị tính chưa có sẽ được tạo mới theo tên trong file.</li>}
            {excluded > 0 && (
              <li>
                <strong>{excluded} dòng không gửi</strong> vì bạn đã bỏ chọn
                {excludedParts.length > 0 ? ` (${excludedParts.join(', ')})` : ''}; các dòng này vẫn nằm trong bảng.
              </li>
            )}
            <li>
              Mỗi dòng được kiểm tra như khi tạo từng bản ghi và lưu riêng: dòng bị từ chối không ảnh hưởng các dòng khác.
              Bấm nhập không tốn thêm lượt AI.
            </li>
          </ul>
        </div>
      ),
      okText: `Nhập ${toSend.length} dòng`,
      cancelText: 'Xem lại',
      onOk: () => {
        void runImport(toSend)
      },
    })
  }

  const backToPreview = () => {
    setStage('preview')
    setDirty(rows.length > 0)
  }

  const stepIndex = stage === 'upload' ? 0 : stage === 'preview' ? 1 : 2
  const usageText = formatUsage(usage)
  const quotaReached = usage !== null && typeof usage.max === 'number' && usage.max >= 0 && usage.used >= usage.max
  const typeInfo = dataType ? DATA_TYPES[dataType] : null
  const expiresClock = formatClock(extraction?.expiresAt)

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Title level={3} className="!mb-1 flex items-center gap-2">
            <RobotOutlined className="text-blue-600" />
            Nhập dữ liệu bằng AI
          </Title>
          <Text type="secondary">
            AI đọc file Excel, CSV, PDF hoặc ảnh thành bảng dữ liệu để bạn xem lại, sửa rồi mới nhập vào danh mục.
          </Text>
        </div>
        {usageText !== null && (
          <Tag color={quotaReached ? 'red' : 'blue'} className="m-0 self-start sm:self-center">
            Lượt AI tháng này: {usageText}
          </Tag>
        )}
      </div>

      <Card size="small">
        <Steps
          size="small"
          current={stepIndex}
          items={[{ title: 'Tải file' }, { title: 'Xem trước và duyệt' }, { title: 'Kết quả nhập' }]}
        />
      </Card>

      {restoreError && (
        <Alert
          type="warning"
          showIcon
          closable
          onClose={() => setRestoreError(null)}
          message="Không khôi phục được bảng xem trước"
          description={restoreError}
        />
      )}

      {restoring && (
        <Card>
          <div className="flex flex-col items-center gap-3 py-8">
            <Spin />
            <Text type="secondary">Đang lấy lại kết quả trích xuất...</Text>
          </div>
        </Card>
      )}

      {!restoring && stage === 'upload' && (
        <Card title="Bước 1: Chọn loại dữ liệu và file">
          {extracting ? (
            <div className="flex flex-col items-center gap-3 py-8 text-center">
              <Spin size="large" />
              <div className="font-medium">
                Đang đọc file {file?.name} ({elapsed} giây)
              </div>
              <Text type="secondary" className="max-w-xl">
                Bảng tính thường xong trong vài chục giây; PDF và ảnh có thể mất tới khoảng 3 phút. Vui lòng giữ trang
                này mở. Chưa có dữ liệu nào được ghi vào danh mục.
              </Text>
              <Button onClick={cancelExtraction}>Hủy</Button>
            </div>
          ) : (
            <div className="space-y-4">
              <div>
                <div className="mb-2 font-medium">Loại dữ liệu</div>
                <Radio.Group
                  value={dataType}
                  onChange={(event) => setDataType(event.target.value as ExtractionDataType)}
                  optionType="button"
                  buttonStyle="solid"
                  options={allowedTypes.map((type) => ({ value: type, label: DATA_TYPES[type].label }))}
                />
                {typeInfo && <div className="mt-2 text-xs text-slate-500">{typeInfo.hint}</div>}
              </div>

              <Upload.Dragger
                accept={ACCEPT_ATTRIBUTE}
                multiple={false}
                showUploadList={false}
                beforeUpload={(picked) => {
                  pickFile(picked)
                  return Upload.LIST_IGNORE
                }}
              >
                <div className="py-4">
                  <CloudUploadOutlined className="text-4xl text-blue-500" />
                  <div className="mt-2 font-medium">Kéo thả file vào đây hoặc bấm để chọn file</div>
                  <div className="text-xs text-slate-500">xlsx, xls, csv, pdf, png, jpg; tối đa 10 MB (ảnh png, jpg tối đa 7,5 MB), 200 dòng</div>
                </div>
              </Upload.Dragger>

              {file && (
                <div className="flex flex-wrap items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2">
                  <FileTextOutlined className="text-slate-500" />
                  <span className="font-medium">{file.name}</span>
                  <span className="text-xs text-slate-500">{formatFileSize(file.size)}</span>
                  <Button type="link" size="small" onClick={() => setFile(null)}>
                    Chọn file khác
                  </Button>
                </div>
              )}
              {fileError && <Alert type="error" showIcon message={fileError} />}
              {uploadError && <Alert type="error" showIcon message="Không trích xuất được dữ liệu" description={uploadError} />}

              <Alert
                type="info"
                showIcon
                message="AI làm gì với file của bạn"
                description={
                  <ul className="m-0 list-disc space-y-0.5 pl-4 text-sm">
                    <li>
                      Bảng tính: AI xem dòng tiêu đề và vài dòng mẫu để ghép cột với các trường của danh mục, rồi hệ
                      thống đọc mọi dòng. PDF, ảnh: AI đọc trực tiếp từng dòng.
                    </li>
                    <li>
                      Mỗi lần trích xuất thành công dùng 1 lượt AI trong hạn mức tháng
                      {usageText !== null ? ` (đã dùng ${usageText})` : ''}; file lỗi không bị tính.
                    </li>
                    <li>Chưa có gì được ghi vào danh mục cho tới khi bạn xem lại và bấm nhập. Kết quả giữ 1 giờ.</li>
                  </ul>
                }
              />
              {quotaReached && (
                <Alert type="warning" showIcon message="Đã dùng hết lượt AI của tháng theo gói hiện tại." />
              )}

              <Button
                type="primary"
                size="large"
                icon={<RobotOutlined />}
                disabled={!file || !dataType}
                onClick={startExtraction}
                data-write-action
              >
                Trích xuất dữ liệu
              </Button>
            </div>
          )}
        </Card>
      )}

      {!restoring && stage === 'preview' && extraction && typeInfo && (
        <Card
          title={`Bước 2: Xem trước và duyệt dữ liệu ${typeInfo.noun}`}
          extra={
            <Button onClick={confirmNewFile} disabled={importing}>
              Tải file khác
            </Button>
          }
        >
          <div className="space-y-4">
            <div className="rounded-lg border border-blue-200/80 bg-blue-50/50 px-4 py-3">
              <div className="text-[15px] font-semibold text-slate-900">AI đã đọc xong, chưa có gì được lưu</div>
              <div className="mt-0.5 text-sm text-slate-600">
                Bạn là người quyết định: xem lại, sửa hoặc bỏ chọn từng dòng. Dữ liệu chỉ được ghi vào{' '}
                {typeInfo.catalogLabel} khi bạn bấm Nhập và xác nhận.
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
                <span>
                  File <strong className="font-medium text-slate-700">{extraction.fileName}</strong>
                </span>
                <span>
                  Loại dữ liệu <strong className="font-medium text-slate-700">{typeInfo.label}</strong>
                </span>
                <span>
                  {extraction.rows.length === extraction.totalRows
                    ? `${extraction.totalRows} dòng trong file`
                    : `${extraction.totalRows} dòng trong file, đã đọc ${extraction.rows.length}`}
                </span>
                {extractSeconds !== null && <span>AI đọc trong {extractSeconds} giây</span>}
                <span>{METHOD_LABELS[extraction.method] ?? extraction.method}</span>
                {remaining !== null && now > 0 && (
                  <span className={expired ? 'text-red-600' : remaining < 10 * 60_000 ? 'text-amber-600' : ''}>
                    Kết quả giữ tới {expiresClock ?? ''} ({formatRemaining(remaining)})
                  </span>
                )}
              </div>
            </div>

            {restored && (
              <Alert
                type="info"
                showIcon
                closable
                message="Đã khôi phục bảng xem trước từ máy chủ sau khi tải lại trang. Các chỉnh sửa trước đó chỉ lưu trên trình duyệt nên không còn."
              />
            )}
            {expired && (
              <Alert
                type="error"
                showIcon
                message="Kết quả trích xuất đã hết hạn (giữ 1 giờ) nên không nhập được nữa. Hãy tải file lên lại."
                action={
                  <Button size="small" onClick={resetToUpload}>
                    Tải file lên lại
                  </Button>
                }
              />
            )}
            {extraction.method === 'HEADER_MATCHING' && (
              <Alert
                type="warning"
                showIcon
                message="Hệ thống đang ở chế độ giả lập AI: cột được ghép theo tên tiêu đề, hãy kiểm tra kỹ."
              />
            )}
            {extraction.truncated && (
              <Alert
                type="warning"
                showIcon
                message={`File có ${extraction.totalRows} dòng, chỉ 200 dòng đầu được đọc. Hãy tách phần còn lại sang file khác.`}
              />
            )}
            {extraction.warnings.length > 0 && (
              <Alert
                type="warning"
                showIcon
                message="Lưu ý khi đọc file"
                description={
                  <ul className="m-0 list-disc pl-4">
                    {extraction.warnings.map((warning) => (
                      <li key={warning}>{warning}</li>
                    ))}
                  </ul>
                }
              />
            )}

            <section aria-label="Tóm tắt kết quả đọc" className="space-y-2">
              <div className="text-sm text-slate-700">
                AI tìm thấy <strong>{summary.total}</strong> dòng: <strong>{summary.toImport}</strong> dòng sẽ được nhập
                {summary.blocking > 0 && (
                  <>
                    , <strong className="text-red-600">{summary.blocking}</strong> dòng phải sửa hoặc bỏ chọn trước
                  </>
                )}
                {summary.counts.excluded > 0 && `, ${summary.counts.excluded} dòng đã bỏ chọn`}.
              </div>
              <ProportionBar
                label={`Tỉ lệ ${summary.total} dòng theo trạng thái`}
                total={summary.total}
                segments={ROW_STATUS_ORDER.map((status) => ({
                  key: status,
                  label: ROW_STATUS_META[status].title,
                  count: summary.counts[status],
                  color: ROW_STATUS_META[status].barColor,
                }))}
              />
              <Row gutter={[8, 8]}>
                <Col xs={12} sm={8} xl={4}>
                  <StatTile
                    title="Tất cả"
                    value={summary.total}
                    hint="Bấm để xem lại toàn bộ"
                    active={filter === 'all'}
                    onClick={() => setFilter('all')}
                  />
                </Col>
                {ROW_STATUS_ORDER.map((status: RowStatus) => (
                  <Col key={status} xs={12} sm={8} xl={4}>
                    <StatTile
                      title={ROW_STATUS_META[status].title}
                      value={summary.counts[status]}
                      hint={ROW_STATUS_META[status].hint}
                      color={ROW_STATUS_META[status].barColor}
                      active={filter === status}
                      onClick={() => toggleFilter(status)}
                    />
                  </Col>
                ))}
              </Row>
            </section>

            <div className="flex flex-wrap items-center gap-2">
              <Button
                size="small"
                onClick={excludeBlocking}
                disabled={blockingRows.length === 0 || importing}
              >
                Bỏ chọn các dòng lỗi ({blockingRows.length})
              </Button>
              <Button
                size="small"
                type={filter === 'attention' ? 'primary' : 'default'}
                aria-pressed={filter === 'attention'}
                disabled={summary.attention === 0 && filter !== 'attention'}
                onClick={() => toggleFilter('attention')}
              >
                Chỉ xem dòng cần xem lại ({summary.attention})
              </Button>
              {emptyFields.length > 0 && (
                <Button size="small" type="text" onClick={() => setShowEmptyColumns((value) => !value)}>
                  {showEmptyColumns ? `Ẩn ${emptyFields.length} cột trống` : `Hiện ${emptyFields.length} cột trống`}
                </Button>
              )}
              <span className="ml-auto inline-flex">
                <Button size="small" type="text" onClick={() => selectAll(true)} disabled={importing}>
                  Chọn tất cả
                </Button>
                <Button size="small" type="text" onClick={() => selectAll(false)} disabled={importing}>
                  Bỏ chọn tất cả
                </Button>
              </span>
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
              <span>Bấm vào ô để sửa (Enter lưu, Esc bỏ); bỏ tích đầu dòng để không nhập dòng đó.</span>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="inline-block h-3 w-4 rounded-sm bg-yellow-100" />
                AI chưa chắc chắn (dưới {Math.round(LOW_CONFIDENCE_THRESHOLD * 100)}%)
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="inline-block h-3 w-4 rounded-sm bg-red-50 ring-1 ring-red-300" />
                Phải sửa
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="inline-block h-3 w-4 rounded-sm bg-purple-50 ring-1 ring-purple-300" />
                Mã trùng
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="font-medium text-blue-700">Abc</span>
                Bạn đã sửa
              </span>
            </div>

            <AiImportPreviewTable
              columns={columns}
              rows={visibleRows}
              errorsByRow={errorsByRow}
              assessments={assessments}
              identityField={identityField}
              hiddenFields={hiddenFields}
              selectedKeys={rows.filter((row) => !deselected.has(row.row)).map((row) => row.row)}
              method={extraction.method}
              disabled={importing}
              onSelectionChange={changeSelection}
              onCellChange={changeCell}
              onDeleteRow={deleteRow}
            />

            <div className="ai-preview-footer flex flex-wrap items-center justify-end gap-3">
              <div className="mr-auto text-sm" aria-live="polite">
                {summary.blocking > 0 ? (
                  <span className="text-red-600">
                    Còn {summary.blocking} dòng lỗi hoặc trùng mã: sửa ô được đánh dấu hoặc bỏ chọn trước khi nhập.
                  </span>
                ) : (
                  <span className="text-slate-600">
                    Sẽ nhập <strong className="text-slate-900">{selectedRows.length}</strong> dòng
                    {summary.counts.excluded > 0 ? `, không gửi ${summary.counts.excluded} dòng đã bỏ chọn` : ''}.
                  </span>
                )}
              </div>
              {summary.blocking > 0 && (
                <Button onClick={excludeBlocking} disabled={importing}>
                  Bỏ chọn các dòng lỗi
                </Button>
              )}
              <Button
                type="primary"
                size="large"
                icon={<CloudUploadOutlined />}
                loading={importing}
                disabled={selectedRows.length === 0 || expired || summary.blocking > 0}
                onClick={confirmImport}
                data-write-action
              >
                {importing ? 'Đang nhập...' : `Nhập ${selectedRows.length} dòng`}
              </Button>
            </div>
          </div>
        </Card>
      )}

      {!restoring && stage === 'result' && outcome && dataType && (
        <Card title="Bước 3: Kết quả nhập">
          <AiImportResultView
            dataType={dataType}
            columns={columns}
            outcome={outcome}
            onFixRemaining={backToPreview}
            onNewFile={resetToUpload}
          />
        </Card>
      )}

      <Modal
        open={blocker.state === 'blocked'}
        title="Rời trang nhập dữ liệu?"
        okText="Rời trang"
        cancelText="Ở lại"
        onOk={() => {
          abortRef.current?.abort()
          blocker.proceed?.()
        }}
        onCancel={() => blocker.reset?.()}
      >
        {extracting
          ? 'File đang được trích xuất. Rời trang sẽ dừng chờ kết quả (máy chủ vẫn có thể tính một lượt AI).'
          : 'Các chỉnh sửa trong bảng xem trước chưa được nhập sẽ mất.'}
      </Modal>
    </div>
  )
}
