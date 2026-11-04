import {
  CalendarOutlined,
  CheckCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  InfoCircleOutlined,
  SaveOutlined,
  ShopOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency, formatNumber } from '../../../utils/format'
import * as productApi from '../../products/productApi'
import type { Product } from '../../products/types'
import * as supplierApi from '../../suppliers/supplierApi'
import type { Supplier } from '../../suppliers/types'
import * as warehouseApi from '../../warehouses/warehouseApi'
import type { Warehouse } from '../../warehouses/types'
import { calculateOrderTotals } from '../poCalculations'
import * as purchasesApi from '../purchasesApi'
import type {
  CreatePurchaseOrderPayload,
  OrderStatus,
  PurchaseOrder,
  PurchaseOrderItemPayload,
} from '../types'
import PurchaseOrderItemsTable from './PurchaseOrderItemsTable'

const { Title, Text } = Typography
const { TextArea } = Input

interface FormValues {
  poNumber?: string
  supplierId: number
  warehouseId?: number
  orderDate: dayjs.Dayjs
  expectedDate?: dayjs.Dayjs
  note?: string
}

interface Props {
  initialOrder?: PurchaseOrder | null
  isEdit?: boolean
  onSuccess?: (order: PurchaseOrder) => void
  onCancel?: () => void
}

