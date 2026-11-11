import {
  CalendarOutlined,
  CheckCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  SaveOutlined,
  ShopOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
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
  Switch,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency, formatNumber } from '../../../utils/format'
import * as customerApi from '../../customers/customerApi'
import type { Customer, CustomerCreditCheckResponse } from '../../customers/types'
import * as productApi from '../../products/productApi'
import type { Product } from '../../products/types'
import * as warehouseApi from '../../warehouses/warehouseApi'
import type { Warehouse } from '../../warehouses/types'
import * as salesApi from '../salesApi'
import { calculateOrderTotals } from '../soCalculations'
import type {
  CreateSalesOrderPayload,
  OrderStatus,
  SalesOrder,
  SalesOrderItemPayload,
  StockAtpResponse,
} from '../types'
import CustomerCreditAlert from './CustomerCreditAlert'
import SalesOrderItemsTable from './SalesOrderItemsTable'

const { Title, Text } = Typography
const { TextArea } = Input

interface FormValues {
  soNumber?: string
  customerId: number
  warehouseId?: number
  orderDate: dayjs.Dayjs
  expectedDate?: dayjs.Dayjs
  deliveryAddress?: string
  recipientName?: string
  recipientPhone?: string
  allowBackorder?: boolean
  note?: string
}

interface Props {
  initialOrder?: SalesOrder | null
  isEdit?: boolean
  onSuccess?: (order: SalesOrder) => void
  onCancel?: () => void
}

