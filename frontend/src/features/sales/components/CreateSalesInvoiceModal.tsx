import {
  DollarOutlined,
  FileDoneOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Select,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency } from '../../../utils/format'
import * as customerApi from '../../customers/customerApi'
import type { Customer, CustomerCreditCheckResponse } from '../../customers/types'
import type { SalesOrder } from '../types'

interface Props {
  open: boolean
  order?: SalesOrder | null
  customer?: Customer | null
  onClose: () => void
  onSuccess?: () => void
}

interface FormValues {
  customerId: number
  referenceCode: string
  amount: number
  type: 'SALES_INVOICE' | 'PAYMENT'
  note?: string
}

export default function CreateSalesInvoiceModal({
  open,
  order,
  customer,
  onClose,
  onSuccess,
}: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(false)
  const [customers, setCustomers] = useState<Customer[]>([])
  const [creditInfo, setCreditInfo] = useState<CustomerCreditCheckResponse | null>(null)

  // Tải danh mục khách hàng nếu chưa có
  useEffect(() => {
    if (!open) return
    let ignore = false
    const loadCustomers = async () => {
      try {
        const res = await customerApi.fetchCustomers({ active: true, size: 100 })
        if (!ignore) {
          setCustomers(res.content || [])
        }
      } catch {
        // Bỏ qua lỗi tải khách hàng
      }
    }
    void loadCustomers()
    return () => {
      ignore = true
    }
  }, [open])

  // Thiết lập giá trị mặc định cho form
  useEffect(() => {
    if (!open) return

    const initialCustomerId = order?.customerId || customer?.id || undefined
    const autoRef = order
      ? `INV-${order.soNumber}`
      : `INV-${dayjs().format('YYYYMMDD')}-${Math.floor(100 + Math.random() * 900)}`

    form.setFieldsValue({
      customerId: initialCustomerId,
      referenceCode: autoRef,
      amount: order?.totalAmount ?? 0,
      type: 'SALES_INVOICE',
      note: order ? `Xuất hóa đơn bán hàng cho đơn ${order.soNumber}` : 'Xuất hóa đơn bán hàng',
    })

    if (initialCustomerId) {
      let ignore = false
      void customerApi
        .checkCustomerCredit(initialCustomerId, order?.totalAmount ?? 0)
        .then((res) => {
          if (!ignore) setCreditInfo(res)
        })
        .catch(() => {
          if (!ignore) setCreditInfo(null)
        })
      return () => {
        ignore = true
      }
    }
  }, [open, order, customer, form])

  const handleCustomerChange = async (cid: number) => {
    try {
      const amount = form.getFieldValue('amount') || 0
      const res = await customerApi.checkCustomerCredit(cid, amount)
      setCreditInfo(res)
    } catch {
      setCreditInfo(null)
    }
  }

  const handleSubmit = async (values: FormValues) => {
    setLoading(true)
    try {
      await customerApi.recordCustomerTransaction(values.customerId, {
        type: values.type,
        amount: values.amount,
        referenceCode: values.referenceCode.trim(),
        note: values.note?.trim(),
      })

      const actionName =
        values.type === 'SALES_INVOICE'
          ? 'Tạo Hóa đơn bán (Ghi nợ phải thu - UNPAID)'
          : 'Ghi nhận thanh toán (PAID)'

      message.success(`${actionName} thành công cho mã chứng từ "${values.referenceCode}"!`)
      onSuccess?.()
      onClose()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi tạo hóa đơn / ghi nhận công nợ'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center gap-2 text-slate-800 text-base font-semibold">
          <FileDoneOutlined className="text-blue-600" />
          <span>Tạo Hóa Đơn Bán Hàng & Ghi Nhận Công Nợ (FR18)</span>
        </div>
      }
      onCancel={() => {
        setCreditInfo(null)
        onClose()
      }}
      width={680}
      destroyOnClose
      footer={null}
    >
      <div className="pt-2 space-y-4">
        {/* Banner tóm tắt đơn bán nếu có */}
        {order && (
          <Alert
            type="info"
            showIcon
            icon={<InfoCircleOutlined />}
            message={
              <div className="flex justify-between items-center text-xs">
                <span>
                  Đơn bán hàng liên kết: <strong>{order.soNumber}</strong> | Trạng thái:{' '}
                  <span className="text-blue-600 font-medium">{order.status}</span>
                </span>
                <span className="font-semibold text-slate-700">
                  Tổng tiền đơn: {formatCurrency(order.totalAmount)}
                </span>
              </div>
            }
          />
        )}

        {/* Cảnh báo công nợ hiện tại của khách hàng */}
        {creditInfo && (
          <Card size="small" className="bg-slate-50 border-slate-200">
            <Row gutter={[12, 8]} className="text-xs">
              <Col span={8}>
                <span className="text-gray-500">Hạn mức nợ: </span>
                <strong className="text-slate-700">{formatCurrency(creditInfo.creditLimit)}</strong>
              </Col>
              <Col span={8}>
                <span className="text-gray-500">Công nợ hiện tại: </span>
                <strong className="text-amber-600">{formatCurrency(creditInfo.currentDebt)}</strong>
              </Col>
              <Col span={8}>
                <span className="text-gray-500">Dư mức nợ còn lại: </span>
                <strong className="text-green-600">{formatCurrency(creditInfo.availableCredit)}</strong>
              </Col>
            </Row>
          </Card>
        )}

        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          requiredMark="optional"
          className="mt-2"
        >
          <Row gutter={16}>
            <Col span={14}>
              <Form.Item
                name="customerId"
                label="Khách hàng / Đại lý"
                rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}
              >
                <Select
                  placeholder="Chọn khách hàng"
                  showSearch
                  disabled={Boolean(order?.customerId || customer?.id)}
                  onChange={handleCustomerChange}
                  filterOption={(input, option) =>
                    (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
                  }
                  options={customers.map((c) => ({
                    value: c.id,
                    label: `${c.code} - ${c.name} (Nợ: ${formatCurrency(c.currentDebt)})`,
                  }))}
                />
              </Form.Item>
            </Col>

            <Col span={10}>
              <Form.Item
                name="referenceCode"
                label="Số Hóa đơn / Mã chứng từ"
                rules={[
                  { required: true, message: 'Vui lòng nhập số hóa đơn' },
                  { max: 50, message: 'Tối đa 50 ký tự' },
                ]}
              >
                <Input placeholder="Ví dụ: INV-SO-20261105-01" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={14}>
              <Form.Item
                name="type"
                label="Hình thức ghi nhận hóa đơn (FR18)"
                rules={[{ required: true, message: 'Vui lòng chọn hình thức' }]}
              >
                <Radio.Group className="w-full">
                  <div className="space-y-2">
                    <Radio value="SALES_INVOICE">
                      <div>
                        <span className="font-medium text-slate-800">
                          Bán nợ (Tăng công nợ phải thu - UNPAID)
                        </span>
                        <div className="text-[11px] text-gray-500">
                          Ghi nhận hóa đơn bán, cộng số tiền vào công nợ phải thu của khách hàng.
                        </div>
                      </div>
                    </Radio>
                    <Radio value="PAYMENT">
                      <div>
                        <span className="font-medium text-slate-800">
                          Thu tiền ngay (Thanh toán hoàn tất - PAID)
                        </span>
                        <div className="text-[11px] text-gray-500">
                          Khách hàng thanh toán tiền mặt/chuyển khoản ngay, không tăng nợ.
                        </div>
                      </div>
                    </Radio>
                  </div>
                </Radio.Group>
              </Form.Item>
            </Col>

            <Col span={10}>
              <Form.Item
                name="amount"
                label="Số tiền hóa đơn (VNĐ)"
                rules={[
                  { required: true, message: 'Vui lòng nhập số tiền' },
                  {
                    type: 'number',
                    min: 1,
                    message: 'Số tiền phải lớn hơn 0',
                  },
                ]}
              >
                <InputNumber
                  className="w-full"
                  formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
                  parser={(val) => Number(val?.replace(/\./g, '') || 0)}
                  placeholder="0"
                  prefix={<DollarOutlined className="text-gray-400" />}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="note"
            label="Ghi chú / Diễn giải"
            rules={[{ max: 500, message: 'Tối đa 500 ký tự' }]}
          >
            <Input.TextArea
              rows={2}
              placeholder="Ví dụ: Xuất hóa đơn bán hàng theo đơn SO-20261105-01"
            />
          </Form.Item>

          <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
            <Button
              onClick={() => {
                setCreditInfo(null)
                onClose()
              }}
              disabled={loading}
            >
              Hủy bỏ
            </Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              icon={<FileDoneOutlined />}
              className="bg-blue-600 hover:bg-blue-700 font-medium"
            >
              Phát hành Hóa đơn
            </Button>
          </div>
        </Form>
      </div>
    </Modal>
  )
}
