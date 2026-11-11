import {
  BarcodeOutlined,
  CameraOutlined,
  CheckCircleOutlined,
  FastForwardOutlined,
  RocketOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons'
import {
  App,
  Badge,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { InputRef } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useRef, useState } from 'react'
import BarcodeCameraScanner from '../../purchases/components/BarcodeCameraScanner'
import { getApiErrorMessage, getErrorStatus } from '../../../services/api'
import { formatNumber } from '../../../utils/format'
import { playErrorBeep, playSuccessBeep, playWarningBeep } from '../../../utils/sound'
import * as pickingApi from '../pickingApi'
import * as salesApi from '../salesApi'
import type { SalesOrder, SalesOrderItem } from '../types'
import * as gdnApi from '../gdnApi'
import type { CreateGdnRequest, GdnResponse } from '../gdnTypes'
import PrintDeliveryNoteModal from './PrintDeliveryNoteModal'

const { Text } = Typography

/** Số lượng lưu DECIMAL(18,3): làm tròn 3 chữ số để tránh sai số dấu phẩy động (0.07 - 0.01) */
const round3 = (value: number) => Math.round(value * 1000) / 1000

/**
 * Số lượng đã xuất của từng dòng đơn bán, cộng từ các phiếu xuất đã lập (trừ phiếu đã hủy).
 * SalesOrderItemResponse không trả deliveredQuantity nên phải tính từ lịch sử phiếu xuất của đơn.
 */
function sumDeliveredBySoItem(gdns: GdnResponse[]): Map<number, number> {
  const delivered = new Map<number, number>()
  gdns
    .filter((g) => g.status !== 'CANCELLED')
    .forEach((g) => {
      ;(g.items || []).forEach((it) => {
        if (it.soItemId == null) return
        delivered.set(it.soItemId, round3((delivered.get(it.soItemId) ?? 0) + Number(it.deliveredQuantity ?? 0)))
      })
    })
  return delivered
}

export interface DeliveryItemRow {
  key: string
  salesOrderItemId: number
  productId: number
  skuCode: string
  productName: string
  barcode?: string
  uom: string
  orderedQuantity: number
  alreadyDeliveredQuantity: number
  remainingQuantity: number
  dispatchQuantity: number
  lotNo?: string
  expDate?: string
  binLocationId?: number
  binCode?: string
  isVerified: boolean
}

interface SmartGoodsDeliveryFormProps {
  open: boolean
  order: SalesOrder | null
  onClose: () => void
  onSuccess?: (createdGdn: GdnResponse) => void
}

export default function SmartGoodsDeliveryForm({
  open,
  order,
  onClose,
  onSuccess,
}: SmartGoodsDeliveryFormProps) {
  const { message } = App.useApp()

  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null)
  const [confirmedOrders, setConfirmedOrders] = useState<SalesOrder[]>([])
  const [loadingOrders, setLoadingOrders] = useState(false)

  // Đơn hàng đang xử lý: ưu tiên prop `order`, nếu không lấy theo dropdown đã chọn
  const activeOrder = order ?? confirmedOrders.find((o) => o.id === selectedOrderId) ?? null

  const [items, setItems] = useState<DeliveryItemRow[]>([])
  const [loadingItems, setLoadingItems] = useState(false)
  const [deliveryNoteText, setDeliveryNoteText] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // Chặn gửi hai lần (bấm đúp, phím Enter của súng quét rơi vào nút xác nhận) trước khi state kịp cập nhật
  const submittingRef = useRef(false)
  // Tăng để tải lại số lượng còn lại từ máy chủ (sau lỗi mạng không rõ phiếu đã được lập hay chưa)
  const [reloadKey, setReloadKey] = useState(0)

  // Quét mã vạch tự động
  const [barcodeInput, setBarcodeInput] = useState('')
  const [lastScannedSku, setLastScannedSku] = useState<string | null>(null)
  const [isCameraOpen, setIsCameraOpen] = useState(false)
  const barcodeInputRef = useRef<InputRef>(null)
  const scanFeedbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const scanFocusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // In phiếu xuất kho sau khi lập xong
  const [createdGdnForPrint, setCreatedGdnForPrint] = useState<GdnResponse | null>(null)
  const [isPrintModalOpen, setIsPrintModalOpen] = useState(false)

  // Nếu mở form mà chưa có order, tải danh sách đơn CONFIRMED để chọn
  useEffect(() => {
    if (!open || order) return

    let ignore = false
    const loadConfirmed = async () => {
      setLoadingOrders(true)
      try {
        const res = await salesApi.fetchSalesOrders({ status: 'CONFIRMED', size: 50 })
        if (!ignore) {
          const list = res.content || []
          setConfirmedOrders(list)
          if (list.length > 0) {
            // Đơn đã chọn trước đó có thể đã xuất đủ (COMPLETED) nên không còn trong danh sách
            setSelectedOrderId((prev) =>
              prev != null && list.some((o) => o.id === prev) ? prev : list[0].id
            )
          }
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách đơn bán hàng'))
        }
      } finally {
        if (!ignore) setLoadingOrders(false)
      }
    }

    void loadConfirmed()
    return () => {
      ignore = true
    }
  }, [open, order, message])

  // Dọn dẹp timer khi đóng modal hoặc unmount
  useEffect(() => {
    return () => {
      if (scanFeedbackTimerRef.current) clearTimeout(scanFeedbackTimerRef.current)
      if (scanFocusTimerRef.current) clearTimeout(scanFocusTimerRef.current)
    }
  }, [])

  // Auto-focus ô quét mã khi mở
  useEffect(() => {
    if (open) {
      const timer = setTimeout(() => {
        barcodeInputRef.current?.focus()
      }, 250)
      return () => {
        clearTimeout(timer)
        if (scanFeedbackTimerRef.current) clearTimeout(scanFeedbackTimerRef.current)
        if (scanFocusTimerRef.current) clearTimeout(scanFocusTimerRef.current)
        setLastScannedSku(null)
      }
    }
  }, [open, activeOrder])

  // Tải chi tiết đơn bán hàng & Lộ trình lấy hàng để hiển thị gợi ý vị trí ô/kệ & lô
  useEffect(() => {
    if (!open || !activeOrder) {
      return
    }

    let ignore = false
    const loadOrderItemsForDelivery = async () => {
      setLoadingItems(true)
      setItems([])
      try {
        // Tải chi tiết SO mới nhất và các phiếu xuất đã lập để tính số lượng còn phải giao
        const [fullOrder, existingGdns] = await Promise.all([
          salesApi.getSalesOrderById(activeOrder.id),
          gdnApi.fetchGoodsDeliveriesBySoId(activeOrder.id),
        ])
        if (ignore) return
        const deliveredMap = sumDeliveredBySoItem(existingGdns || [])

        // Tải gợi ý lấy hàng từ API hoặc optimizer
        const pickingMap = new Map<number, { locationCode: string; binId?: number; lotNo?: string }>()
        try {
          const pickingList = await pickingApi.loadPickingRoute(fullOrder)
          pickingList.forEach((p) => {
            if (!pickingMap.has(p.productId)) {
              pickingMap.set(p.productId, {
                locationCode: p.locationCode,
                binId: p.locationId,
                lotNo: p.lotNo,
              })
            }
          })
        } catch {
          // Bỏ qua lỗi gợi ý lộ trình, người dùng vẫn có thể kiểm đếm theo đơn
        }

        const rows: DeliveryItemRow[] = (fullOrder.items || []).map((it: SalesOrderItem, idx: number) => {
          const ordered = Number(it.quantity ?? 0)
          const delivered = deliveredMap.get(it.id) ?? 0
          const remaining = Math.max(0, round3(ordered - delivered))
          const pInfo = pickingMap.get(it.productId)

          return {
            key: `so-item-${it.id || idx}`,
            salesOrderItemId: it.id,
            productId: it.productId,
            skuCode: it.skuCode,
            productName: it.productName,
            barcode: it.barcode,
            uom: it.uom || '',
            orderedQuantity: ordered,
            alreadyDeliveredQuantity: delivered,
            remainingQuantity: remaining,
            // Ban đầu để 0 chờ súng bắn mã quét hoặc nhập
            dispatchQuantity: 0,
            lotNo: pInfo?.lotNo,
            binLocationId: pInfo?.binId,
            binCode: pInfo?.locationCode,
            isVerified: remaining === 0,
          }
        })

        if (!ignore) {
          setItems(rows)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải chi tiết mặt hàng đơn xuất kho'))
        }
      } finally {
        if (!ignore) setLoadingItems(false)
      }
    }

    void loadOrderItemsForDelivery()
    return () => {
      ignore = true
    }
  }, [open, activeOrder, message, reloadKey])

  // Cập nhật số lượng xuất thủ công
  const handleQuantityChange = (key: string, qty: number | null) => {
    const val = Math.max(0, round3(Number(qty ?? 0)))
    setItems((prev) =>
      prev.map((it) => {
        if (it.key === key) {
          const finalVal = Math.min(val, it.remainingQuantity)
          return {
            ...it,
            dispatchQuantity: finalVal,
            isVerified: finalVal >= it.remainingQuantity && it.remainingQuantity > 0,
          }
        }
        return it
      })
    )
  }

  // Đóng gói / Kiểm nhanh toàn bộ (Pack All)
  const handlePackAll = () => {
    setItems((prev) =>
      prev.map((it) => ({
        ...it,
        dispatchQuantity: it.remainingQuantity,
        isVerified: it.remainingQuantity > 0,
      }))
    )
    playSuccessBeep()
    message.success('Đã điền đủ 100% số lượng xuất xưởng cho toàn bộ mặt hàng!')
    barcodeInputRef.current?.focus()
  }

  // Quét mã vạch kiểm tra hàng hóa (< 100ms)
  const processBarcodeScan = (rawCode: string) => {
    const code = rawCode.trim().toLowerCase()
    if (!code) return

    // Tìm mặt hàng trong danh sách khớp mã SKU, Barcode hoặc Số lô
    const matches = items.filter(
      (it) =>
        it.skuCode.toLowerCase() === code ||
        (it.barcode && it.barcode.toLowerCase() === code) ||
        (it.lotNo && it.lotNo.toLowerCase() === code) ||
        String(it.productId) === code
    )

    if (matches.length === 0) {
      playErrorBeep()
      message.error(`Mã vạch "${rawCode}" không thuộc bất kỳ mặt hàng nào trong đơn ${activeOrder?.soNumber}!`)
      setBarcodeInput('')
      barcodeInputRef.current?.focus()
      return
    }

    // Ưu tiên dòng chưa kiểm đủ số lượng
    const target = matches.find((it) => it.dispatchQuantity < it.remainingQuantity)

    if (!target) {
      playWarningBeep()
      message.warning(`Mặt hàng ${matches[0].skuCode} đã được đóng gói đủ số lượng cần xuất!`)
      setBarcodeInput('')
      barcodeInputRef.current?.focus()
      return
    }

    // Tăng số lượng đã kiểm lên 1
    playSuccessBeep()
    setItems((prev) =>
      prev.map((it) => {
        if (it.key === target.key) {
          const nextQty = Math.min(it.remainingQuantity, round3(it.dispatchQuantity + 1))
          return {
            ...it,
            dispatchQuantity: nextQty,
            isVerified: nextQty >= it.remainingQuantity,
          }
        }
        return it
      })
    )

    setLastScannedSku(target.skuCode)
    if (scanFeedbackTimerRef.current) clearTimeout(scanFeedbackTimerRef.current)
    scanFeedbackTimerRef.current = setTimeout(() => setLastScannedSku(null), 1500)

    setBarcodeInput('')
    if (scanFocusTimerRef.current) clearTimeout(scanFocusTimerRef.current)
    scanFocusTimerRef.current = setTimeout(() => {
      barcodeInputRef.current?.focus()
    }, 40)
  }

  const handleBarcodeKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      processBarcodeScan(barcodeInput)
    }
  }

  // Xác nhận xuất xưởng (Dispatch GDN)
  const handleConfirmDispatch = async () => {
    if (!activeOrder) {
      message.warning('Vui lòng chọn đơn bán hàng cần xuất kho!')
      return
    }

    // Chỉ gửi các dòng có số lượng xuất > 0 (backend yêu cầu deliveredQuantity > 0),
    // không vượt số lượng còn lại của dòng đơn bán, làm tròn 3 chữ số như DECIMAL(18,3)
    const itemsToDispatch = items
      .map((it) => ({ ...it, dispatchQuantity: Math.min(round3(it.dispatchQuantity), it.remainingQuantity) }))
      .filter((it) => it.dispatchQuantity > 0)

    if (itemsToDispatch.length === 0) {
      message.warning('Vui lòng quét mã vạch hoặc nhập số lượng xuất cho ít nhất 1 mặt hàng!')
      barcodeInputRef.current?.focus()
      return
    }

    if (submittingRef.current || createdGdnForPrint) return
    submittingRef.current = true
    setSubmitting(true)
    try {
      const payload: CreateGdnRequest = {
        soId: activeOrder.id,
        // COMPLETED: xuất ngay, backend phát một sự kiện order.dispatched và inventory-service trừ tồn một lần
        status: 'COMPLETED',
        note: deliveryNoteText.trim() || undefined,
        items: itemsToDispatch.map((it) => ({
          soItemId: it.salesOrderItemId,
          productId: it.productId,
          deliveredQuantity: it.dispatchQuantity,
          batchNumber: it.lotNo,
          locationId: it.binLocationId,
          locationCode: it.binCode,
        })),
      }

      const createdGdn = await gdnApi.createGoodsDelivery(payload)
      playSuccessBeep()
      message.success(`Đã lập Phiếu xuất kho ${createdGdn.gdnNumber}, tồn kho được trừ theo phiếu này.`)

      // Chuẩn bị in phiếu giao hàng
      setCreatedGdnForPrint(createdGdn)
      setIsPrintModalOpen(true)

      if (onSuccess) {
        onSuccess(createdGdn)
      }
    } catch (err) {
      playErrorBeep()
      const status = getErrorStatus(err)
      if (status === undefined || status >= 500) {
        // Không nhận được phản hồi (mất mạng, hết thời gian chờ): phiếu có thể đã được lập ở máy chủ.
        // Không gửi lại nguyên yêu cầu cũ, tải lại số lượng còn lại để người dùng kiểm tra rồi mới xuất tiếp.
        message.warning(
          'Không xác định được phiếu xuất đã được lập hay chưa. Đã tải lại số lượng còn lại của đơn, vui lòng kiểm tra danh sách phiếu xuất trước khi xuất tiếp.'
        )
        setReloadKey((k) => k + 1)
      } else {
        message.error(getApiErrorMessage(err, 'Lỗi khi xác nhận xuất xưởng phiếu GDN'))
      }
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  // Thống kê tiến độ đóng gói
  const verifiedCount = items.filter((it) => it.isVerified && it.remainingQuantity > 0).length
  const activeItemsCount = items.filter((it) => it.remainingQuantity > 0).length
  const totalRemainingUnits = round3(items.reduce((s, it) => s + it.remainingQuantity, 0))
  const totalDispatchUnits = round3(items.reduce((s, it) => s + it.dispatchQuantity, 0))

  const progressPercent =
    activeItemsCount > 0 ? Math.round((verifiedCount / activeItemsCount) * 100) : 100
  const isFullyPacked = activeItemsCount > 0 && verifiedCount === activeItemsCount

  const columns: ColumnsType<DeliveryItemRow> = [
    {
      title: 'Mặt hàng & SKU',
      key: 'product',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-slate-800">{r.productName}</div>
          <div className="text-xs text-slate-500 flex items-center gap-2 mt-0.5">
            <span className="font-mono bg-slate-100 px-1 py-0.5 rounded border border-slate-200">
              {r.skuCode}
            </span>
            {r.barcode && (
              <span className="text-[11px] text-slate-400 font-mono">Mã: {r.barcode}</span>
            )}
          </div>
        </div>
      ),
    },
    {
      title: 'Vị trí lấy hàng',
      key: 'location',
      width: 150,
      render: (_, r) =>
        r.binCode ? (
          <div>
            <span className="font-mono font-bold text-blue-700 text-xs">
              {r.binCode}
            </span>
            <div className="text-[10px] text-slate-400">Ô lưu cất gợi ý</div>
          </div>
        ) : (
          <span className="text-xs text-slate-400 italic">Chưa chỉ định ô</span>
        ),
    },
    {
      title: 'Số Lô hàng',
      key: 'lotNo',
      width: 140,
      render: (_, r) =>
        r.lotNo ? (
          <Tag color="blue" className="font-mono text-xs m-0">
            Lô: {r.lotNo}
          </Tag>
        ) : (
          <span className="text-xs text-slate-400 italic">Không theo lô</span>
        ),
    },
    {
      title: 'SL đặt / Còn lại',
      key: 'quantities',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <div className="text-right">
          <div className="text-xs text-slate-500">
            Đặt: <strong>{formatNumber(r.orderedQuantity)}</strong> {r.uom}
          </div>
          <div className="text-xs font-bold text-orange-600">
            Cần xuất: {formatNumber(r.remainingQuantity)} {r.uom}
          </div>
        </div>
      ),
    },
    {
      title: 'Số lượng xuất xưởng',
      key: 'dispatchQty',
      width: 160,
      render: (_, r) => (
        <div className="space-y-1">
          <InputNumber
            min={0}
            max={r.remainingQuantity}
            precision={3}
            value={r.dispatchQuantity}
            onChange={(val) => handleQuantityChange(r.key, val)}
            disabled={r.remainingQuantity === 0}
            className="w-full font-mono text-sm font-bold text-emerald-700"
            addonAfter={r.uom}
          />
          {r.dispatchQuantity >= r.remainingQuantity && r.remainingQuantity > 0 && (
            <div className="text-[11px] text-emerald-600 font-semibold flex items-center gap-1">
              <CheckCircleOutlined /> Đã đủ số lượng
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      key: 'status',
      width: 120,
      align: 'center',
      render: (_, r) => {
        if (r.remainingQuantity === 0) {
          return <Tag color="default">Đã giao đủ</Tag>
        }
        if (r.dispatchQuantity === r.remainingQuantity) {
          return (
            <Tag color="success" className="font-medium">
              <CheckCircleOutlined /> Đã đóng gói
            </Tag>
          )
        }
        if (r.dispatchQuantity > 0) {
          return <Tag color="processing">Đang đóng gói</Tag>
        }
        return <Tag color="warning">Chưa kiểm</Tag>
      },
    },
  ]

  return (
    <>
      <Modal
        open={open}
        title={
          <div className="flex items-center gap-2 text-slate-800">
            <RocketOutlined className="text-emerald-600 text-lg" />
            <span>Form Lập Phiếu Xuất Kho (GDN) & Xác Nhận Xuất Xưởng</span>
          </div>
        }
        width={1050}
        onCancel={onClose}
        footer={[
          <Button key="close" onClick={onClose} disabled={submitting}>
            Hủy / Đóng
          </Button>,
          <Button
            key="submit"
            type="primary"
            icon={<CheckCircleOutlined />}
            loading={submitting}
            disabled={!activeOrder || loadingItems || Boolean(createdGdnForPrint)}
            onClick={() => void handleConfirmDispatch()}
            className="bg-emerald-600 hover:bg-emerald-700 font-semibold"
          >
            Xác Nhận Xuất Xưởng & In Phiếu Giao Hàng
          </Button>,
        ]}
        destroyOnClose
      >
        <div className="space-y-4 pt-2">
          {/* 1. Chọn đơn bán hàng SO nếu chưa có đơn */}
          {!order && (
            <Card size="small" className="bg-blue-50/50 border-blue-200">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="font-semibold text-slate-800 text-xs">
                  Chọn Đơn Bán Hàng (SO) đã xác nhận để xuất kho:
                </div>
                <Select
                  loading={loadingOrders}
                  placeholder="Chọn đơn SO cần xuất kho..."
                  value={activeOrder?.id}
                  onChange={(val) => {
                    setSelectedOrderId(val)
                  }}
                  className="w-full sm:w-80"
                  options={confirmedOrders.map((o) => ({
                    value: o.id,
                    label: `${o.soNumber} - ${o.customerName || 'N/A'} (${o.orderDate})`,
                  }))}
                />
              </div>
            </Card>
          )}

          {/* 2. Thông tin tóm tắt đơn bán hàng */}
          {activeOrder && (
            <Card size="small" className="bg-slate-50 border-slate-200">
              <Row gutter={[16, 12]} align="middle">
                <Col xs={24} md={14}>
                  <Descriptions size="small" column={{ xs: 1, sm: 2 }}>
                    <Descriptions.Item label="Mã Đơn SO">
                      <span className="font-mono font-bold text-blue-700">
                        {activeOrder.soNumber}
                      </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="Kho xuất hàng">
                      <strong className="text-slate-800">
                        {activeOrder.warehouseName || `Kho #${activeOrder.warehouseId}`}
                      </strong>
                    </Descriptions.Item>
                    <Descriptions.Item label="Khách hàng">
                      {activeOrder.customerName || 'N/A'}
                    </Descriptions.Item>
                    <Descriptions.Item label="Địa chỉ giao">
                      {activeOrder.deliveryAddress || 'Giao tại kho khách hàng'}
                    </Descriptions.Item>
                  </Descriptions>
                </Col>

                <Col xs={24} md={10}>
                  <div className="bg-white p-3 rounded-lg border border-slate-200 space-y-2">
                    <div className="flex justify-between items-center text-xs">
                      <span className="font-semibold text-slate-700 flex items-center gap-1">
                        <ShoppingCartOutlined className="text-emerald-600" /> Tiến độ đóng gói:
                      </span>
                      <span className="font-bold text-slate-900">
                        {verifiedCount}/{activeItemsCount} mặt hàng ({progressPercent}%)
                      </span>
                    </div>
                    <Progress
                      percent={progressPercent}
                      status={isFullyPacked ? 'success' : 'active'}
                      strokeColor={isFullyPacked ? '#10b981' : '#059669'}
                    />
                    <div className="flex justify-between text-[11px] text-slate-500">
                      <span>
                        Tổng SP xuất: <strong>{formatNumber(totalDispatchUnits)}</strong> /{' '}
                        {formatNumber(totalRemainingUnits)}
                      </span>
                      {isFullyPacked && (
                        <span className="text-emerald-600 font-semibold">
                          Đã kiểm đủ số lượng
                        </span>
                      )}
                    </div>
                  </div>
                </Col>
              </Row>
            </Card>
          )}

          {/* 3. Khung Quét Mã Vạch Thông Minh (< 100ms) */}
          <Card
            size="small"
            className="border-2 border-emerald-300 bg-emerald-50/40"
            bodyStyle={{ padding: '12px 16px' }}
          >
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <BarcodeOutlined className="text-emerald-600 animate-pulse text-base" />
                  <span className="font-bold text-slate-800 text-xs">
                    Ô quét mã vạch SKU / Barcode / Số lô kiểm tra đóng gói xuất xưởng (&lt; 100ms)
                  </span>
                  <Tag color="emerald" className="text-[10px] m-0">
                    Tự động focus
                  </Tag>
                  <Tag color="blue" className="text-[10px] m-0">
                    Âm thanh bíp
                  </Tag>
                </div>
                <Input
                  ref={barcodeInputRef}
                  placeholder="Quét mã vạch sản phẩm hoặc số lô để tự động cộng dồn số lượng xuất..."
                  value={barcodeInput}
                  onChange={(e) => setBarcodeInput(e.target.value)}
                  onKeyDown={handleBarcodeKeyDown}
                  className="font-mono text-sm border-emerald-300 focus:border-emerald-500"
                  allowClear
                />
              </div>

              <Space wrap>
                <Button
                  icon={<CameraOutlined />}
                  onClick={() => setIsCameraOpen(true)}
                  className="text-slate-700"
                >
                  Quét bằng Camera
                </Button>
                <Button
                  icon={<FastForwardOutlined />}
                  onClick={handlePackAll}
                  className="text-blue-600 border-blue-400"
                >
                  Đóng gói toàn bộ
                </Button>
              </Space>
            </div>

            {lastScannedSku && (
              <div className="mt-2 text-xs text-emerald-800 bg-emerald-100 px-2.5 py-1 rounded border border-emerald-300 flex items-center gap-1.5">
                <CheckCircleOutlined className="text-emerald-600" />
                <span>
                  Đã quét thành công mặt hàng: <strong>{lastScannedSku}</strong> (+1 số lượng xuất)
                </span>
              </div>
            )}
          </Card>

          {/* Ghi chú xuất hàng */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-slate-700 whitespace-nowrap">
              Ghi chú xuất hàng:
            </span>
            <Input
              placeholder="Nhập ghi chú giao hàng (ví dụ: Biển số xe, tên tài xế, đợt giao...)"
              value={deliveryNoteText}
              onChange={(e) => setDeliveryNoteText(e.target.value)}
              size="small"
              allowClear
            />
          </div>

          {/* 4. Bảng danh sách mặt hàng cần xuất */}
          <Card variant="borderless" className="shadow-2xs">
            <div className="flex justify-between items-center mb-2">
              <div className="font-semibold text-slate-800 flex items-center gap-2 text-xs">
                <span>Chi tiết các mặt hàng xuất xưởng</span>
                <Badge count={items.length} style={{ backgroundColor: '#059669' }} />
              </div>
              <Text type="secondary" className="text-xs">
                Bắn mã vạch liên tục hoặc chỉnh số lượng xuất thực tế
              </Text>
            </div>

            <Table
              rowKey="key"
              columns={columns}
              dataSource={items}
              loading={loadingItems}
              pagination={false}
              size="small"
              scroll={{ x: 800 }}
            />
          </Card>
        </div>
      </Modal>

      {/* Modal Quét Mã Vạch bằng Camera điện thoại/máy tính */}
      <BarcodeCameraScanner
        open={isCameraOpen}
        onClose={() => {
          setIsCameraOpen(false)
          barcodeInputRef.current?.focus()
        }}
        onScan={(code) => {
          processBarcodeScan(code)
          setIsCameraOpen(false)
          barcodeInputRef.current?.focus()
        }}
      />

      {/* Modal In Phiếu Giao Hàng (Delivery Note) sau khi xác nhận xuất xưởng thành công */}
      <PrintDeliveryNoteModal
        open={isPrintModalOpen}
        gdn={createdGdnForPrint}
        onClose={() => {
          setIsPrintModalOpen(false)
          setCreatedGdnForPrint(null)
          onClose()
        }}
      />
    </>
  )
}