export default function SalesOrderForm({
  initialOrder,
  isEdit = false,
  onSuccess,
  onCancel,
}: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()

  const [customers, setCustomers] = useState<Customer[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [loadingMetadata, setLoadingMetadata] = useState(false)
  const [loadingCustomers, setLoadingCustomers] = useState(false)
  const [loadingProducts, setLoadingProducts] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // Credit check state
  const [creditInfo, setCreditInfo] = useState<CustomerCreditCheckResponse | null>(null)
  const [loadingCredit, setLoadingCredit] = useState(false)

  // Kho và Khách hàng đang chọn
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | undefined>(
    initialOrder?.customerId
  )
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | undefined>(
    initialOrder?.warehouseId ?? undefined
  )

  // Items trong đơn hàng
  const [items, setItems] = useState<SalesOrderItemPayload[]>(() => {
    if (initialOrder?.items && initialOrder.items.length > 0) {
      return initialOrder.items.map((it, idx) => ({
        _key: `row-${idx + 1}-${Date.now()}`,
        id: it.id,
        productId: it.productId,
        skuCode: it.skuCode,
        productName: it.productName,
        uom: it.uom ?? 'Cái',
        quantity: it.quantity,
        unitPrice: it.unitPrice,
        discountPercent: it.discountPercent ?? 0,
        discountAmount: it.discountAmount ?? 0,
        taxRate: it.taxRate ?? 8,
        taxAmount: it.taxAmount ?? 0,
        note: it.note ?? '',
        onHandQuantity: it.onHandQuantity,
        reservedQuantity: it.reservedQuantity,
        atpQuantity: it.atpQuantity,
      }))
    }
    return [
      {
        _key: `row-1-${Date.now()}`,
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

  // Tải danh mục ban đầu (size: 100)
  useEffect(() => {
    let ignore = false
    const loadMetadata = async () => {
      setLoadingMetadata(true)
      try {
        const [custRes, whRes, prodRes] = await Promise.all([
          customerApi.fetchCustomers({ active: true, size: 100 }),
          warehouseApi.fetchWarehouses({ active: true }),
          productApi.fetchProducts({ active: true, size: 100 }),
        ])
        if (!ignore) {
          setCustomers(custRes.content || [])
          setWarehouses(whRes || [])
          setProducts(prodRes.content || [])
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh mục Khách hàng / Kho / Sản phẩm'))
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

  // Tìm kiếm Khách hàng theo từ khóa từ server (debounce 300ms)
  const customerSearchTimeout = useRef<number | null>(null)
  const handleSearchCustomer = useCallback((keyword: string) => {
    if (customerSearchTimeout.current) {
      window.clearTimeout(customerSearchTimeout.current)
    }
    customerSearchTimeout.current = window.setTimeout(async () => {
      setLoadingCustomers(true)
      try {
        const res = await customerApi.fetchCustomers({
          keyword: keyword.trim() || undefined,
          active: true,
          size: 50,
        })
        setCustomers(res.content || [])
      } catch {
        // Tìm kiếm lỗi thì giữ danh sách khách hàng đang có, không đụng tới kết quả kiểm tra công nợ
      } finally {
        setLoadingCustomers(false)
      }
    }, 300)
  }, [])

  // Tìm kiếm Sản phẩm / SKU theo từ khóa từ server (debounce 300ms)
  const productSearchTimeout = useRef<number | null>(null)
  const handleSearchProducts = useCallback((keyword: string) => {
    if (productSearchTimeout.current) {
      window.clearTimeout(productSearchTimeout.current)
    }
    productSearchTimeout.current = window.setTimeout(async () => {
      setLoadingProducts(true)
      try {
        const res = await productApi.fetchProducts({
          keyword: keyword.trim() || undefined,
          active: true,
          size: 50,
        })
        setProducts(res.content || [])
      } catch {
        // Tìm kiếm lỗi thì giữ danh sách sản phẩm đang có, không đụng tới kết quả kiểm tra công nợ
      } finally {
        setLoadingProducts(false)
      }
    }, 300)
  }, [])

  // Tính tổng tiền toàn đơn hàng
  const orderTotals = useMemo(() => calculateOrderTotals(items), [items])

  // Kiểm tra hạn mức công nợ khách hàng khi khách hàng hoặc tổng tiền thay đổi (debounce 300ms)
  const creditCheckTimeout = useRef<number | null>(null)
  useEffect(() => {
    if (!selectedCustomerId) {
      return
    }

    if (creditCheckTimeout.current) {
      window.clearTimeout(creditCheckTimeout.current)
    }

    creditCheckTimeout.current = window.setTimeout(async () => {
      setLoadingCredit(true)
      try {
        const res = await customerApi.checkCustomerCredit(
          selectedCustomerId,
          orderTotals.grandTotal
        )
        setCreditInfo(res)
      } catch {
        setCreditInfo(null)
      } finally {
        setLoadingCredit(false)
      }
    }, 300)

    return () => {
      if (creditCheckTimeout.current) {
        window.clearTimeout(creditCheckTimeout.current)
      }
    }
  }, [selectedCustomerId, orderTotals.grandTotal])

  // Cập nhật lại tồn khả dụng ATP cho các dòng hàng khi chọn kho xuất thay đổi
  const fetchAtpForProduct = useCallback(
    async (productId: number, warehouseId?: number) => {
      try {
        const atpRes = await salesApi.getStockAtp(productId, warehouseId)
        return atpRes
      } catch {
        return {
          productId,
          warehouseId,
          onHandQuantity: 0,
          reservedQuantity: 0,
          atpQuantity: 0,
        }
      }
    },
    []
  )

  const refreshAllItemsAtp = useCallback(
    async (warehouseId?: number) => {
      const currentItems = items.filter((it) => Boolean(it.productId))
      if (currentItems.length === 0) return

      const atpMap = new Map<number, StockAtpResponse>()
      await Promise.all(
        currentItems.map(async (it) => {
          if (it.productId && !atpMap.has(it.productId)) {
            const atp = await fetchAtpForProduct(it.productId, warehouseId)
            atpMap.set(it.productId, atp)
          }
        })
      )

      setItems((prev) =>
        prev.map((it) => {
          if (!it.productId || !atpMap.has(it.productId)) return it
          const atp = atpMap.get(it.productId)!
          return {
            ...it,
            onHandQuantity: atp.onHandQuantity,
            reservedQuantity: atp.reservedQuantity,
            atpQuantity: atp.atpQuantity,
          }
        })
      )
    },
    [items, fetchAtpForProduct]
  )

  const handleWarehouseChange = (whId?: number) => {
    setSelectedWarehouseId(whId)
    void refreshAllItemsAtp(whId)
  }

  // Thao tác với danh sách mặt hàng
  const handleAddItem = () => {
    setItems((prev) => [
      ...prev,
      {
        _key: `row-${Date.now()}-${prev.length + 1}`,
        skuCode: '',
        productName: '',
        uom: 'Cái',
        quantity: 1,
        unitPrice: 0,
        discountPercent: 0,
        taxRate: 8,
        note: '',
      },
    ])
  }

  const handleRemoveItem = (key: string) => {
    setItems((prev) => prev.filter((it) => it._key !== key))
  }

  const handleItemChange = (
    key: string,
    field: keyof SalesOrderItemPayload,
    value: unknown
  ) => {
    setItems((prev) =>
      prev.map((it) => (it._key === key ? { ...it, [field]: value } : it))
    )
  }

  const handleProductSelect = async (key: string, product: Product) => {
    const atp = await fetchAtpForProduct(product.id, selectedWarehouseId)

    setItems((prev) =>
      prev.map((it) => {
        if (it._key !== key) return it
        return {
          ...it,
          productId: product.id,
          skuCode: product.sku,
          productName: product.name,
          uom: product.uomName || product.uomCode || 'Cái',
          unitPrice: product.sellingPrice || 0,
          taxRate: product.vatRate ?? 8,
          onHandQuantity: atp.onHandQuantity,
          reservedQuantity: atp.reservedQuantity,
          atpQuantity: atp.atpQuantity,
        }
      })
    )
  }

  // Submit form tạo / lưu đơn bán hàng
  const handleSubmit = async (targetStatus: OrderStatus) => {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }

    // Validate danh sách hàng hóa
    if (!items || items.length === 0) {
      message.error('Đơn bán hàng phải có ít nhất 1 dòng mặt hàng')
      return
    }

    for (let i = 0; i < items.length; i++) {
      const it = items[i]
      if (!it.productId || !it.skuCode) {
        message.error(`Dòng ${i + 1}: Vui lòng chọn sản phẩm / SKU`)
        return
      }
      if (!it.quantity || it.quantity <= 0) {
        message.error(`Dòng ${i + 1} (${it.skuCode}): Số lượng phải lớn hơn 0`)
        return
      }
    }

    // Cảnh báo nếu công nợ bị khóa hoàn toàn
    if (creditInfo && !creditInfo.canProceed) {
      message.error(creditInfo.message || 'Khách hàng không đủ điều kiện tín dụng để đặt đơn!')
      return
    }

    const payload: CreateSalesOrderPayload = {
      soNumber: values.soNumber?.trim() || undefined,
      customerId: values.customerId,
      warehouseId: values.warehouseId,
      warehouseName: warehouses.find((w) => w.id === values.warehouseId)?.name,
      deliveryAddress: values.deliveryAddress?.trim() || undefined,
      recipientName: values.recipientName?.trim() || undefined,
      recipientPhone: values.recipientPhone?.trim() || undefined,
      orderDate: values.orderDate.format('YYYY-MM-DD'),
      expectedDate: values.expectedDate ? values.expectedDate.format('YYYY-MM-DD') : undefined,
      note: values.note?.trim() || undefined,
      status: targetStatus,
      allowBackorder: values.allowBackorder ?? true,
      items: items.map((it) => ({
        productId: it.productId!,
        skuCode: it.skuCode,
        productName: it.productName,
        uom: it.uom,
        quantity: it.quantity,
        unitPrice: it.unitPrice,
        discountPercent: it.discountPercent ?? 0,
        discountAmount: it.discountAmount ?? 0,
        taxRate: it.taxRate ?? 0,
        taxAmount: it.taxAmount ?? 0,
        note: it.note?.trim() || undefined,
      })),
    }

    setSubmitting(true)
    try {
      let result: SalesOrder
      if (isEdit && initialOrder) {
        result = await salesApi.updateSalesOrder(initialOrder.id, {
          customerId: payload.customerId,
          warehouseId: payload.warehouseId,
          warehouseName: payload.warehouseName,
          deliveryAddress: payload.deliveryAddress,
          recipientName: payload.recipientName,
          recipientPhone: payload.recipientPhone,
          orderDate: payload.orderDate!,
          expectedDate: payload.expectedDate,
          note: payload.note,
          allowBackorder: payload.allowBackorder,
          items: payload.items,
        })
        message.success(`Cập nhật đơn bán hàng ${result.soNumber} thành công!`)
      } else {
        result = await salesApi.createSalesOrder(payload)
        if (result.status === 'BACKORDER') {
          message.warning(
            `Đơn ${result.soNumber} đã được tạo và chuyển sang BACKORDER do thiếu tồn khả dụng ATP!`
          )
        } else {
          message.success(`Tạo đơn bán hàng ${result.soNumber} thành công!`)
        }
      }

      onSuccess?.(result)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi lưu đơn bán hàng'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Spin spinning={loadingMetadata || submitting}>
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          soNumber: initialOrder?.soNumber,
          customerId: initialOrder?.customerId,
          warehouseId: initialOrder?.warehouseId,
          orderDate: initialOrder?.orderDate ? dayjs(initialOrder.orderDate) : dayjs(),
          expectedDate: initialOrder?.expectedDate ? dayjs(initialOrder.expectedDate) : dayjs().add(3, 'day'),
          deliveryAddress: initialOrder?.deliveryAddress,
          recipientName: initialOrder?.recipientName,
          recipientPhone: initialOrder?.recipientPhone,
          allowBackorder: true,
          note: initialOrder?.note,
        }}
      >
        <Card className="mb-4 shadow-sm border-slate-200">
          <div className="flex items-center justify-between mb-4">
            <Space align="center">
              <FileDoneOutlined className="text-xl text-blue-600" />
              <Title level={4} className="!m-0 text-slate-800">
                {isEdit ? `Chỉnh sửa Đơn bán hàng: ${initialOrder?.soNumber}` : 'Lập Đơn Bán Hàng Mới (Sales Order)'}
              </Title>
            </Space>
            <Space>
              <Tag color="blue">FR14: Kiểm tra Hạn mức nợ</Tag>
              <Tag color="cyan">FR15: Tồn khả dụng ATP</Tag>
            </Space>
          </div>

          <Row gutter={16}>
            <Col xs={24} sm={12} md={8}>
              <Form.Item
                name="customerId"
                label={
                  <span className="font-semibold text-slate-700">
                    <UserOutlined className="mr-1 text-blue-500" />
                    Khách hàng / Đại lý
                  </span>
                }
                rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}
              >
                <Select
                  showSearch
                  placeholder="Chọn hoặc tìm khách hàng theo tên, mã..."
                  loading={loadingCustomers}
                  filterOption={false}
                  onSearch={handleSearchCustomer}
                  onChange={(val) => {
                    setSelectedCustomerId(val)
                    setCreditInfo(null)
                    const cust = customers.find((c) => c.id === val)
                    if (cust && cust.address && !form.getFieldValue('deliveryAddress')) {
                      form.setFieldValue('deliveryAddress', cust.address)
                    }
                    if (cust && cust.name && !form.getFieldValue('recipientName')) {
                      form.setFieldValue('recipientName', cust.name)
                    }
                    if (cust && cust.phone && !form.getFieldValue('recipientPhone')) {
                      form.setFieldValue('recipientPhone', cust.phone)
                    }
                  }}
                  options={customers.map((c) => ({
                    value: c.id,
                    label: `${c.code} - ${c.name} (${c.customerType})`,
                  }))}
                />
              </Form.Item>
            </Col>

            <Col xs={24} sm={12} md={8}>
              <Form.Item
                name="warehouseId"
                label={
                  <span className="font-semibold text-slate-700">
                    <ShopOutlined className="mr-1 text-green-500" />
                    Kho xuất hàng
                  </span>
                }
              >
                <Select
                  allowClear
                  placeholder="Chọn kho xuất (để trống: tính ATP toàn hệ thống)"
                  onChange={handleWarehouseChange}
                  options={warehouses.map((w) => ({
                    value: w.id,
                    label: `${w.code} - ${w.name}`,
                  }))}
                />
              </Form.Item>
            </Col>

            <Col xs={24} sm={12} md={8}>
              <Form.Item
                name="soNumber"
                label={<span className="font-semibold text-slate-700">Mã đơn SO</span>}
                tooltip="Bỏ trống để hệ thống tự sinh mã theo mẫu SO-YYYYMMDD-XXXX"
              >
                <Input placeholder="Tự động sinh mã nếu để trống" disabled={isEdit} />
              </Form.Item>
            </Col>

            <Col xs={24} sm={12} md={8}>
              <Form.Item
                name="orderDate"
                label={
                  <span className="font-semibold text-slate-700">
                    <CalendarOutlined className="mr-1 text-blue-500" />
                    Ngày lập đơn
                  </span>
                }
                rules={[{ required: true, message: 'Vui lòng chọn ngày lập đơn' }]}
              >
                <DatePicker format="DD/MM/YYYY" className="w-full" />
              </Form.Item>
            </Col>

            <Col xs={24} sm={12} md={8}>
              <Form.Item
                name="expectedDate"
                label={
                  <span className="font-semibold text-slate-700">
                    <CalendarOutlined className="mr-1 text-indigo-500" />
                    Ngày dự kiến giao hàng
                  </span>
                }
              >
                <DatePicker format="DD/MM/YYYY" className="w-full" />
              </Form.Item>
            </Col>

            <Col xs={24} sm={12} md={8}>
              <Form.Item
                name="allowBackorder"
                valuePropName="checked"
                label={
                  <span className="font-semibold text-slate-700">
                    Cho phép chuyển Backorder
                  </span>
                }
                tooltip="Nếu thiếu hàng ATP, đơn sẽ chuyển sang trạng thái BACKORDER chờ nhập hàng bù thay vì báo lỗi chặn đơn"
              >
                <Switch
                  checkedChildren="Cho phép"
                  unCheckedChildren="Chặn khi thiếu"
                  defaultChecked
                />
              </Form.Item>
            </Col>

            <Col xs={24} sm={8}>
              <Form.Item
                name="recipientName"
                label={<span className="font-semibold text-slate-700">Người nhận hàng</span>}
              >
                <Input placeholder="Họ tên người nhận" />
              </Form.Item>
            </Col>

            <Col xs={24} sm={8}>
              <Form.Item
                name="recipientPhone"
                label={<span className="font-semibold text-slate-700">SĐT người nhận</span>}
              >
                <Input placeholder="Số điện thoại liên hệ" />
              </Form.Item>
            </Col>

            <Col xs={24} sm={8}>
              <Form.Item
                name="deliveryAddress"
                label={<span className="font-semibold text-slate-700">Địa chỉ giao hàng</span>}
              >
                <Input placeholder="Địa chỉ giao nhận hàng hóa" />
              </Form.Item>
            </Col>

            <Col xs={24}>
              <Form.Item
                name="note"
                label={<span className="font-semibold text-slate-700">Ghi chú đơn hàng</span>}
              >
                <TextArea rows={2} placeholder="Nhập ghi chú xuất hàng hoặc chỉ dẫn giao hàng..." />
              </Form.Item>
            </Col>
          </Row>

          {/* Cảnh báo hạn mức công nợ khách hàng (FR14) */}
          <CustomerCreditAlert creditInfo={creditInfo} loading={loadingCredit} />

          {/* Bảng chi tiết mặt hàng & Kiểm tra tồn khả dụng ATP tức thời (FR15) */}
          <div className="mt-4 pt-4 border-t border-slate-200">
            <SalesOrderItemsTable
              items={items}
              products={products}
              loadingProducts={loadingProducts}
              warehouseId={selectedWarehouseId}
              onSearchProduct={handleSearchProducts}
              onItemChange={handleItemChange}
              onProductSelect={handleProductSelect}
              onAddItem={handleAddItem}
              onRemoveItem={handleRemoveItem}
            />
          </div>

          <Divider />

          {/* Tổng kết tiền đơn hàng */}
          <Row gutter={24} align="bottom">
            <Col xs={24} md={12}>
              <div className="text-xs text-gray-500 space-y-1">
                <div>• Công thức tính: <Text code>Thành tiền = (Số lượng × Đơn giá - Chiết khấu) + VAT</Text></div>
                <div>• Tồn khả dụng: <Text code>ATP = OnHand (Tồn thực tế) - Reserved (Đã giữ chỗ)</Text></div>
                <div>• Khi duyệt đơn bán, số lượng hàng sẽ tự động được giữ chỗ (Soft Reservation).</div>
              </div>
            </Col>

            <Col xs={24} md={12}>
              <Card size="small" className="bg-slate-50 border-slate-200">
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-600">Tiền hàng trước thuế & CK:</span>
                    <span className="font-medium">{formatCurrency(orderTotals.subtotal)}</span>
                  </div>
                  <div className="flex justify-between text-amber-600">
                    <span>Tổng tiền chiết khấu:</span>
                    <span>- {formatCurrency(orderTotals.totalDiscount)}</span>
                  </div>
                  <div className="flex justify-between text-blue-600">
                    <span>Tổng thuế VAT:</span>
                    <span>+ {formatCurrency(orderTotals.totalTax)}</span>
                  </div>
                  <Divider className="my-2" />
                  <div className="flex justify-between items-baseline">
                    <span className="font-bold text-base text-slate-800">
                      <DollarOutlined className="mr-1 text-emerald-600" />
                      Tổng tiền thanh toán:
                    </span>
                    <span className="font-bold text-xl text-emerald-600">
                      {formatCurrency(orderTotals.grandTotal)}
                    </span>
                  </div>
                  <div className="text-right text-xs text-gray-500">
                    Tổng số lượng mặt hàng: {formatNumber(orderTotals.totalQuantity)} ({orderTotals.itemCount} dòng)
                  </div>
                </div>
              </Card>
            </Col>
          </Row>

          {/* Action buttons */}
          <div className="mt-6 flex justify-end gap-3">
            {onCancel && (
              <Button onClick={onCancel} disabled={submitting}>
                Hủy bỏ
              </Button>
            )}

            <Button
              icon={<SaveOutlined />}
              onClick={() => handleSubmit('DRAFT')}
              loading={submitting}
            >
              Lưu bản nháp (DRAFT)
            </Button>

            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={() => handleSubmit('CONFIRMED')}
              loading={submitting}
              className="bg-blue-600 hover:bg-blue-700"
            >
              {isEdit ? 'Cập nhật đơn bán' : 'Tạo & Xác nhận đơn (CONFIRMED)'}
            </Button>
          </div>
        </Card>
      </Form>
    </Spin>
  )
}
