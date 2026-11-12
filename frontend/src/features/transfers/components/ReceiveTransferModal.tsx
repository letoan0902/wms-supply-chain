import {
  BarcodeOutlined,
  CameraOutlined,
  CheckCircleOutlined,
  EnvironmentOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  ReloadOutlined,
  ShopOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Divider,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { InputRef } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useMemo, useRef, useState } from 'react'
import { formatDateTime, formatNumber } from '../../../utils/format'
import { playErrorBeep, playSuccessBeep, playWarningBeep } from '../../../utils/sound'
import { fetchProducts } from '../../products/productApi'
import type { Product } from '../../products/types'
import BarcodeCameraScanner from '../../purchases/components/BarcodeCameraScanner'
import type { BinLocation } from '../../warehouses/types'
import { fetchLocations } from '../../warehouses/warehouseApi'
import type { ReceiveStockTransferPayload, StockTransfer } from '../types'

interface ReceiveRowItem {
  itemId: number
  productId: number
  skuCode: string
  productName?: string
  uom?: string
  sourceBinLocationCode?: string
  transferredQuantity: number
  receivedQuantity: number
  damagedQuantity: number
  destinationBinLocationId?: number
  notes: string
}

interface Props {
  open: boolean
  transfer: StockTransfer
  submitting: boolean
  onClose: () => void
  onSubmitReceive: (id: number, payload: ReceiveStockTransferPayload) => Promise<void>
}

const INCIDENT_PRESETS = [
  'Hư hỏng / móp méo trong quá trình vận chuyển',
  'Thiếu số lượng so với phiếu xuất điều chuyển',
  'Bao bì rách / ẩm ướt',
  'Đổ vỡ không thể nhập kho',
]

// Làm tròn 3 chữ số thập phân như cột DECIMAL(18,3) của backend. Phép trừ số thực của JS
// (ví dụ 0.07 - 0.01 = 0.060000000000000005) làm tổng thực nhận + hư hỏng gửi lên vượt SL đã xuất
// và backend từ chối phiếu nhận hợp lệ.
const round3 = (value: number) => Math.round(value * 1000) / 1000

// Số lượng còn có thể ghi nhận thực nhận cho một dòng (SL đã xuất - hư hỏng - đã nhận)
const remainingOf = (r: ReceiveRowItem) =>
  round3(r.transferredQuantity - r.damagedQuantity - r.receivedQuantity)

// Số lượng thiếu của một dòng (không âm)
const missingOf = (r: ReceiveRowItem) =>
  Math.max(0, round3(r.transferredQuantity - r.receivedQuantity - r.damagedQuantity))

