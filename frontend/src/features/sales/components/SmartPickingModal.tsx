import {
  BarcodeOutlined,
  CheckCircleOutlined,
  CheckOutlined,
  FastForwardOutlined,
  PrinterOutlined,
  RocketOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Badge,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
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
import { formatNumber } from '../../../utils/format'
import { playErrorBeep, playSuccessBeep, playWarningBeep } from '../../../utils/sound'
import * as warehouseApi from '../../warehouses/warehouseApi'
import type { BinLocation } from '../../warehouses/types'
import * as pickingApi from '../pickingApi'
import { buildOptimizedPickingList } from '../pickingOptimizer'
import type { DeductStockPayload, PickingItem } from '../pickingTypes'
import type { SalesOrder } from '../types'
import PrintPickingListModal from './PrintPickingListModal'

const { Text } = Typography

interface SmartPickingModalProps {
  open: boolean
  order: SalesOrder | null
  onClose: () => void
  onSuccess?: (order: SalesOrder) => void
}

export default function SmartPickingModal({
  open,
  order,
  onClose,
  onSuccess,
}: SmartPickingModalProps) {
  const { message } = App.useApp()

  const [items, setItems] = useState<PickingItem[]>([])
  const [loadingRoute, setLoadingRoute] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // Quét mã vạch tự động
  const [barcodeInput, setBarcodeInput] = useState('')
  const [lastScannedSku, setLastScannedSku] = useState<string | null>(null)
  const barcodeInputRef = useRef<InputRef>(null)

  // Modal in phiếu
  const [isPrintModalOpen, setIsPrintModalOpen] = useState(false)

  // Focus ô quét mã vạch khi mở modal
  useEffect(() => {
    if (open) {
      const timer = setTimeout(() => {
        barcodeInputRef.current?.focus()
      }, 200)
      return () => clearTimeout(timer)
    }
  }, [open])

  // Tải danh sách ô kệ và lô hàng để tối ưu lộ trình
  useEffect(() => {
    if (!open || !order) {
      return
    }

    let ignore = false
    const loadRouteData = async () => {
      setLoadingRoute(true)
      try {
        let bins: BinLocation[] = []
        if (order.warehouseId) {
          const rawBins = await warehouseApi.fetchLocations({
            warehouseId: order.warehouseId,
            active: true,
          })
          bins = (rawBins || []).filter((b) => b.active)
        }

        const lots = order.warehouseId
          ? await pickingApi.fetchLotsForWarehouse(order.warehouseId)
          : []

        if (!ignore) {
          const optimized = buildOptimizedPickingList(order, bins, lots)
          setItems(optimized)
        }
      } catch (err) {
        if (!ignore) {
          console.error('Không thể tối ưu lộ trình lấy hàng:', err)
          // Fallback lộ trình cơ bản từ items của SO
          const fallback = buildOptimizedPickingList(order, [], [])
          setItems(fallback)
        }
      } finally {
        if (!ignore) setLoadingRoute(false)
      }
    }

    void loadRouteData()
    return () => {
      ignore = true
    }
  }, [open, order])

  // Cập nhật trạng thái tick đã lấy hàng vào xe đẩy
  const handleTogglePicked = (id: string, checked: boolean) => {
    setItems((prev) =>
      prev.map((it) => {
        if (it.id === id) {
          return {
            ...it,
            isPicked: checked,
            pickedQuantity: checked ? it.orderedQuantity : 0,
          }
        }
        return it
      })
    )
    if (checked) {
      playSuccessBeep()
    }
  }

  // Cập nhật số lượng nhặt thủ công
  const handleQuantityChange = (id: string, qty: number | null) => {
    const val = Math.max(0, qty || 0)
    setItems((prev) =>
      prev.map((it) => {
        if (it.id === id) {
          return {
            ...it,
            pickedQuantity: val,
            isPicked: val >= it.orderedQuantity,
          }
        }
        return it
      })
    )
  }

  // Lấy nhanh toàn bộ vào xe đẩy
  const handlePickAll = () => {
    setItems((prev) =>
      prev.map((it) => ({
        ...it,
        isPicked: true,
        pickedQuantity: it.orderedQuantity,
      }))
    )
    playSuccessBeep()
    message.success('Đã đánh dấu lấy toàn bộ hàng hóa vào xe đẩy!')
    barcodeInputRef.current?.focus()
  }

  // Quét mã vạch SKU để tự động tick vào xe đẩy (< 100ms)
  const processBarcodeScan = (rawCode: string) => {
    const code = rawCode.trim().toLowerCase()
    if (!code) return

    const targetIndex = items.findIndex(
      (it) =>
        it.skuCode.toLowerCase() === code ||
        (it.barcode && it.barcode.toLowerCase() === code) ||
        String(it.productId) === code
    )

    if (targetIndex === -1) {
      playErrorBeep()
      message.error(`Mã vạch "${rawCode}" không có trong lộ trình lấy hàng của đơn ${order?.soNumber}!`)
      setBarcodeInput('')
      barcodeInputRef.current?.focus()
      return
    }

    const target = items[targetIndex]
    const nextQty = target.pickedQuantity + 1

    if (nextQty > target.orderedQuantity) {
      playWarningBeep()
      message.warning(`Mặt hàng ${target.skuCode} đã lấy đủ số lượng yêu cầu (${target.orderedQuantity} ${target.uom})!`)
    } else {
      playSuccessBeep()
    }

    setItems((prev) =>
      prev.map((it, idx) => {
        if (idx === targetIndex) {
          const newQty = Math.min(it.orderedQuantity, it.pickedQuantity + 1)
          return {
            ...it,
            pickedQuantity: newQty,
            isPicked: newQty >= it.orderedQuantity,
          }
        }
        return it
      })
    )

    setLastScannedSku(target.skuCode)
    setTimeout(() => setLastScannedSku(null), 1500)

    setBarcodeInput('')
    setTimeout(() => {
      barcodeInputRef.current?.focus()
    }, 40)
  }

  const handleBarcodeKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      processBarcodeScan(barcodeInput)
    }
  }

  // Thống kê tiến độ lấy hàng
  const totalItemsCount = items.length
  const pickedItemsCount = items.filter((it) => it.isPicked).length
  const totalOrderedQuantity = items.reduce((sum, it) => sum + (it.orderedQuantity || 0), 0)
  const totalPickedQuantity = items.reduce((sum, it) => sum + (it.pickedQuantity || 0), 0)

  const progressPercent =
    totalItemsCount > 0 ? Math.round((pickedItemsCount / totalItemsCount) * 100) : 0
  const isFullyPicked = totalItemsCount > 0 && pickedItemsCount === totalItemsCount

  // Xác nhận hoàn tất nhặt hàng & Xuất xưởng trừ tồn kho
  const handleCompleteDispatch = async () => {
    if (!order) return

    if (!isFullyPicked) {
      playErrorBeep()
      message.warning('Vui lòng hoàn tất lấy đủ 100% các mặt hàng vào xe đẩy trước khi xuất kho!')
      return
    }

    const deductPayload: DeductStockPayload = {
      orderId: order.id,
      orderNumber: order.soNumber,
      warehouseId: order.warehouseId || 1,
      reason: 'SALES_DISPATCH',
      note: `Xuất kho hoàn tất lộ trình lấy hàng theo đơn ${order.soNumber}`,
      items: items.map((it) => ({
        productId: it.productId,
        skuCode: it.skuCode,
        quantity: it.pickedQuantity,
        binLocationId: it.locationId,
        lotNo: it.lotNo,
      })),
    }

    setSubmitting(true)
    try {
      await pickingApi.dispatchOrderStock(deductPayload)
      playSuccessBeep()
      message.success(`Đã hoàn tất lấy hàng và xuất kho thành công cho đơn ${order.soNumber}!`)
      if (onSuccess) {
        onSuccess({
          ...order,
          status: 'COMPLETED',
        })
      }
      onClose()
    } catch (err) {
      playErrorBeep()
      message.error(getApiErrorMessage(err, 'Không thể xác nhận xuất kho'))
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ColumnsType<PickingItem> = [
    {
      title: 'Chặng',
      dataIndex: 'step',
      key: 'step',
      width: 70,
      align: 'center',
      render: (step) => (
        <span className="font-bold text-slate-800 bg-blue-50 text-blue-700 px-2 py-0.5 rounded border border-blue-200">
          #{step}
        </span>
      ),
    },
    {
      title: 'Vị trí lấy hàng (Zone → Rack → Bin)',
      key: 'location',
      width: 220,
      render: (_, r) => (
        <div className="space-y-0.5">
          <div className="flex items-center gap-1.5">
            <Tag color="cyan" className="font-mono font-bold text-xs m-0">
              {r.locationCode}
            </Tag>
            <span className="text-[11px] font-semibold text-slate-700">
              Khu {r.zone || '-'}
            </span>
          </div>
          <div className="text-[11px] text-slate-500">
            Dãy {r.rack || '-'} &bull; Ô {r.bin || '-'}
          </div>
        </div>
      ),
    },
    {
      title: 'Mặt hàng & SKU',
      key: 'product',
      width: 260,
      render: (_, r) => {
        const isHighlighted = lastScannedSku === r.skuCode
        return (
          <div
            className={`p-1 rounded transition-colors ${
              isHighlighted ? 'bg-emerald-100 ring-2 ring-emerald-400' : ''
            }`}
          >
            <div
              className={`font-semibold text-slate-800 ${
                r.isPicked ? 'line-through text-slate-400' : ''
              }`}
            >
              {r.productName}
            </div>
            <div className="text-xs text-slate-500 flex items-center gap-2 mt-0.5">
              <span className="font-mono bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200">
                {r.skuCode}
              </span>
              <span>ĐVT: {r.uom}</span>
            </div>
            {(r.lotNo || r.expDate) && (
              <div className="flex items-center gap-2 mt-1 text-[11px]">
                {r.lotNo && (
                  <Tag color="blue" className="text-[10px] m-0 font-mono">
                    Lô: {r.lotNo}
                  </Tag>
                )}
                {r.expDate && (
                  <span className="text-purple-700">
                    HSD: {dayjs(r.expDate).format('DD/MM/YYYY')}
                  </span>
                )}
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'SL yêu cầu',
      dataIndex: 'orderedQuantity',
      key: 'orderedQuantity',
      width: 100,
      align: 'right',
      render: (val, r) => (
        <span className="font-semibold text-slate-800">
          {formatNumber(val)} {r.uom}
        </span>
      ),
    },
    {
      title: 'Đã vào xe đẩy',
      key: 'pickedQuantity',
      width: 160,
      align: 'center',
      render: (_, r) => (
        <div className="flex items-center justify-center gap-1.5">
          <InputNumber
            min={0}
            max={r.orderedQuantity}
            value={r.pickedQuantity}
            onChange={(val) => handleQuantityChange(r.id, val)}
            className="w-20 text-center font-bold text-emerald-700"
            disabled={order?.status === 'COMPLETED'}
          />
          <span className="text-xs text-slate-500">/ {r.orderedQuantity}</span>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      key: 'status',
      width: 130,
      align: 'center',
      render: (_, r) => {
        if (r.isPicked) {
          return (
            <Tag color="success" className="flex items-center gap-1 justify-center m-0">
              <CheckCircleOutlined /> Đã vào xe
            </Tag>
          )
        }
        if (r.pickedQuantity > 0) {
          return <Tag color="processing">Đang lấy dở</Tag>
        }
        return <Tag color="default">Chưa lấy</Tag>
      },
    },
    {
      title: 'Tick chọn',
      key: 'action',
      width: 100,
      align: 'center',
      render: (_, r) => (
        <Checkbox
          checked={r.isPicked}
          onChange={(e) => handleTogglePicked(r.id, e.target.checked)}
          disabled={order?.status === 'COMPLETED'}
        >
          <span className="text-xs">{r.isPicked ? 'Đã lấy' : 'Xong'}</span>
        </Checkbox>
      ),
    },
  ]

  if (!order) return null

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center gap-2 text-slate-800">
          <RocketOutlined className="text-orange-500 text-lg animate-pulse" />
          <span>Lộ Trình Lấy Hàng Thông Minh (Smart Picking View - FR17)</span>
        </div>
      }
      width={1100}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        <Button
          key="print"
          icon={<PrinterOutlined />}
          onClick={() => setIsPrintModalOpen(true)}
        >
          In Phiếu Lấy Hàng
        </Button>,
        <Button
          key="dispatch"
          type="primary"
          icon={<CheckOutlined />}
          loading={submitting}
          disabled={!isFullyPicked || order.status === 'COMPLETED'}
          onClick={handleCompleteDispatch}
          className="bg-emerald-600 hover:bg-emerald-700 font-semibold"
        >
          {order.status === 'COMPLETED'
            ? 'Đơn đã xuất kho'
            : isFullyPicked
            ? 'Hoàn tất lấy hàng & Xuất kho'
            : `Đang lấy hàng (${progressPercent}%)`}
        </Button>,
      ]}
      destroyOnClose
    >
      <div className="space-y-4 pt-2">
        {/* 1. Header tóm tắt đơn bán hàng và kho xuất */}
        <Card size="small" className="bg-slate-50 border-slate-200">
          <Row gutter={[16, 12]} align="middle">
            <Col xs={24} md={14}>
              <Descriptions size="small" column={{ xs: 1, sm: 2 }}>
                <Descriptions.Item label="Mã Đơn Bán Hàng">
                  <span className="font-mono font-bold text-blue-700 text-sm">
                    {order.soNumber}
                  </span>
                </Descriptions.Item>
                <Descriptions.Item label="Kho xuất hàng">
                  <strong className="text-slate-800">
                    {order.warehouseName || `Kho #${order.warehouseId}`}
                  </strong>
                </Descriptions.Item>
                <Descriptions.Item label="Khách hàng">
                  {order.customerName || 'N/A'}
                </Descriptions.Item>
                <Descriptions.Item label="Địa chỉ giao">
                  {order.deliveryAddress || '-'}
                </Descriptions.Item>
              </Descriptions>
            </Col>

            <Col xs={24} md={10}>
              <div className="bg-white p-3 rounded-lg border border-slate-200 space-y-2">
                <div className="flex justify-between items-center text-xs">
                  <span className="font-semibold text-slate-700 flex items-center gap-1">
                    <ShoppingCartOutlined className="text-blue-600" /> Tiến độ nhặt hàng:
                  </span>
                  <span className="font-bold text-slate-900">
                    {pickedItemsCount}/{totalItemsCount} mặt hàng ({progressPercent}%)
                  </span>
                </div>
                <Progress
                  percent={progressPercent}
                  status={isFullyPicked ? 'success' : 'active'}
                  strokeColor={isFullyPicked ? '#10b981' : '#f97316'}
                />
                <div className="flex justify-between text-[11px] text-slate-500">
                  <span>
                    Tổng SP đã lấy: <strong>{formatNumber(totalPickedQuantity)}</strong> /{' '}
                    {formatNumber(totalOrderedQuantity)}
                  </span>
                  {isFullyPicked && (
                    <span className="text-emerald-600 font-semibold">
                      ✓ Đã nhặt đủ 100% lên xe đẩy
                    </span>
                  )}
                </div>
              </div>
            </Col>
          </Row>
        </Card>

        {/* 2. Khung Quét Mã Vạch Thông Minh (Smart Scan Zone) */}
        {order.status !== 'COMPLETED' && (
          <Card
            size="small"
            className="border-2 border-orange-300 bg-orange-50/40"
            bodyStyle={{ padding: '12px 16px' }}
          >
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <BarcodeOutlined className="text-orange-600 animate-pulse text-base" />
                  <span className="font-bold text-slate-800 text-xs">
                    Ô quét mã vạch SKU / Barcode nhặt hàng nhanh (&lt; 100ms)
                  </span>
                  <Tag color="orange" className="text-[10px] m-0">
                    Auto-Focus
                  </Tag>
                  <Tag color="blue" className="text-[10px] m-0">
                    Sound BEEP
                  </Tag>
                </div>
                <Input
                  ref={barcodeInputRef}
                  placeholder="Quét mã vạch SKU sản phẩm để tự động đánh dấu vào xe đẩy..."
                  value={barcodeInput}
                  onChange={(e) => setBarcodeInput(e.target.value)}
                  onKeyDown={handleBarcodeKeyDown}
                  className="font-mono text-sm border-orange-300 focus:border-orange-500"
                  allowClear
                />
              </div>

              <Space>
                <Button
                  icon={<FastForwardOutlined />}
                  onClick={handlePickAll}
                  className="text-blue-600 border-blue-400"
                >
                  Lấy nhanh toàn bộ
                </Button>
              </Space>
            </div>

            {lastScannedSku && (
              <div className="mt-2 text-xs text-emerald-800 bg-emerald-100 px-2.5 py-1 rounded border border-emerald-300 flex items-center gap-1.5">
                <CheckCircleOutlined className="text-emerald-600" />
                <span>
                  Đã ghi nhận quét thành công cho SKU: <strong>{lastScannedSku}</strong> (+1)
                </span>
              </div>
            )}
          </Card>
        )}

        {/* Thông báo hướng dẫn lộ trình */}
        <Alert
          type="info"
          showIcon
          message="Lộ trình di chuyển lấy hàng tối ưu theo thứ tự Zone → Rack → Bin"
          description="Danh sách đã được thuật toán sắp xếp tuần tự theo Khu vực (Zone), Dãy kệ (Rack), và Ô kệ (Bin) để người nhặt hàng đi một vòng liên tục qua các lối đi mà không cần quay lui."
          className="text-xs"
        />

        {/* 3. Bảng Lộ trình lấy hàng */}
        <Card variant="borderless" className="shadow-2xs">
          <div className="flex justify-between items-center mb-3">
            <div className="font-semibold text-slate-800 flex items-center gap-2">
              <RocketOutlined className="text-orange-500" />
              <span>Thứ tự các điểm dừng lấy hàng (Picking Route Steps)</span>
              <Badge count={items.length} style={{ backgroundColor: '#f97316' }} />
            </div>
            <Text type="secondary" className="text-xs">
              Tick chọn hoặc quét mã vạch để đưa hàng vào xe đẩy
            </Text>
          </div>

          <Table
            rowKey="id"
            columns={columns}
            dataSource={items}
            loading={loadingRoute}
            pagination={false}
            scroll={{ x: 1000 }}
            size="small"
          />
        </Card>
      </div>

      {/* Modal In Phiếu Lấy Hàng */}
      <PrintPickingListModal
        open={isPrintModalOpen}
        order={order}
        items={items}
        onClose={() => setIsPrintModalOpen(false)}
      />
    </Modal>
  )
}
