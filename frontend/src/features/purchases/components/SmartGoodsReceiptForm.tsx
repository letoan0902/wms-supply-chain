import {
  BarcodeOutlined,
  CalendarOutlined,
  CameraOutlined,
  CheckCircleOutlined,
  ClearOutlined,
  EnvironmentOutlined,
  ExclamationCircleOutlined,
  FastForwardOutlined,
  InboxOutlined,
  MinusOutlined,
  PlusOutlined,
  SaveOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  App,
  Badge,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  InputNumber,
  Progress,
  Radio,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { InputRef } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useEffect, useRef, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency, formatNumber } from '../../../utils/format'
import { playErrorBeep, playSuccessBeep, playWarningBeep } from '../../../utils/sound'
import * as warehouseApi from '../../warehouses/warehouseApi'
import type { BinLocation } from '../../warehouses/types'
import * as productApi from '../../products/productApi'
import type { Product } from '../../products/types'
import * as grnApi from '../grnApi'
import type {
  CreateGrnItemRequest,
  CreateGrnRequest,
  GrnResponse,
  PoReceiptProgressResponse,
} from '../grnTypes'
import * as purchasesApi from '../purchasesApi'
import type { PurchaseOrder } from '../types'
import BarcodeCameraScanner from './BarcodeCameraScanner'
import LotExpiryPutawayModal, {
  type LotExpiryPutawayFormData,
  type LotExpiryPutawayModalItem,
} from './LotExpiryPutawayModal'

const { Title, Text } = Typography

export interface ReceiptItemRow {
  poItemId: number
  productId: number
  skuCode: string
  productName: string
  uom: string
  orderedQuantity: number
  receivedQuantity: number
  remainingQuantity: number
  unitPrice: number
  isCompleted: boolean
  barcode?: string
  manageByLot?: boolean
  manageByExpiration?: boolean
  // Editable fields for this GRN
  acceptedQuantity: number
  rejectedQuantity: number
  rejectedReason: string
  batchNumber: string
  mfgDate?: string
  expDate?: string
  locationId?: number
  locationCode?: string
  note: string
}

interface SmartGoodsReceiptFormProps {
  initialPoId?: number
  onSuccess: (created: GrnResponse) => void
  onCancel: () => void
}