export default function ReceiveTransferModal({
  open,
  transfer,
  submitting,
  onClose,
  onSubmitReceive,
}: Props) {
  const { message } = App.useApp()

  const [rows, setRows] = useState<ReceiveRowItem[]>(() =>
    (transfer.items || []).map((it) => ({
      itemId: it.id,
      productId: it.productId,
      skuCode: it.skuCode,
      productName: it.productName,
      uom: it.uom,
      sourceBinLocationCode: it.sourceBinLocationCode,
      transferredQuantity: it.transferredQuantity ?? it.quantity ?? 0,
      receivedQuantity: it.transferredQuantity ?? it.quantity ?? 0,
      damagedQuantity: 0,
      destinationBinLocationId: it.destinationBinLocationId,
      notes: '',
    }))
  )

  const [destBins, setDestBins] = useState<BinLocation[]>([])
  const [loadingBins, setLoadingBins] = useState<boolean>(true)
  const [products, setProducts] = useState<Product[]>([])

  const [bulkBinId, setBulkBinId] = useState<number | undefined>(undefined)
  const [barcodeInput, setBarcodeInput] = useState<string>('')
  const [scanStep, setScanStep] = useState<number>(1)
  const [lastScannedSku, setLastScannedSku] = useState<string | null>(null)
  const [isCameraOpen, setIsCameraOpen] = useState<boolean>(false)
  const [incidentNotes, setIncidentNotes] = useState<string>('')

  const barcodeInputRef = useRef<InputRef>(null)

  // Nạp danh sách vị trí ô/kệ tại kho nhập đích
  useEffect(() => {
    let ignore = false
    fetchLocations({ warehouseId: transfer.destinationWarehouseId, active: true })
      .then((bins) => {
        if (!ignore) {
          setDestBins(bins)
        }
      })
      .catch((err) => {
        console.error('Lỗi khi nạp vị trí ô/kệ kho đích:', err)
        if (!ignore) {
          message.error('Không thể tải danh sách vị trí ô/kệ tại kho đích')
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoadingBins(false)
        }
      })

    return () => {
      ignore = true
    }
  }, [transfer.destinationWarehouseId, message])

  // Nạp danh mục sản phẩm để hỗ trợ đối chiếu Barcode khi quét mã
  useEffect(() => {
    let ignore = false
    fetchProducts({ size: 200, active: true })
      .then((res) => {
        if (!ignore) {
          setProducts(res.content || [])
        }
      })
      .catch((err) => console.error('Lỗi nạp danh mục sản phẩm phục vụ quét mã:', err))

    return () => {
      ignore = true
    }
  }, [])

  // Danh sách vị trí khả dụng (active và không bảo trì)
  const availableBins = useMemo(
    () => destBins.filter((b) => b.active && b.status !== 'MAINTENANCE'),
    [destBins]
  )

  // Tổng hợp số liệu kiểm đếm theo thời gian thực
  const summary = useMemo(() => {
    let totalTransferred = 0
    let totalReceived = 0
    let totalDamaged = 0
    let totalMissing = 0

    for (const r of rows) {
      totalTransferred += r.transferredQuantity
      totalReceived += r.receivedQuantity
      totalDamaged += r.damagedQuantity
      totalMissing += missingOf(r)
    }

    totalTransferred = round3(totalTransferred)
    totalReceived = round3(totalReceived)
    totalDamaged = round3(totalDamaged)
    totalMissing = round3(totalMissing)

    const hasDiscrepancy = totalDamaged > 0 || totalMissing > 0
    return {
      totalTransferred,
      totalReceived,
      totalDamaged,
      totalMissing,
      hasDiscrepancy,
    }
  }, [rows])

  // Cập nhật số lượng thực nhận với tự động ràng buộc tổng <= SL đã xuất
  const handleReceivedChange = (itemId: number, value: number | null) => {
    const raw = round3(Math.max(0, Number(value ?? 0)))
    setRows((prev) =>
      prev.map((r) => {
        if (r.itemId !== itemId) return r
        const clampedReceived = Math.min(raw, r.transferredQuantity)
        const maxDamaged = Math.max(0, round3(r.transferredQuantity - clampedReceived))
        const nextDamaged = Math.min(r.damagedQuantity, maxDamaged)
        return {
          ...r,
          receivedQuantity: clampedReceived,
          damagedQuantity: nextDamaged,
        }
      })
    )
  }

  // Cập nhật số lượng hư hỏng / mất mát
  const handleDamagedChange = (itemId: number, value: number | null) => {
    const raw = round3(Math.max(0, Number(value ?? 0)))
    setRows((prev) =>
      prev.map((r) => {
        if (r.itemId !== itemId) return r
        const clampedDamaged = Math.min(raw, r.transferredQuantity)
        const maxReceived = Math.max(0, round3(r.transferredQuantity - clampedDamaged))
        const nextReceived = Math.min(r.receivedQuantity, maxReceived)
        return {
          ...r,
          damagedQuantity: clampedDamaged,
          receivedQuantity: nextReceived,
        }
      })
    )
  }

  // Cập nhật vị trí hoặc ghi chú dòng
  const handleUpdateField = (
    itemId: number,
    field: 'destinationBinLocationId' | 'notes',
    value: number | string | undefined
  ) => {
    setRows((prev) =>
      prev.map((r) => (r.itemId === itemId ? { ...r, [field]: value } : r))
    )
  }

  // Điền nhận đủ 100% tất cả mặt hàng
  const handleFillAllFull = () => {
    setRows((prev) =>
      prev.map((r) => ({
        ...r,
        receivedQuantity: r.transferredQuantity,
        damagedQuantity: 0,
      }))
    )
    message.info('Đã đặt toàn bộ số lượng thực nhận khớp với số lượng đã xuất')
  }

  // Đặt lại thực nhận về 0 để kiểm đếm lại / quét mã từng sản phẩm
  const handleResetToZero = () => {
    setRows((prev) =>
      prev.map((r) => ({
        ...r,
        receivedQuantity: 0,
        damagedQuantity: 0,
      }))
    )
    setLastScannedSku(null)
    setTimeout(() => barcodeInputRef.current?.focus(), 100)
    message.info('Đã đưa số lượng thực nhận về 0. Sẵn sàng quét mã kiểm đếm!')
  }

  // Áp dụng cùng một vị trí kho đích cho tất cả dòng
  const handleApplyBulkBin = () => {
    if (!bulkBinId) {
      message.warning('Vui lòng chọn vị trí ô/kệ trước khi áp dụng hàng loạt')
      return
    }
    setRows((prev) =>
      prev.map((r) => ({
        ...r,
        destinationBinLocationId: bulkBinId,
      }))
    )
    const selectedBin = availableBins.find((b) => b.id === bulkBinId)
    message.success(`Đã gán vị trí ${selectedBin?.code || ''} cho toàn bộ danh sách mặt hàng`)
  }

  // Xử lý quét mã vạch hoặc nhập mã SKU
  const handleBarcodeScan = (rawCode: string) => {
    const code = rawCode.trim()
    if (!code) return

    const lower = code.toLowerCase()
    let candidates = rows.filter((r) => r.skuCode.toLowerCase() === lower)

    if (candidates.length === 0) {
      const matchedProduct = products.find(
        (p) =>
          (p.barcode && p.barcode.toLowerCase() === lower) ||
          p.sku.toLowerCase() === lower
      )
      if (matchedProduct) {
        candidates = rows.filter((r) => r.productId === matchedProduct.id)
      }
    }

    // Một lệnh có thể có nhiều dòng cùng SKU (xuất từ nhiều ô/kệ): ưu tiên dòng còn thiếu số lượng
    const matched = candidates.find((r) => remainingOf(r) > 0) ?? candidates[0]

    setBarcodeInput('')

    if (!matched) {
      playErrorBeep()
      message.error(`Mã "${code}" không thuộc lệnh điều chuyển này`)
      return
    }

    const maxAllowed = round3(matched.transferredQuantity - matched.damagedQuantity)
    const room = remainingOf(matched)

    if (room <= 0) {
      playWarningBeep()
      message.warning(
        `Sản phẩm ${matched.skuCode} đã nhận đủ số lượng tối đa (${formatNumber(maxAllowed)})`
      )
      return
    }

    const added = Math.min(scanStep, room)
    // Tính lại trên state mới nhất để quét liên tục không đẩy dòng vượt SL đã xuất
    setRows((prev) =>
      prev.map((r) =>
        r.itemId === matched.itemId
          ? {
              ...r,
              receivedQuantity: round3(
                r.receivedQuantity + Math.min(scanStep, Math.max(0, remainingOf(r)))
              ),
            }
          : r
      )
    )
    setLastScannedSku(matched.skuCode)
    playSuccessBeep()
    message.success(`+${added} ${matched.skuCode} (${matched.productName || ''})`)
  }

  // Chèn nhanh mẫu nội dung sự cố vào biên bản
  const handleAppendIncidentPreset = (preset: string) => {
    setIncidentNotes((prev) => {
      const trimmed = prev.trim()
      if (!trimmed) return preset
      if (trimmed.includes(preset)) return prev
      return `${trimmed}\n- ${preset}`
    })
  }

  // Tự động tổng hợp bảng chi tiết chênh lệch vào biên bản sự cố
  const handleAutoBuildIncidentReport = () => {
    const discrepancyLines = rows
      .map((r) => {
        const missing = missingOf(r)
        if (r.damagedQuantity === 0 && missing === 0) return null
        const parts: string[] = [
          `Xuất ${formatNumber(r.transferredQuantity)}`,
          `Thực nhận ${formatNumber(r.receivedQuantity)}`,
        ]
        if (r.damagedQuantity > 0) parts.push(`Hư hỏng ${formatNumber(r.damagedQuantity)}`)
        if (missing > 0) parts.push(`Thiếu ${formatNumber(missing)}`)
        const noteSuffix = r.notes.trim() ? ` — ${r.notes.trim()}` : ''
        return `- ${r.skuCode}${r.productName ? ` (${r.productName})` : ''}: ${parts.join(', ')}${noteSuffix}`
      })
      .filter((line): line is string => line !== null)

    if (discrepancyLines.length === 0) {
      message.info('Hiện tại không có mặt hàng nào bị thiếu hoặc hư hỏng')
      return
    }

    const reportText = `Biên bản kiểm đếm thực tế tại ${transfer.destinationWarehouseName}:\n${discrepancyLines.join('\n')}`
    setIncidentNotes(reportText)
    message.success('Đã tổng hợp chi tiết thiếu/hỏng vào nội dung biên bản')
  }

  // Kiểm tra dữ liệu và gửi yêu cầu xác nhận nhận hàng
  const handleSubmit = async () => {
    if (rows.length === 0) {
      Modal.error({
        title: 'Danh sách trống',
        content: 'Lệnh điều chuyển không có mặt hàng nào để tiếp nhận.',
      })
      return
    }

    const missingBinRow = rows.find(
      (r) => r.receivedQuantity > 0 && !r.destinationBinLocationId
    )
    if (missingBinRow) {
      Modal.error({
        title: 'Chưa chọn vị trí lưu kho',
        content: `Vui lòng chọn vị trí ô/kệ tại kho đích cho sản phẩm ${missingBinRow.skuCode}.`,
      })
      return
    }

    const overRow = rows.find(
      (r) => round3(r.receivedQuantity + r.damagedQuantity) > r.transferredQuantity
    )
    if (overRow) {
      Modal.error({
        title: 'Số lượng vượt quá lượng đã xuất',
        content: `Tổng thực nhận và hư hỏng của sản phẩm ${overRow.skuCode} vượt quá số lượng đã xuất (${formatNumber(overRow.transferredQuantity)}).`,
      })
      return
    }

    if (summary.hasDiscrepancy && !incidentNotes.trim()) {
      Modal.error({
        title: 'Bắt buộc nhập biên bản sự cố',
        content:
          'Có mặt hàng bị thiếu hoặc hư hỏng khi kiểm đếm. Vui lòng nhập nội dung biên bản sự cố để ghi nhận đối soát.',
      })
      return
    }

    const payload: ReceiveStockTransferPayload = {
      items: rows.map((r) => ({
        itemId: r.itemId,
        receivedQuantity: r.receivedQuantity,
        damagedQuantity: r.damagedQuantity,
        destinationBinLocationId:
          r.receivedQuantity > 0 ? r.destinationBinLocationId : undefined,
        notes: r.notes.trim() || undefined,
      })),
      notes: incidentNotes.trim() || undefined,
    }

    await onSubmitReceive(transfer.id, payload)
  }

  const columns: ColumnsType<ReceiveRowItem> = [
    {
      title: 'STT',
      width: 48,
      align: 'center',
      render: (_: unknown, __: unknown, idx: number) => idx + 1,
    },
    {
      title: 'Sản phẩm / SKU',
      key: 'product',
      width: 220,
      render: (_, record) => {
        const isJustScanned = lastScannedSku === record.skuCode
        return (
          <div className={isJustScanned ? 'bg-emerald-50 p-1 rounded border border-emerald-200' : ''}>
            <div className="font-mono font-semibold text-gray-800">{record.skuCode}</div>
            {record.productName && (
              <div className="text-xs text-gray-500 line-clamp-2">{record.productName}</div>
            )}
            <div className="text-[11px] text-gray-400 mt-0.5">
              ĐVT: {record.uom || '—'}
              {record.sourceBinLocationCode ? ` • Xuất từ: ${record.sourceBinLocationCode}` : ''}
            </div>
          </div>
        )
      },
    },
    {
      title: 'SL đã xuất',
      dataIndex: 'transferredQuantity',
      key: 'transferredQuantity',
      align: 'right',
      width: 95,
      render: (val: number) => (
        <span className="font-semibold text-blue-600">{formatNumber(val)}</span>
      ),
    },
    {
      title: 'SL thực nhận',
      key: 'receivedQuantity',
      width: 120,
      render: (_, record) => (
        <InputNumber
          min={0}
          max={record.transferredQuantity}
          precision={3}
          value={record.receivedQuantity}
          onChange={(val) => handleReceivedChange(record.itemId, val)}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'SL hư hỏng',
      key: 'damagedQuantity',
      width: 115,
      render: (_, record) => (
        <InputNumber
          min={0}
          max={record.transferredQuantity}
          precision={3}
          status={record.damagedQuantity > 0 ? 'warning' : undefined}
          value={record.damagedQuantity}
          onChange={(val) => handleDamagedChange(record.itemId, val)}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'Tình trạng',
      key: 'diff',
      width: 125,
      render: (_, record) => {
        const missing = missingOf(record)
        if (record.damagedQuantity === 0 && missing === 0) {
          return (
            <Tag color="success" icon={<CheckCircleOutlined />}>
              Đủ hàng
            </Tag>
          )
        }
        return (
          <div className="space-y-1">
            {record.damagedQuantity > 0 && (
              <Tag color="error" className="block w-fit">
                Hỏng: {formatNumber(record.damagedQuantity)}
              </Tag>
            )}
            {missing > 0 && (
              <Tag color="warning" className="block w-fit">
                Thiếu: {formatNumber(missing)}
              </Tag>
            )}
          </div>
        )
      },
    },
    {
      title: 'Vị trí cất hàng (Kho đích)',
      key: 'destinationBinLocationId',
      width: 210,
      render: (_, record) => {
        const needsBin = record.receivedQuantity > 0
        const isMissingBin = needsBin && !record.destinationBinLocationId
        return (
          <div>
            <Select
              showSearch
              allowClear
              loading={loadingBins}
              disabled={!needsBin}
              placeholder={needsBin ? 'Chọn ô/kệ nhập kho' : 'Không nhập kho'}
              status={isMissingBin ? 'error' : undefined}
              value={needsBin ? record.destinationBinLocationId : undefined}
              optionFilterProp="label"
              style={{ width: '100%' }}
              onChange={(val) =>
                handleUpdateField(record.itemId, 'destinationBinLocationId', val)
              }
              options={availableBins.map((b) => ({
                value: b.id,
                label: `${b.code} (${b.zone} - ${b.rack})`,
              }))}
            />
            {isMissingBin && (
              <span className="text-[11px] text-red-500 block mt-0.5">
                Bắt buộc chọn vị trí
              </span>
            )}
          </div>
        )
      },
    },
    {
      title: 'Ghi chú dòng / Sự cố',
      key: 'notes',
      width: 185,
      render: (_, record) => {
        const hasLineIssue =
          record.damagedQuantity > 0 ||
          record.receivedQuantity < record.transferredQuantity
        return (
          <Input
            placeholder={hasLineIssue ? 'Mô tả lý do thiếu/hỏng...' : 'Ghi chú (tùy chọn)'}
            status={hasLineIssue && !record.notes.trim() ? 'warning' : undefined}
            value={record.notes}
            onChange={(e) => handleUpdateField(record.itemId, 'notes', e.target.value)}
          />
        )
      },
    },
  ]

  return (
    <>
      <Modal
        title={
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-lg font-bold text-gray-800">
              Xác nhận nhận hàng điều chuyển
            </span>
            <Tag color="blue" className="font-mono text-sm px-2 py-0.5">
              {transfer.transferNumber}
            </Tag>
            {summary.hasDiscrepancy ? (
              <Tag color="warning" icon={<WarningOutlined />}>
                Có thiếu / hư hỏng
              </Tag>
            ) : (
              <Tag color="success" icon={<CheckCircleOutlined />}>
                Đủ hàng
              </Tag>
            )}
          </div>
        }
        open={open}
        width={1220}
        onCancel={onClose}
        maskClosable={!submitting}
        footer={[
          <Button key="close" onClick={onClose} disabled={submitting}>
            Đóng
          </Button>,
          <Button
            key="submit"
            type="primary"
            danger={summary.hasDiscrepancy}
            loading={submitting}
            icon={summary.hasDiscrepancy ? <FileTextOutlined /> : <CheckCircleOutlined />}
            onClick={handleSubmit}
          >
            {summary.hasDiscrepancy
              ? 'Xác nhận nhận một phần & Lưu biên bản'
              : 'Xác nhận đã nhận đủ hàng'}
          </Button>,
        ]}
        destroyOnClose
      >
        <div className="space-y-4 mt-3">
          {/* Thông tin tuyến điều chuyển */}
          <Card size="small" className="bg-slate-50 border-slate-200">
            <Row gutter={[16, 8]} align="middle">
              <Col xs={24} md={8}>
                <div className="text-xs text-gray-500 flex items-center gap-1">
                  <ShopOutlined /> Kho xuất nguồn
                </div>
                <div className="font-semibold text-gray-800">
                  {transfer.sourceWarehouseName}{' '}
                  <span className="text-xs font-mono text-gray-500">
                    ({transfer.sourceWarehouseCode})
                  </span>
                </div>
              </Col>
              <Col xs={24} md={8}>
                <div className="text-xs text-green-600 font-medium flex items-center gap-1">
                  <EnvironmentOutlined /> Kho nhận đích
                </div>
                <div className="font-bold text-green-700">
                  {transfer.destinationWarehouseName}{' '}
                  <span className="text-xs font-mono text-green-600">
                    ({transfer.destinationWarehouseCode})
                  </span>
                </div>
              </Col>
              <Col xs={24} md={8}>
                <div className="text-xs text-gray-500">Xuất chuyển bởi / Thời gian</div>
                <div className="text-sm text-gray-700">
                  <strong>{transfer.approvedBy || 'Thủ kho nguồn'}</strong> •{' '}
                  {formatDateTime(transfer.transferDate || transfer.approvedAt)}
                </div>
              </Col>
            </Row>
          </Card>

          {/* Thanh công cụ kiểm đếm nhanh & Quét mã vạch */}
          <Card size="small" className="border-blue-100 bg-blue-50/30">
            <Row gutter={[12, 12]} align="middle">
              <Col xs={24} lg={10}>
                <Space.Compact style={{ width: '100%' }}>
                  <Input
                    ref={barcodeInputRef}
                    prefix={<BarcodeOutlined className="text-blue-500" />}
                    placeholder="Quét mã vạch hoặc nhập mã SKU + Enter..."
                    value={barcodeInput}
                    onChange={(e) => setBarcodeInput(e.target.value)}
                    onPressEnter={() => handleBarcodeScan(barcodeInput)}
                    allowClear
                  />
                  <Select
                    value={scanStep}
                    onChange={setScanStep}
                    style={{ width: 80 }}
                    options={[
                      { value: 1, label: '+1' },
                      { value: 5, label: '+5' },
                      { value: 10, label: '+10' },
                    ]}
                  />
                  <Tooltip title="Mở camera quét mã vạch">
                    <Button
                      icon={<CameraOutlined />}
                      onClick={() => setIsCameraOpen(true)}
                    >
                      Camera
                    </Button>
                  </Tooltip>
                </Space.Compact>
              </Col>

              <Col xs={24} sm={12} lg={8}>
                <Space.Compact style={{ width: '100%' }}>
                  <Select
                    showSearch
                    allowClear
                    loading={loadingBins}
                    placeholder="Chọn vị trí ô/kệ mặc định..."
                    value={bulkBinId}
                    onChange={setBulkBinId}
                    optionFilterProp="label"
                    style={{ width: '100%' }}
                    options={availableBins.map((b) => ({
                      value: b.id,
                      label: `${b.code} (${b.zone} - ${b.rack})`,
                    }))}
                  />
                  <Button type="default" onClick={handleApplyBulkBin}>
                    Áp dụng tất cả
                  </Button>
                </Space.Compact>
              </Col>

              <Col xs={24} sm={12} lg={6} className="flex justify-end gap-2">
                <Tooltip title="Đặt toàn bộ SL thực nhận bằng SL đã xuất">
                  <Button
                    size="small"
                    type="primary"
                    ghost
                    icon={<CheckCircleOutlined />}
                    onClick={handleFillAllFull}
                  >
                    Nhận đủ 100%
                  </Button>
                </Tooltip>
                <Tooltip title="Đưa thực nhận về 0 để kiểm đếm hoặc quét mã từng món">
                  <Button
                    size="small"
                    icon={<ReloadOutlined />}
                    onClick={handleResetToZero}
                  >
                    Đặt về 0
                  </Button>
                </Tooltip>
              </Col>
            </Row>
          </Card>

          {/* Bảng kiểm đếm từng mặt hàng */}
          <Table<ReceiveRowItem>
            rowKey="itemId"
            columns={columns}
            dataSource={rows}
            pagination={false}
            size="small"
            bordered
            scroll={{ x: 1050, y: 340 }}
          />

          {/* Thống kê tổng hợp kiểm đếm */}
          <Row gutter={[12, 12]}>
            <Col xs={12} sm={6}>
              <Card size="small" className="text-center bg-gray-50">
                <Statistic
                  title="Tổng SL đã xuất"
                  value={formatNumber(summary.totalTransferred)}
                  valueStyle={{ fontSize: 18, fontWeight: 600, color: '#1677ff' }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card size="small" className="text-center bg-green-50/50 border-green-200">
                <Statistic
                  title="Thực nhận nhập kho"
                  value={formatNumber(summary.totalReceived)}
                  valueStyle={{ fontSize: 18, fontWeight: 600, color: '#52c41a' }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card
                size="small"
                className={`text-center ${summary.totalDamaged > 0 ? 'bg-red-50 border-red-200' : 'bg-gray-50'}`}
              >
                <Statistic
                  title="Hàng hư hỏng"
                  value={formatNumber(summary.totalDamaged)}
                  valueStyle={{
                    fontSize: 18,
                    fontWeight: 600,
                    color: summary.totalDamaged > 0 ? '#ff4d4f' : '#8c8c8c',
                  }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card
                size="small"
                className={`text-center ${summary.totalMissing > 0 ? 'bg-orange-50 border-orange-200' : 'bg-gray-50'}`}
              >
                <Statistic
                  title="Hàng thiếu hụt"
                  value={formatNumber(summary.totalMissing)}
                  valueStyle={{
                    fontSize: 18,
                    fontWeight: 600,
                    color: summary.totalMissing > 0 ? '#fa8c16' : '#8c8c8c',
                  }}
                />
              </Card>
            </Col>
          </Row>

          {/* Cảnh báo trạng thái kết quả & Khối Lập Biên bản Sự cố */}
          {summary.hasDiscrepancy ? (
            <div className="space-y-3 p-4 rounded-xl border border-amber-300 bg-amber-50/50">
              <Alert
                type="warning"
                showIcon
                icon={<ExclamationCircleOutlined />}
                message="Phát hiện chênh lệch hàng hóa khi kiểm đếm tại kho đích"
                description={`Tổng thiếu hụt: ${formatNumber(summary.totalMissing)}, Tổng hư hỏng: ${formatNumber(summary.totalDamaged)}. Lệnh điều chuyển sẽ kết thúc ở trạng thái "Nhận một phần" kèm biên bản sự cố để đối soát.`}
              />

              <Divider orientation="left" className="!my-2 text-sm font-semibold text-amber-800">
                Biên bản sự cố thiếu / hỏng hàng hóa (Bắt buộc)
              </Divider>

              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="text-xs text-gray-600 mr-1">Chọn nhanh nguyên nhân:</span>
                  {INCIDENT_PRESETS.map((preset) => (
                    <Tag
                      key={preset}
                      color="orange"
                      className="cursor-pointer hover:opacity-80"
                      onClick={() => handleAppendIncidentPreset(preset)}
                    >
                      + {preset}
                    </Tag>
                  ))}
                </div>

                <Button
                  size="small"
                  icon={<FileTextOutlined />}
                  onClick={handleAutoBuildIncidentReport}
                >
                  Tổng hợp chi tiết chênh lệch
                </Button>
              </div>

              <Input.TextArea
                rows={3}
                status={!incidentNotes.trim() ? 'error' : undefined}
                placeholder="Ghi rõ nguyên nhân thiếu/hỏng, tình trạng niêm phong, phương án xử lý hoặc đối chiếu vận chuyển..."
                value={incidentNotes}
                onChange={(e) => setIncidentNotes(e.target.value)}
              />
            </div>
          ) : (
            <div className="space-y-2">
              <Alert
                type="success"
                showIcon
                message="Số lượng kiểm đếm khớp 100% với phiếu xuất điều chuyển. Toàn bộ hàng đạt chuẩn sẽ được cộng vào tồn kho đích."
              />
              <Input.TextArea
                rows={2}
                placeholder="Ghi chú giao nhận hàng hóa tại kho đích (tùy chọn)..."
                value={incidentNotes}
                onChange={(e) => setIncidentNotes(e.target.value)}
              />
            </div>
          )}
        </div>
      </Modal>

      <BarcodeCameraScanner
        open={isCameraOpen}
        onClose={() => setIsCameraOpen(false)}
        onScan={handleBarcodeScan}
      />
    </>
  )
}