export default function PurchaseOrderForm({
  initialOrder,
  isEdit = false,
  onSuccess,
  onCancel,
}: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()

  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [loadingMetadata, setLoadingMetadata] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // Khởi tạo trực tiếp từ initialOrder hoặc mặc định 1 dòng rỗng
  const [items, setItems] = useState<PurchaseOrderItemPayload[]>(() => {
    if (initialOrder?.items && initialOrder.items.length > 0) {
      return initialOrder.items.map((it) => ({
        productId: it.productId ?? undefined,
        skuCode: it.skuCode,
        productName: it.productName,
        uom: it.uom ?? 'Cái',
        quantity: it.quantity,
        unitPrice: it.unitPrice,
        discountPercent: it.discountPercent ?? 0,
        discountAmount: it.discountAmount ?? 0,
        taxRate: it.taxRate ?? 0,
        taxAmount: it.taxAmount ?? 0,
        note: it.note ?? '',
      }))
    }
    return [
      {
        skuCode: '',
        productName: '',
        uom: 'Cái',
        quantity: 1,
        unitPrice: 0,
        discountPercent: 0,
        taxRate: 8,
        note: '',
      },
    ]
  })

  // State nhà cung cấp và kho đang được chọn
  const [selectedSupplierId, setSelectedSupplierId] = useState<number | undefined>(
    initialOrder?.supplierId
  )
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | undefined>(
    initialOrder?.warehouseId ?? undefined
  )

  // Tải danh mục NCC, Kho và Sản phẩm từ API
  useEffect(() => {
    let ignore = false
    const loadMetadata = async () => {
      setLoadingMetadata(true)
      try {
        const [supRes, whRes, prodRes] = await Promise.all([
          supplierApi.fetchSuppliers({ active: true, size: 500 }),
          warehouseApi.fetchWarehouses({ active: true }),
          productApi.fetchProducts({ active: true, size: 500 }),
        ])
        if (!ignore) {
          setSuppliers(supRes.content || [])
          setWarehouses(whRes || [])
          setProducts(prodRes.content || [])
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh mục Nhà cung cấp / Kho / Sản phẩm'))
        }
      } finally {
        if (!ignore) {
          setLoadingMetadata(false)
        }
      }
    }

    void loadMetadata()
    return () => {
      ignore = true
    }
  }, [message])

  // Lấy chi tiết NCC đang chọn để hiển thị thông tin thẻ tóm tắt & cảnh báo nợ
  const currentSupplier = useMemo(
    () => suppliers.find((s) => s.id === selectedSupplierId),
    [suppliers, selectedSupplierId]
  )

  // Lấy chi tiết Kho đang chọn
  const currentWarehouse = useMemo(
    () => warehouses.find((w) => w.id === selectedWarehouseId),
    [warehouses, selectedWarehouseId]
  )

  // Tính toán tổng tiền tức thì (Real-time calculation)
  const orderTotals = useMemo(() => calculateOrderTotals(items), [items])

  // Xử lý gửi Form (submit PO)
  const handleSubmitOrder = async (targetStatus: OrderStatus) => {
    try {
      const values = await form.validateFields()

      // Kiểm tra danh sách mặt hàng
      if (items.length === 0) {
        message.error('Đơn mua hàng phải có ít nhất 1 dòng mặt hàng')
        return
      }

      for (let i = 0; i < items.length; i++) {
        const it = items[i]
        if (!it.skuCode || !it.skuCode.trim()) {
          message.error(`Dòng #${i + 1}: Mã SKU không được để trống`)
          return
        }
        if (!it.productName || !it.productName.trim()) {
          message.error(`Dòng #${i + 1}: Tên mặt hàng không được để trống`)
          return
        }
        if (!it.quantity || it.quantity <= 0) {
          message.error(`Dòng #${i + 1} (${it.skuCode}): Số lượng đặt mua phải lớn hơn 0`)
          return
        }
        if (it.unitPrice === undefined || it.unitPrice === null || it.unitPrice < 0) {
          message.error(`Dòng #${i + 1} (${it.skuCode}): Đơn giá mua không được âm`)
          return
        }
      }

      setSubmitting(true)

      const payload: CreatePurchaseOrderPayload = {
        poNumber: values.poNumber?.trim() ? values.poNumber.trim() : undefined,
        supplierId: values.supplierId,
        warehouseId: values.warehouseId,
        warehouseName: currentWarehouse?.name,
        orderDate: values.orderDate.format('YYYY-MM-DD'),
        expectedDate: values.expectedDate ? values.expectedDate.format('YYYY-MM-DD') : undefined,
        note: values.note?.trim(),
        status: targetStatus,
        items: items.map((it) => ({
          productId: it.productId,
          skuCode: it.skuCode.trim(),
          productName: it.productName.trim(),
          uom: it.uom?.trim() || 'Cái',
          quantity: it.quantity,
          unitPrice: it.unitPrice,
          discountPercent: it.discountPercent ?? 0,
          discountAmount: it.discountAmount ?? 0,
          taxRate: it.taxRate ?? 0,
          taxAmount: it.taxAmount ?? 0,
          note: it.note?.trim(),
        })),
      }

      let result: PurchaseOrder
      if (isEdit && initialOrder) {
        result = await purchasesApi.updatePurchaseOrder(initialOrder.id, payload)
        message.success(`Cập nhật đơn mua hàng "${result.poNumber}" thành công!`)
      } else {
        result = await purchasesApi.createPurchaseOrder(payload)
        const statusLabel = targetStatus === 'PENDING' ? 'đã gửi chờ phê duyệt' : 'lưu nháp'
        message.success(`Lập đơn mua hàng "${result.poNumber}" thành công (${statusLabel})!`)
      }

      if (onSuccess) {
        onSuccess(result)
      }
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi lưu đơn mua hàng'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Spin spinning={loadingMetadata} tip="Đang tải danh mục dữ liệu...">
      <div className="space-y-6 max-w-7xl mx-auto">
        {/* Header Form */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 border-b border-slate-200">
          <div>
            <Title level={4} className="!mb-1 flex items-center gap-2 text-slate-800">
              <FileDoneOutlined className="text-blue-600" />
              <span>{isEdit ? `Chỉnh sửa Đơn Mua Hàng: ${initialOrder?.poNumber}` : 'Lập Đơn Mua Hàng Mới (Purchase Order)'}</span>
            </Title>
            <Text type="secondary" className="text-xs sm:text-sm">
              Chọn nhà cung cấp, kho tiếp nhận, thêm danh sách sản phẩm SKU và tự động tính tổng tiền thanh toán (FR10 - WMS-09.3)
            </Text>
          </div>

          <Space>
            {onCancel && (
              <Button onClick={onCancel} disabled={submitting}>
                Hủy / Quay lại
              </Button>
            )}
            <Button
              icon={<SaveOutlined />}
              onClick={() => handleSubmitOrder('DRAFT')}
              loading={submitting}
            >
              Lưu nháp (DRAFT)
            </Button>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={() => handleSubmitOrder('PENDING')}
              loading={submitting}
              className="bg-blue-600 hover:bg-blue-700 font-semibold"
            >
              Gửi phê duyệt (PENDING)
            </Button>
          </Space>
        </div>

        <Form
          form={form}
          layout="vertical"
          initialValues={{
            poNumber: initialOrder?.poNumber,
            supplierId: initialOrder?.supplierId,
            warehouseId: initialOrder?.warehouseId ?? undefined,
            orderDate: initialOrder?.orderDate ? dayjs(initialOrder.orderDate) : dayjs(),
            expectedDate: initialOrder?.expectedDate ? dayjs(initialOrder.expectedDate) : undefined,
            note: initialOrder?.note ?? '',
          }}
        >
          <Row gutter={[16, 16]}>
            {/* Cột trái: Thông tin Nhà cung cấp & Kho nhận */}
            <Col xs={24} lg={16}>
              <Card
                title={
                  <div className="flex items-center gap-2 text-slate-700 text-sm font-semibold">
                    <ShopOutlined className="text-blue-600" />
                    <span>Thông tin đối tác & Địa điểm giao nhận</span>
                  </div>
                }
                size="small"
                className="shadow-2xs"
              >
                <Row gutter={16}>
                  <Col xs={24} sm={12}>
                    <Form.Item
                      name="supplierId"
                      label="Nhà cung cấp"
                      rules={[{ required: true, message: 'Vui lòng chọn nhà cung cấp' }]}
                    >
                      <Select
                        showSearch
                        placeholder="Tìm và chọn Nhà cung cấp..."
                        filterOption={(input, option) => {
                          const label = String(option?.label ?? '')
                          return label.toLowerCase().includes(input.toLowerCase())
                        }}
                        options={suppliers.map((s) => ({
                          value: s.id,
                          label: `[${s.code}] ${s.name}`,
                        }))}
                        onChange={(val) => setSelectedSupplierId(val)}
                      />
                    </Form.Item>
                  </Col>

                  <Col xs={24} sm={12}>
                    <Form.Item name="warehouseId" label="Kho tiếp nhận hàng">
                      <Select
                        showSearch
                        placeholder="Chọn kho nhận hàng..."
                        allowClear
                        filterOption={(input, option) => {
                          const label = String(option?.label ?? '')
                          return label.toLowerCase().includes(input.toLowerCase())
                        }}
                        options={warehouses.map((w) => ({
                          value: w.id,
                          label: `[${w.code}] ${w.name}`,
                        }))}
                        onChange={(val) => setSelectedWarehouseId(val)}
                      />
                    </Form.Item>
                  </Col>
                </Row>

                {/* Thẻ hiển thị tóm tắt thông tin NCC đang chọn */}
                {currentSupplier && (
                  <div className="mt-1 p-3 bg-slate-50 border border-slate-200 rounded-md text-xs space-y-1.5">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="font-semibold text-slate-800">
                        {currentSupplier.code} - {currentSupplier.name}
                      </div>
                      <div className="flex items-center gap-2">
                        {currentSupplier.isOverCreditLimit && (
                          <Tag color="error" className="m-0 text-[10px] flex items-center gap-1">
                            <WarningOutlined /> Vượt hạn mức nợ
                          </Tag>
                        )}
                        <Tag color="cyan" className="m-0 text-[10px]">
                          ĐKTT: {currentSupplier.paymentTerms || 'NET_30'}
                        </Tag>
                      </div>
                    </div>

                    <div className="text-slate-500 flex flex-wrap gap-x-4 gap-y-1">
                      {currentSupplier.taxCode && <span>MST: <strong>{currentSupplier.taxCode}</strong></span>}
                      {currentSupplier.phone && <span>SĐT: <strong>{currentSupplier.phone}</strong></span>}
                      {currentSupplier.address && <span>Địa chỉ: <strong>{currentSupplier.address}</strong></span>}
                    </div>

                    <div className="flex flex-wrap items-center gap-x-6 gap-y-1 pt-1 border-t border-slate-200 text-slate-600">
                      <span>
                        Hạn mức nợ: <strong>{formatCurrency(currentSupplier.creditLimit)}</strong>
                      </span>
                      <span>
                        Công nợ hiện tại:{' '}
                        <strong className={currentSupplier.isOverCreditLimit ? 'text-red-600' : 'text-slate-800'}>
                          {formatCurrency(currentSupplier.currentDebt)}
                        </strong>
                      </span>
                      <span>
                        Còn được nợ:{' '}
                        <strong className="text-green-700">
                          {formatCurrency(Math.max(0, currentSupplier.creditLimit - currentSupplier.currentDebt))}
                        </strong>
                      </span>
                    </div>

                    {currentSupplier.isOverCreditLimit && (
                      <Alert
                        type="warning"
                        showIcon
                        message="Lưu ý: Nhà cung cấp này đang vượt hạn mức công nợ quy định. Đơn hàng có thể cần quản lý tài chính phê duyệt đặc biệt."
                        className="mt-2 py-1 text-xs"
                      />
                    )}
                  </div>
                )}
              </Card>
            </Col>

            {/* Cột phải: Thông tin Đơn hàng & Ngày nhận */}
            <Col xs={24} lg={8}>
              <Card
                title={
                  <div className="flex items-center gap-2 text-slate-700 text-sm font-semibold">
                    <CalendarOutlined className="text-blue-600" />
                    <span>Thời gian & Mã chứng từ</span>
                  </div>
                }
                size="small"
                className="shadow-2xs h-full"
              >
                <Form.Item
                  name="poNumber"
                  label="Mã đơn mua hàng (PO)"
                  tooltip="Để trống hệ thống sẽ tự động cấp mã theo định dạng PO-YYYYMMDD-XXXX"
                >
                  <Input placeholder="Tự động sinh (PO-YYYYMMDD-XXXX)" className="font-mono text-xs" />
                </Form.Item>

                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item
                      name="orderDate"
                      label="Ngày lập đơn"
                      rules={[{ required: true, message: 'Chọn ngày lập' }]}
                    >
                      <DatePicker className="w-full" format="DD/MM/YYYY" />
                    </Form.Item>
                  </Col>

                  <Col span={12}>
                    <Form.Item name="expectedDate" label="Ngày nhận dự kiến">
                      <DatePicker className="w-full" format="DD/MM/YYYY" placeholder="Chọn ngày" />
                    </Form.Item>
                  </Col>
                </Row>

                <Form.Item name="note" label="Ghi chú đơn hàng" className="!mb-0">
                  <TextArea
                    rows={2}
                    placeholder="Giao hàng giờ hành chính, đóng gói quy cách tiêu chuẩn..."
                    maxLength={500}
                    showCount
                  />
                </Form.Item>
              </Card>
            </Col>
          </Row>
        </Form>

        {/* Bảng chi tiết sản phẩm SKU động */}
        <Card
          size="small"
          className="shadow-2xs"
          title={
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-800 text-sm">
                Chi tiết mặt hàng đặt mua & Tính toán tức thì
              </span>
              <span className="text-xs text-slate-400 font-normal">
                Hệ thống tự động tính thành tiền dòng = (Số lượng × Đơn giá - Chiết khấu) + Thuế VAT
              </span>
            </div>
          }
        >
          <PurchaseOrderItemsTable
            items={items}
            onChange={setItems}
            products={products}
            disabled={submitting}
          />
        </Card>

        {/* Khung Tóm tắt Tổng giá trị đơn hàng (Grand Total Summary Card) */}
        <Card size="small" className="bg-slate-50 border-slate-200 shadow-2xs">
          <Row gutter={[16, 16]} align="middle">
            <Col xs={24} md={10}>
              <div className="space-y-1">
                <div className="flex items-center gap-2 text-slate-700 font-semibold text-sm">
                  <DollarOutlined className="text-blue-600" />
                  <span>Tổng hợp giá trị Đơn mua hàng (PO)</span>
                </div>
                <div className="text-xs text-slate-500">
                  Tổng số mặt hàng: <strong>{formatNumber(orderTotals.itemCount)}</strong> dòng • 
                  Tổng số lượng: <strong>{formatNumber(orderTotals.totalQuantity)}</strong> đơn vị sản phẩm
                </div>
                <div className="text-[11px] text-slate-400 italic">
                  Đơn giá và các khoản chiết khấu, thuế đã được kiểm tra theo chuẩn nghiệp vụ mua hàng (FR10).
                </div>
              </div>
            </Col>

            <Col xs={24} md={14}>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-white p-3 rounded-lg border border-slate-200">
                <div>
                  <div className="text-[11px] text-slate-500">Tiền hàng (Subtotal):</div>
                  <div className="font-semibold text-slate-700 text-sm">
                    {formatCurrency(orderTotals.subtotal)}
                  </div>
                </div>

                <div>
                  <div className="text-[11px] text-slate-500">Chiết khấu:</div>
                  <div className="font-semibold text-amber-600 text-sm">
                    -{formatCurrency(orderTotals.totalDiscount)}
                  </div>
                </div>

                <div>
                  <div className="text-[11px] text-slate-500">Tiền thuế VAT:</div>
                  <div className="font-semibold text-blue-600 text-sm">
                    +{formatCurrency(orderTotals.totalTax)}
                  </div>
                </div>

                <div className="border-l border-slate-200 pl-3">
                  <div className="text-[11px] text-slate-500 font-medium">Tổng thanh toán:</div>
                  <div className="font-bold text-red-600 text-base">
                    {formatCurrency(orderTotals.grandTotal)}
                  </div>
                </div>
              </div>
            </Col>
          </Row>

          <Divider className="my-3" />

          {/* Action Bar cuối trang */}
          <div className="flex items-center justify-between">
            <div className="text-xs text-slate-500 flex items-center gap-1">
              <InfoCircleOutlined className="text-blue-500" />
              <span>Đơn ở trạng thái <strong>PENDING</strong> sẽ được gửi tới Quản lý để phê duyệt.</span>
            </div>

            <Space>
              {onCancel && (
                <Button onClick={onCancel} disabled={submitting}>
                  Hủy / Quay lại
                </Button>
              )}
              <Button
                icon={<SaveOutlined />}
                onClick={() => handleSubmitOrder('DRAFT')}
                loading={submitting}
              >
                Lưu nháp (DRAFT)
              </Button>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={() => handleSubmitOrder('PENDING')}
                loading={submitting}
                className="bg-blue-600 hover:bg-blue-700 font-semibold"
              >
                Gửi phê duyệt đơn mua (PENDING)
              </Button>
            </Space>
          </div>
        </Card>
      </div>
    </Spin>
  )
}