export default function SmartGoodsReceiptForm({
  initialPoId,
  onSuccess,
  onCancel,
}: SmartGoodsReceiptFormProps) {
  const { message } = App.useApp()

  // Danh sách các PO hợp lệ để nhập kho (APPROVED hoặc PARTIALLY_RECEIVED)
  const [candidatePos, setCandidatePos] = useState<PurchaseOrder[]>([])
  const [loadingPos, setLoadingPos] = useState(false)
  const [selectedPoId, setSelectedPoId] = useState<number | undefined>(initialPoId)

  // Thông tin tiến độ & danh sách hàng của PO được chọn
  const [poProgress, setPoProgress] = useState<PoReceiptProgressResponse | null>(null)
  const [loadingProgress, setLoadingProgress] = useState(false)
  const [items, setItems] = useState<ReceiptItemRow[]>([])

  // Danh mục vị trí ô/kệ của kho tiếp nhận
  const [binLocations, setBinLocations] = useState<BinLocation[]>([])
  const [loadingBins, setLoadingBins] = useState(false)

  // Thông tin phiếu nhập
  const [receiptDate, setReceiptDate] = useState<string>(dayjs().format('YYYY-MM-DD'))
  const [grnNote, setGrnNote] = useState<string>('')
  const [submitting, setSubmitting] = useState(false)

  // Quét mã vạch thông minh & súng bắn mã
  const [barcodeInput, setBarcodeInput] = useState<string>('')
  const [scanStep, setScanStep] = useState<number>(1)
  const [lastScannedSku, setLastScannedSku] = useState<string | null>(null)
  const [isCameraModalOpen, setIsCameraModalOpen] = useState(false)
  const barcodeInputRef = useRef<InputRef>(null)

  // Modal Nhập thông tin Lô hàng & Hạn sử dụng (WMS-10.5)
  const [lotModalItem, setLotModalItem] = useState<LotExpiryPutawayModalItem | null>(null)
  const [isLotModalOpen, setIsLotModalOpen] = useState(false)

  // Tự động focus vào ô quét mã ngay khi vào form hoặc khi đổi PO
  useEffect(() => {
    const focusTimer = setTimeout(() => {
      barcodeInputRef.current?.focus()
    }, 150)
    return () => clearTimeout(focusTimer)
  }, [selectedPoId])

  const handleSelectPo = (val?: number) => {
    setSelectedPoId(val)
    if (!val) {
      setPoProgress(null)
      setItems([])
      setBinLocations([])
    }
  }

  // Tải danh sách đơn PO đủ điều kiện nhập kho
  useEffect(() => {
    let ignore = false
    const loadCandidatePos = async () => {
      setLoadingPos(true)
      try {
        const [approvedRes, partialRes] = await Promise.all([
          purchasesApi.fetchPurchaseOrders({ status: 'APPROVED', size: 100 }),
          purchasesApi.fetchPurchaseOrders({ status: 'PARTIALLY_RECEIVED', size: 100 }),
        ])
        if (!ignore) {
          const list = [...(approvedRes.content || []), ...(partialRes.content || [])]
          setCandidatePos(list)
        }
      } catch (err) {
        console.error('Không thể tải danh sách đơn PO chờ nhập', err)
      } finally {
        if (!ignore) {
          setLoadingPos(false)
        }
      }
    }
    void loadCandidatePos()
    return () => {
      ignore = true
    }
  }, [])

  // Tải tiến độ nhập của PO được chọn
  useEffect(() => {
    if (!selectedPoId) {
      return
    }

    let ignore = false
    const loadPoData = async () => {
      setLoadingProgress(true)
      try {
        const progress = await grnApi.fetchPoReceiptProgress(selectedPoId)
        if (ignore) return

        setPoProgress(progress)

        // Tải chi tiết sản phẩm theo từng ID của đơn PO để luôn lấy đúng cờ manageByLot/manageByExpiration
        const uniqueProductIds = Array.from(
          new Set((progress.items || []).map((it) => it.productId).filter(Boolean))
        )
        const productMap = new Map<number, Product>()
        const settledProducts = await Promise.allSettled(
          uniqueProductIds.map((id) => productApi.fetchProductById(id))
        )
        settledProducts.forEach((res) => {
          if (res.status === 'fulfilled' && res.value) {
            productMap.set(res.value.id, res.value)
          }
        })

        // Khởi tạo các dòng hàng
        const initialRows: ReceiptItemRow[] = (progress.items || []).map((it) => {
          const prod = productMap.get(it.productId)
          return {
            poItemId: it.poItemId,
            productId: it.productId,
            skuCode: it.skuCode,
            productName: it.productName,
            uom: it.uom,
            orderedQuantity: it.orderedQuantity,
            receivedQuantity: it.receivedQuantity,
            remainingQuantity: it.remainingQuantity,
            unitPrice: it.unitPrice,
            isCompleted: it.isCompleted,
            barcode: it.barcode || prod?.barcode,
            manageByLot: Boolean(prod?.manageByLot),
            manageByExpiration: Boolean(prod?.manageByExpiration),
            // Đợt nhập hiện tại: mặc định 0, chờ quét mã hoặc chỉnh tay
            acceptedQuantity: 0,
            rejectedQuantity: 0,
            rejectedReason: '',
            // Số lô, ngày SX, hạn dùng để trống: chỉ gửi khi người dùng tự nhập
            batchNumber: '',
            mfgDate: undefined,
            expDate: undefined,
            locationId: undefined,
            locationCode: undefined,
            note: '',
          }
        })

        setItems(initialRows)

        // Tải vị trí ô kệ đang hoạt động của kho
        if (progress.warehouseId) {
          setLoadingBins(true)
          try {
            const bins = await warehouseApi.fetchLocations({
              warehouseId: progress.warehouseId,
              active: true,
            })
            if (!ignore) {
              setBinLocations((bins || []).filter((b) => b.active))
            }
          } catch (err) {
            console.error('Không thể tải vị trí kho', err)
          } finally {
            if (!ignore) setLoadingBins(false)
          }
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải chi tiết tiến độ đơn PO'))
        }
      } finally {
        if (!ignore) {
          setLoadingProgress(false)
        }
      }
    }

    void loadPoData()
    return () => {
      ignore = true
    }
  }, [selectedPoId, message])

  // Xử lý logic quét mã vạch (Súng bắn mã hoặc Camera)
  const processBarcodeScan = (rawCode: string) => {
    const code = rawCode.trim()
    if (!code) return

    if (!poProgress || items.length === 0) {
      playErrorBeep()
      message.warning('Vui lòng chọn Đơn mua hàng (PO) trước khi quét mã vạch!')
      setBarcodeInput('')
      barcodeInputRef.current?.focus()
      return
    }

    // Tìm kiếm sản phẩm trong danh sách dòng hàng của PO
    const normalized = code.toLowerCase()
    const targetIndex = items.findIndex(
      (it) =>
        it.skuCode.toLowerCase() === normalized ||
        (it.barcode && it.barcode.toLowerCase() === normalized) ||
        String(it.productId) === normalized ||
        String(it.poItemId) === normalized
    )

    if (targetIndex === -1) {
      // Không tìm thấy sản phẩm trong PO này
      playErrorBeep()
      message.error(`Mã vạch "${code}" không thuộc đơn hàng ${poProgress.poNumber}!`)
      setBarcodeInput('')
      barcodeInputRef.current?.focus()
      return
    }

    const targetItem = items[targetIndex]
    const newAccepted = targetItem.acceptedQuantity + scanStep
    const totalWillReceive = targetItem.receivedQuantity + newAccepted

    // Cảnh báo nếu quét vượt quá số lượng đặt
    if (totalWillReceive > targetItem.orderedQuantity) {
      playWarningBeep()
      message.warning(
        `Cảnh báo: SKU "${targetItem.skuCode}" đã quét ${newAccepted} ${targetItem.uom} (Đã nhận trước đó: ${targetItem.receivedQuantity}, Đặt: ${targetItem.orderedQuantity}). Đã vượt số lượng PO!`
      )
    } else {
      // Báo thành công chuẩn tiếng súng POS
      playSuccessBeep()
    }

    // Cập nhật tăng số lượng theo state mới nhất để hai lần quét liên tiếp không bị mất
    setItems((prev) =>
      prev.map((it) =>
        it.poItemId === targetItem.poItemId
          ? { ...it, acceptedQuantity: it.acceptedQuantity + scanStep }
          : it
      )
    )

    // Hiệu ứng highlight dòng sản phẩm vừa quét
    setLastScannedSku(targetItem.skuCode)
    setTimeout(() => {
      setLastScannedSku(null)
    }, 1500)

    // Xóa trắng ô input và giữ focus tức thì (< 100ms)
    setBarcodeInput('')
    setTimeout(() => {
      barcodeInputRef.current?.focus()
    }, 30)
  }

  // Sự kiện khi súng bắn mã bấm phím Enter
  const handleBarcodeKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      processBarcodeScan(barcodeInput)
    }
  }

  // Điền nhanh toàn bộ số lượng còn lại
  const handleFillAllRemaining = () => {
    if (items.length === 0) return
    setItems((prev) =>
      prev.map((it) => ({
        ...it,
        acceptedQuantity: Math.max(0, it.remainingQuantity),
        rejectedQuantity: 0,
      }))
    )
    playSuccessBeep()
    message.success('Đã điền nhanh toàn bộ số lượng còn lại của đơn hàng!')
    barcodeInputRef.current?.focus()
  }

  // Đặt lại toàn bộ số lượng đã quét
  const handleResetScan = () => {
    setItems((prev) =>
      prev.map((it) => ({
        ...it,
        acceptedQuantity: 0,
        rejectedQuantity: 0,
      }))
    )
    message.info('Đã đặt lại số lượng quét về 0.')
    barcodeInputRef.current?.focus()
  }

  // Cập nhật một hoặc nhiều trường của một dòng dựa trên state mới nhất (tránh ghi đè lẫn nhau)
  const updateItemFields = (index: number, patch: Partial<ReceiptItemRow>) => {
    setItems((prev) =>
      prev.map((it, i) => (i === index ? { ...it, ...patch } : it))
    )
  }

  // Thay đổi giá trị thủ công trên từng dòng
  const handleItemFieldChange = <K extends keyof ReceiptItemRow>(
    index: number,
    field: K,
    value: ReceiptItemRow[K]
  ) => {
    updateItemFields(index, { [field]: value } as Partial<ReceiptItemRow>)
  }

  // Tăng / giảm số lượng đạt bằng nút
  const handleAdjustAccepted = (index: number, delta: number) => {
    setItems((prev) =>
      prev.map((it, i) =>
        i === index
          ? { ...it, acceptedQuantity: Math.max(0, it.acceptedQuantity + delta) }
          : it
      )
    )
  }

  // Mở modal nhập thông tin Lô hàng, Hạn dùng & Vị trí cất hàng (WMS-10.5)
  const handleOpenLotModal = (index: number) => {
    const target = items[index]
    if (!target) return
    setLotModalItem({
      index,
      productId: target.productId,
      skuCode: target.skuCode,
      productName: target.productName,
      uom: target.uom,
      acceptedQuantity: target.acceptedQuantity,
      batchNumber: target.batchNumber,
      mfgDate: target.mfgDate,
      expDate: target.expDate,
      locationId: target.locationId,
      locationCode: target.locationCode,
      note: target.note,
      manageByLot: target.manageByLot,
      manageByExpiration: target.manageByExpiration,
    })
    setIsLotModalOpen(true)
  }

  // Lưu thông tin từ Modal Lô & HSD (WMS-10.5)
  const handleSaveLotModal = (index: number, data: LotExpiryPutawayFormData) => {
    updateItemFields(index, {
      batchNumber: data.batchNumber,
      mfgDate: data.mfgDate,
      expDate: data.expDate,
      locationId: data.locationId,
      locationCode: data.locationCode,
      note: data.note || '',
    })
    setIsLotModalOpen(false)
    message.success('Đã cập nhật thông tin Lô hàng, Hạn dùng & Vị trí cất hàng!')
    barcodeInputRef.current?.focus()
  }

  // Tính toán tổng số lượng và giá trị
  const totalAccepted = items.reduce((sum, it) => sum + (it.acceptedQuantity || 0), 0)
  const totalRejected = items.reduce((sum, it) => sum + (it.rejectedQuantity || 0), 0)
  const totalEstimatedAmount = items.reduce(
    (sum, it) => sum + (it.acceptedQuantity || 0) * (it.unitPrice || 0),
    0
  )

  // Kiểm tra tính hợp lệ trước khi gửi
  const handleSubmit = async () => {
    if (!selectedPoId || !poProgress) {
      message.error('Vui lòng chọn đơn mua hàng (PO)!')
      return
    }

    if (totalAccepted === 0 && totalRejected === 0) {
      playErrorBeep()
      message.error('Vui lòng quét hoặc nhập số lượng thực nhận (hoặc hàng lỗi) cho ít nhất 1 sản phẩm!')
      barcodeInputRef.current?.focus()
      return
    }

    // Kiểm tra hàng lỗi phải có lý do
    for (const it of items) {
      if (it.rejectedQuantity > 0 && !it.rejectedReason?.trim()) {
        playErrorBeep()
        message.warning(`Vui lòng nhập lý do từ chối/hỏng cho SKU "${it.skuCode}"!`)
        return
      }
    }

    // WMS-10.5: Kiểm tra thông tin Lô & HSD đối với các mặt hàng có cờ quản lý date / lot
    for (let i = 0; i < items.length; i++) {
      const it = items[i]
      if (it.acceptedQuantity > 0) {
        const isDateManaged = Boolean(it.manageByExpiration || it.manageByLot)
        if (isDateManaged) {
          if (!it.batchNumber?.trim()) {
            playErrorBeep()
            message.error(
              `Mặt hàng "${it.productName}" (${it.skuCode}) yêu cầu quản lý Số lô. Vui lòng nhập thông tin Lô!`
            )
            handleOpenLotModal(i)
            return
          }
          if (it.manageByExpiration && !it.expDate) {
            playErrorBeep()
            message.error(
              `Mặt hàng "${it.productName}" (${it.skuCode}) yêu cầu quản lý Hạn sử dụng. Vui lòng nhập Hạn dùng!`
            )
            handleOpenLotModal(i)
            return
          }
        }
        if (it.expDate && it.mfgDate && dayjs(it.expDate).isBefore(dayjs(it.mfgDate))) {
          playErrorBeep()
          message.error(
            `Hạn sử dụng không được trước ngày sản xuất (SKU: ${it.skuCode})!`
          )
          handleOpenLotModal(i)
          return
        }
        if (it.expDate && !dayjs(it.expDate).isAfter(dayjs(receiptDate || undefined), 'day')) {
          playErrorBeep()
          message.error(
            `Hạn sử dụng của lô "${it.batchNumber || it.skuCode}" phải sau ngày nhập kho (${dayjs(receiptDate).format('DD/MM/YYYY')})!`
          )
          handleOpenLotModal(i)
          return
        }
      }
    }

    // Lọc các dòng có nhận hàng (đạt hoặc hỏng)
    const activeLines: CreateGrnItemRequest[] = items
      .filter((it) => it.acceptedQuantity > 0 || it.rejectedQuantity > 0)
      .map((it) => ({
        poItemId: it.poItemId,
        acceptedQuantity: it.acceptedQuantity,
        rejectedQuantity: it.rejectedQuantity,
        rejectedReason: it.rejectedReason?.trim() || undefined,
        batchNumber: it.batchNumber?.trim() || undefined,
        mfgDate: it.mfgDate || undefined,
        expDate: it.expDate || undefined,
        locationId: it.locationId,
        locationCode: it.locationCode,
        note: it.note?.trim() || undefined,
      }))

    const payload: CreateGrnRequest = {
      poId: selectedPoId,
      receiptDate: receiptDate || dayjs().format('YYYY-MM-DD'),
      note: grnNote.trim() || undefined,
      items: activeLines,
    }

    setSubmitting(true)
    try {
      const created = await grnApi.createGoodsReceipt(payload)
      playSuccessBeep()
      message.success(`Lập phiếu nhập kho "${created.grnNumber}" thành công!`)
      onSuccess(created)
    } catch (err) {
      playErrorBeep()
      message.error(getApiErrorMessage(err, 'Không thể lập phiếu nhập kho'))
    } finally {
      setSubmitting(false)
    }
  }

  // Cấu hình bảng hiển thị chi tiết các dòng hàng
  const columns: ColumnsType<ReceiptItemRow> = [
    {
      title: 'STT',
      width: 55,
      align: 'center',
      render: (_, __, idx) => idx + 1,
    },
    {
      title: 'Mặt hàng & SKU',
      width: 240,
      render: (_, r) => {
        const isHighlighted = lastScannedSku === r.skuCode
        return (
          <div
            className={`p-1.5 rounded transition-colors duration-500 ${
              isHighlighted ? 'bg-emerald-100 ring-2 ring-emerald-500' : ''
            }`}
          >
            <div className="font-semibold text-slate-800 flex items-center gap-1.5">
              <span>{r.productName}</span>
              {isHighlighted && (
                <Tag color="success" className="animate-bounce m-0 text-[10px]">
                  + Vừa quét
                </Tag>
              )}
            </div>
            <div className="text-xs text-slate-500 flex items-center gap-2 mt-0.5">
              <span className="font-mono bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200">
                {r.skuCode}
              </span>
              <span>ĐVT: {r.uom}</span>
            </div>
            {(r.manageByExpiration || r.manageByLot) && (
              <div className="flex items-center gap-1 mt-1">
                {r.manageByExpiration && (
                  <Tag color="purple" className="text-[10px] m-0">
                    Quản lý Date
                  </Tag>
                )}
                {r.manageByLot && (
                  <Tag color="cyan" className="text-[10px] m-0">
                    Quản lý Lô
                  </Tag>
                )}
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Tiến độ PO',
      width: 170,
      render: (_, r) => {
        const totalReceivedAfterThis = r.receivedQuantity + r.acceptedQuantity
        const percent = Math.min(
          100,
          Math.round((totalReceivedAfterThis / (r.orderedQuantity || 1)) * 100)
        )
        const isOver = totalReceivedAfterThis > r.orderedQuantity

        return (
          <div className="space-y-1">
            <div className="flex justify-between text-xs text-slate-600">
              <span>
                Đã nhận: <strong>{formatNumber(r.receivedQuantity)}</strong>
              </span>
              <span>
                Đặt: <strong>{formatNumber(r.orderedQuantity)}</strong>
              </span>
            </div>
            <Progress
              percent={percent}
              size="small"
              status={isOver ? 'exception' : percent === 100 ? 'success' : 'active'}
            />
            <div className="flex justify-between text-[11px]">
              <span className="text-slate-500">
                Còn lại: <strong className="text-blue-600">{formatNumber(r.remainingQuantity)}</strong>
              </span>
              {isOver && <span className="text-rose-600 font-semibold">Vượt PO!</span>}
            </div>
          </div>
        )
      },
    },
    {
      title: (
        <span className="text-emerald-700 font-semibold flex items-center gap-1">
          <CheckCircleOutlined /> SL Đạt (Accepted)
        </span>
      ),
      width: 180,
      render: (_, r, idx) => (
        <div className="space-y-1">
          <Space size={4}>
            <Button
              size="small"
              icon={<MinusOutlined />}
              onClick={() => handleAdjustAccepted(idx, -1)}
              disabled={r.acceptedQuantity <= 0}
            />
            <InputNumber
              min={0}
              precision={2}
              value={r.acceptedQuantity}
              onChange={(val) => handleItemFieldChange(idx, 'acceptedQuantity', val || 0)}
              className="w-24 font-bold text-center text-emerald-700"
            />
            <Button
              size="small"
              icon={<PlusOutlined />}
              onClick={() => handleAdjustAccepted(idx, 1)}
            />
          </Space>
          <div className="text-[11px] text-slate-500">
            = {formatCurrency(r.acceptedQuantity * (r.unitPrice || 0))}
          </div>
        </div>
      ),
    },
    {
      title: (
        <span className="text-amber-700 font-semibold flex items-center gap-1">
          <ExclamationCircleOutlined /> SL Hỏng (Rejected)
        </span>
      ),
      width: 200,
      render: (_, r, idx) => (
        <div className="space-y-1.5">
          <InputNumber
            min={0}
            precision={2}
            placeholder="SL hỏng"
            value={r.rejectedQuantity}
            onChange={(val) => handleItemFieldChange(idx, 'rejectedQuantity', val || 0)}
            className="w-full text-amber-700"
          />
          {r.rejectedQuantity > 0 && (
            <Input
              size="small"
              placeholder="Nhập lý do hỏng / phế phẩm *"
              value={r.rejectedReason}
              onChange={(e) => handleItemFieldChange(idx, 'rejectedReason', e.target.value)}
              className="text-xs"
            />
          )}
        </div>
      ),
    },
    {
      title: 'Quản lý Lô & HSD',
      width: 250,
      render: (_, r, idx) => {
        const isDateManaged = Boolean(r.manageByExpiration || r.manageByLot)
        const isMissingRequired =
          isDateManaged &&
          r.acceptedQuantity > 0 &&
          (!r.batchNumber?.trim() || (r.manageByExpiration && !r.expDate))
        const isExpired = r.expDate && dayjs(r.expDate).isBefore(dayjs(), 'day')

        return (
          <div className="space-y-1.5 text-xs">
            <Button
              size="small"
              type={isMissingRequired ? 'primary' : 'default'}
              danger={isMissingRequired}
              icon={<CalendarOutlined />}
              onClick={() => handleOpenLotModal(idx)}
              className="w-full text-xs font-semibold flex items-center justify-center gap-1 shadow-2xs"
            >
              {isMissingRequired
                ? 'Nhập Lô & HSD ngay *'
                : r.batchNumber || r.expDate
                ? 'Sửa Lô / HSD / Vị trí'
                : 'Nhập Lô & HSD'}
            </Button>

            <div className="flex flex-wrap items-center gap-1">
              {r.batchNumber ? (
                <Tag color="blue" className="font-mono text-[11px] m-0">
                  Lô: {r.batchNumber}
                </Tag>
              ) : isDateManaged ? (
                <Tag color="default" className="text-[10px] m-0 text-slate-400">
                  Chưa có lô
                </Tag>
              ) : null}

              {r.expDate ? (
                <Tag color={isExpired ? 'error' : 'purple'} className="text-[11px] m-0">
                  HSD: {dayjs(r.expDate).format('DD/MM/YYYY')}
                </Tag>
              ) : isDateManaged ? (
                <Tag color="default" className="text-[10px] m-0 text-slate-400">
                  Chưa có HSD
                </Tag>
              ) : null}
            </div>

            {isExpired && (
              <Tag color="error" className="m-0 text-[10px] flex items-center gap-1">
                <WarningOutlined /> Lô đã hết hạn!
              </Tag>
            )}
            {isMissingRequired && (
              <div className="text-[10px] text-rose-600 font-medium">
                * Bắt buộc nhập Lô & HSD khi nhận hàng
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Vị trí cất hàng',
      width: 200,
      render: (_, r, idx) => (
        <div className="space-y-1">
          <Select
            size="small"
            placeholder="Chọn vị trí ô/kệ"
            value={r.locationId}
            onChange={(val) => {
              // Nhãn hiển thị có thêm tên khu nên lấy mã vị trí từ danh sách ô kệ, không lấy từ nhãn
              const selectedBin = val != null ? binLocations.find((b) => b.id === val) : undefined
              updateItemFields(idx, {
                locationId: val ?? undefined,
                locationCode: selectedBin?.code,
              })
            }}
            allowClear
            showSearch
            loading={loadingBins}
            className="w-full text-xs"
            options={binLocations.map((b) => ({
              value: b.id,
              label: `${b.code} (Khu ${b.zone || '-'})`,
            }))}
          />
          {r.locationCode && (
            <div className="text-[11px] text-emerald-700 flex items-center gap-1">
              <EnvironmentOutlined />
              <span>
                Ô lưu cất: <strong>{r.locationCode}</strong>
              </span>
            </div>
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-5">
      {/* Header Form */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 border-b border-slate-200 pb-4">
        <div>
          <Title level={4} className="!mb-1 flex items-center gap-2 text-slate-800">
            <BarcodeOutlined className="text-emerald-600" />
            <span>Form Lập Phiếu Nhập Kho Thông Minh (Quét mã vạch cộng dồn SL)</span>
          </Title>
          <Text type="secondary" className="text-xs">
            Hỗ trợ súng bắn mã vạch quét liên tục (&lt; 100ms) tự động tích cộng dồn, phát âm thanh BEEP, quản lý Lô & HSD (FR07, FR11, FR12)
          </Text>
        </div>

        <Space wrap>
          <Button onClick={onCancel}>Hủy bỏ</Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={submitting}
            onClick={handleSubmit}
            className="bg-emerald-600 hover:bg-emerald-700 font-semibold"
          >
            Xác nhận Nhập kho (GRN)
          </Button>
        </Space>
      </div>

      {/* Thông tin Đơn mua hàng PO & Kho tiếp nhận */}
      <Card size="small" className="bg-slate-50 border-slate-200">
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} md={10}>
            <label className="text-xs font-semibold text-slate-700 block mb-1">
              Chọn Đơn Mua Hàng (PO đã duyệt) <span className="text-rose-500">*</span>
            </label>
            <Select
              placeholder="Chọn PO đã duyệt hoặc đang nhập dở..."
              value={selectedPoId}
              onChange={(val) => handleSelectPo(val)}
              loading={loadingPos}
              showSearch
              filterOption={(input, opt) =>
                String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              className="w-full"
              options={candidatePos.map((po) => ({
                value: po.id,
                label: `[${po.poNumber}] - ${po.supplierName || 'NCC'} (Kho: ${
                  po.warehouseName || 'Chưa gán'
                }) - ${po.status}`,
              }))}
            />
          </Col>

          <Col xs={12} md={5}>
            <label className="text-xs font-semibold text-slate-700 block mb-1">
              Ngày nhập kho thực tế
            </label>
            <DatePicker
              format="DD/MM/YYYY"
              value={receiptDate ? dayjs(receiptDate) : null}
              onChange={(d) => setReceiptDate(d ? d.format('YYYY-MM-DD') : '')}
              className="w-full"
            />
          </Col>

          <Col xs={12} md={9}>
            <label className="text-xs font-semibold text-slate-700 block mb-1">
              Ghi chú lần nhập hàng
            </label>
            <Input
              placeholder="Ví dụ: Nhập xe container đợt 1, kiện hàng nguyên vẹn..."
              value={grnNote}
              onChange={(e) => setGrnNote(e.target.value)}
              maxLength={500}
            />
          </Col>
        </Row>

        {poProgress && (
          <div className="mt-3 pt-3 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-600">
            <div>
              <strong>Nhà cung cấp:</strong> {poProgress.supplierName || '---'}
            </div>
            <div>
              <strong>Kho nhận:</strong> {poProgress.warehouseName || `Kho #${poProgress.warehouseId}`}
            </div>
            <div>
              <strong>Ngày đặt PO:</strong> {poProgress.orderDate}
            </div>
            <div>
              <strong>Tổng đặt / Đã nhận:</strong>{' '}
              <span className="font-semibold text-slate-800">
                {formatNumber(poProgress.totalOrderedQuantity)} / {formatNumber(poProgress.totalReceivedQuantity)}
              </span>
            </div>
            <div>
              <strong>Còn lại cần nhập:</strong>{' '}
              <span className="font-bold text-blue-600">
                {formatNumber(poProgress.totalRemainingQuantity)}
              </span>
            </div>
          </div>
        )}
      </Card>

      {/* KHU VỰC QUÉT MÃ VẠCH THÔNG MINH (SMART SCAN ZONE) */}
      <Card
        className="border-2 border-emerald-400 bg-linear-to-r from-emerald-50/60 to-blue-50/40 shadow-sm"
        bodyStyle={{ padding: '16px 20px' }}
      >
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-1.5">
              <BarcodeOutlined className="text-emerald-700 text-lg animate-pulse" />
              <span className="font-bold text-slate-800 text-sm">
                Ô Quét Mã Vạch Thông Minh (Tự động nhận diện súng bắn mã &lt; 100ms)
              </span>
              <Tag color="green" className="text-[10px] m-0">
                Auto-Focus
              </Tag>
              <Tag color="blue" className="text-[10px] m-0">
                Sound Beep ON
              </Tag>
            </div>

            <div className="flex items-center gap-2">
              <Input
                ref={barcodeInputRef}
                size="large"
                prefix={<BarcodeOutlined className="text-slate-400 text-xl mr-1" />}
                placeholder="Bắn mã vạch SKU / Barcode (Enter để cộng dồn SL)..."
                value={barcodeInput}
                onChange={(e) => setBarcodeInput(e.target.value)}
                onKeyDown={handleBarcodeKeyDown}
                className="font-mono text-base font-semibold border-emerald-400 focus:border-emerald-600"
                disabled={!selectedPoId}
                autoFocus
              />

              <Button
                size="large"
                icon={<CameraOutlined />}
                onClick={() => setIsCameraModalOpen(true)}
                disabled={!selectedPoId}
                className="hover:border-blue-500 hover:text-blue-600"
              >
                Camera quét
              </Button>
            </div>
          </div>

          <div className="flex flex-wrap md:flex-col items-start md:items-end gap-2 text-xs">
            <div className="flex items-center gap-1.5">
              <span className="text-slate-500">Mỗi lần quét cộng:</span>
              <Radio.Group
                size="small"
                value={scanStep}
                onChange={(e) => setScanStep(e.target.value)}
                buttonStyle="solid"
              >
                <Radio.Button value={1}>+1</Radio.Button>
                <Radio.Button value={5}>+5</Radio.Button>
                <Radio.Button value={10}>+10</Radio.Button>
              </Radio.Group>
            </div>

            <Space wrap>
              <Button
                size="small"
                icon={<FastForwardOutlined />}
                onClick={handleFillAllRemaining}
                disabled={!selectedPoId || items.length === 0}
                className="text-blue-600 border-blue-400"
              >
                Điền đủ số còn lại
              </Button>
              <Button
                size="small"
                icon={<ClearOutlined />}
                onClick={handleResetScan}
                disabled={!selectedPoId || totalAccepted === 0}
              >
                Đặt lại về 0
              </Button>
            </Space>
          </div>
        </div>

        {/* Thanh trạng thái nhanh vừa quét */}
        {lastScannedSku && (
          <div className="mt-2.5 flex items-center gap-2 text-xs text-emerald-800 bg-emerald-100/80 px-3 py-1.5 rounded-md border border-emerald-300">
            <CheckCircleOutlined className="text-emerald-600" />
            <span>
              Đã ghi nhận quét thành công cho SKU: <strong>{lastScannedSku}</strong> (+{scanStep})
            </span>
          </div>
        )}
      </Card>

      {/* Bảng Chi tiết các mặt hàng của PO */}
      <Card variant="borderless" className="shadow-2xs">
        <div className="flex items-center justify-between mb-3">
          <div className="font-semibold text-slate-800 flex items-center gap-2">
            <InboxOutlined className="text-blue-600" />
            <span>Danh sách mặt hàng nhập kho</span>
            <Badge count={items.length} style={{ backgroundColor: '#108ee9' }} />
          </div>

          <div className="text-xs text-slate-500">
            Số lượng đạt: <strong className="text-emerald-600 text-sm">{formatNumber(totalAccepted)}</strong> |{' '}
            Hàng lỗi: <strong className="text-amber-600 text-sm">{formatNumber(totalRejected)}</strong> |{' '}
            Ước tính giá trị: <strong className="text-slate-800 text-sm">{formatCurrency(totalEstimatedAmount)}</strong>
          </div>
        </div>

        <Table
          rowKey="poItemId"
          columns={columns}
          dataSource={items}
          loading={loadingProgress}
          pagination={false}
          scroll={{ x: 1300 }}
          locale={{
            emptyText: selectedPoId
              ? 'Không có mặt hàng nào cần nhập trong đơn PO này.'
              : 'Vui lòng chọn đơn mua hàng PO ở trên để hiển thị danh sách sản phẩm.',
          }}
        />
      </Card>

      {/* Footer Form Thao tác */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4 p-4 bg-slate-50 rounded-lg border border-slate-200">
        <div className="flex flex-wrap items-center gap-4 text-sm">
          <div>
            Tổng SL đạt chuẩn:{' '}
            <strong className="text-emerald-600 text-base">{formatNumber(totalAccepted)}</strong>
          </div>
          <div>
            Tổng SL phế phẩm:{' '}
            <strong className="text-amber-600 text-base">{formatNumber(totalRejected)}</strong>
          </div>
          <div>
            Tổng tiền hàng:{' '}
            <strong className="text-blue-700 text-base">{formatCurrency(totalEstimatedAmount)}</strong>
          </div>
        </div>

        <Space wrap>
          <Button onClick={onCancel}>Hủy bỏ</Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={submitting}
            onClick={handleSubmit}
            className="bg-emerald-600 hover:bg-emerald-700 font-semibold px-6"
          >
            Xác nhận Nhập kho (GRN)
          </Button>
        </Space>
      </div>

      {/* Modal Nhập Lô, Hạn sử dụng & Gán vị trí cất hàng (WMS-10.5) */}
      <LotExpiryPutawayModal
        open={isLotModalOpen}
        item={lotModalItem}
        binLocations={binLocations}
        receiptDate={receiptDate}
        onClose={() => {
          setIsLotModalOpen(false)
          barcodeInputRef.current?.focus()
        }}
        onSave={handleSaveLotModal}
      />

      {/* Modal Camera Quét Barcode (html5-qrcode) */}
      <BarcodeCameraScanner
        open={isCameraModalOpen}
        onClose={() => {
          setIsCameraModalOpen(false)
          barcodeInputRef.current?.focus()
        }}
        onScan={(code) => {
          processBarcodeScan(code)
        }}
      />
    </div>
  )
}
