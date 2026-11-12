import {
  BankOutlined,
  IdcardOutlined,
  InfoCircleOutlined,
  MailOutlined,
  PhoneOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Switch,
  Tooltip,
} from 'antd'
import { useEffect } from 'react'
import type {
  CreateCustomerPayload,
  Customer,
  CustomerType,
  UpdateCustomerPayload,
} from '../types'

interface CustomerFormModalProps {
  open: boolean
  initialData: Customer | null
  submitting: boolean
  onCancel: () => void
  onSubmit: (values: CreateCustomerPayload | UpdateCustomerPayload) => Promise<void>
}

const PAYMENT_TERM_OPTIONS = [
  { label: 'Thanh toán ngay khi giao hàng (COD)', value: 'COD' },
  { label: 'Thanh toán trước 100% (PREPAID)', value: 'PREPAID' },
  { label: 'Công nợ 15 ngày (NET_15)', value: 'NET_15' },
  { label: 'Công nợ 30 ngày (NET_30)', value: 'NET_30' },
  { label: 'Công nợ 45 ngày (NET_45)', value: 'NET_45' },
  { label: 'Công nợ 60 ngày (NET_60)', value: 'NET_60' },
]

export default function CustomerFormModal({
  open,
  initialData,
  submitting,
  onCancel,
  onSubmit,
}: CustomerFormModalProps) {
  const [form] = Form.useForm()
  const isEditing = Boolean(initialData)

  useEffect(() => {
    if (open) {
      if (initialData) {
        form.setFieldsValue({
          code: initialData.code,
          name: initialData.name,
          customerType: initialData.customerType,
          taxCode: initialData.taxCode ?? '',
          contactName: initialData.contactName ?? '',
          email: initialData.email ?? '',
          phone: initialData.phone ?? '',
          address: initialData.address ?? '',
          paymentTerms: initialData.paymentTerms ?? 'NET_30',
          creditLimit: initialData.creditLimit ?? 0,
          debtLocked: initialData.debtLocked ?? false,
          active: initialData.active ?? true,
          notes: initialData.notes ?? '',
        })
      } else {
        form.resetFields()
        form.setFieldsValue({
          customerType: 'WHOLESALE',
          paymentTerms: 'NET_30',
          creditLimit: 50000000,
          initialDebt: 0,
          active: true,
          debtLocked: false,
        })
      }
    }
  }, [open, initialData, form])

  const handleFinish = async () => {
    try {
      const values = await form.validateFields()
      if (isEditing) {
        const payload: UpdateCustomerPayload = {
          name: values.name.trim(),
          customerType: values.customerType,
          taxCode: values.taxCode?.trim() || undefined,
          contactName: values.contactName?.trim() || undefined,
          email: values.email?.trim() || undefined,
          phone: values.phone?.trim() || undefined,
          address: values.address?.trim() || undefined,
          paymentTerms: values.paymentTerms?.trim() || undefined,
          creditLimit: values.creditLimit ?? 0,
          debtLocked: values.debtLocked ?? false,
          active: values.active ?? true,
          notes: values.notes?.trim() || undefined,
        }
        await onSubmit(payload)
      } else {
        const payload: CreateCustomerPayload = {
          code: values.code.trim().toUpperCase(),
          name: values.name.trim(),
          customerType: values.customerType,
          taxCode: values.taxCode?.trim() || undefined,
          contactName: values.contactName?.trim() || undefined,
          email: values.email?.trim() || undefined,
          phone: values.phone?.trim() || undefined,
          address: values.address?.trim() || undefined,
          paymentTerms: values.paymentTerms?.trim() || undefined,
          creditLimit: values.creditLimit ?? 0,
          initialDebt: values.initialDebt ?? 0,
          notes: values.notes?.trim() || undefined,
        }
        await onSubmit(payload)
      }
    } catch {
      // Validate form error, antd tự hiển thị thông báo dưới trường
    }
  }

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center gap-2 text-base font-semibold text-gray-800">
          <UserOutlined className="text-blue-600" />
          <span>
            {isEditing
              ? `Chỉnh sửa hồ sơ: ${initialData?.name} (${initialData?.code})`
              : 'Thêm mới khách hàng / đại lý'}
          </span>
        </div>
      }
      onCancel={onCancel}
      onOk={handleFinish}
      confirmLoading={submitting}
      okText={isEditing ? 'Lưu thay đổi' : 'Tạo khách hàng'}
      cancelText="Hủy bỏ"
      width={780}
      destroyOnClose
    >
      <Form form={form} layout="vertical" className="pt-2">
        {/* Phân loại khách hàng */}
        <div className="p-3 bg-blue-50/50 rounded-lg border border-blue-100 mb-4">
          <Form.Item
            name="customerType"
            label={
              <span className="font-medium text-gray-700">
                Phân loại khách hàng
              </span>
            }
            className="mb-0"
            rules={[{ required: true, message: 'Vui lòng chọn phân loại khách hàng' }]}
          >
            <Radio.Group
              onChange={(e) => {
                const val = e.target.value as CustomerType
                if (val === 'RETAIL' && !isEditing) {
                  form.setFieldsValue({ creditLimit: 0, paymentTerms: 'COD' })
                } else if (val === 'WHOLESALE' && !isEditing) {
                  form.setFieldsValue({ creditLimit: 100000000, paymentTerms: 'NET_30' })
                }
              }}
            >
              <Radio value="WHOLESALE">
                <span className="font-semibold text-gray-800">Khách sỉ / Đại lý / B2B</span>
                <span className="text-xs text-gray-500 block">
                  Áp dụng hợp đồng hạn mức công nợ trả chậm (Credit Limit)
                </span>
              </Radio>
              <Radio value="RETAIL" className="mt-2 sm:mt-0">
                <span className="font-semibold text-gray-800">Khách lẻ / Cá nhân B2C</span>
                <span className="text-xs text-gray-500 block">
                  Thanh toán ngay bằng tiền mặt / COD / Chuyển khoản (Hạn mức = 0)
                </span>
              </Radio>
            </Radio.Group>
          </Form.Item>
        </div>

        {/* Thông tin cơ bản */}
        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Form.Item
              name="code"
              label="Mã khách hàng"
              rules={[
                { required: !isEditing, message: 'Vui lòng nhập mã khách hàng' },
                { max: 30, message: 'Tối đa 30 ký tự' },
                {
                  pattern: /^[A-Za-z0-9_-]+$/,
                  message: 'Chỉ chứa chữ cái, số, gạch ngang (- hoặc _)',
                },
              ]}
              extra={!isEditing ? 'Tự động viết hoa, vd: CUST-BHX-01' : undefined}
            >
              <Input
                placeholder="VD: CUST-BHX-01"
                disabled={isEditing}
                prefix={<IdcardOutlined className="text-gray-400" />}
                onChange={(e) => {
                  form.setFieldsValue({ code: e.target.value.toUpperCase() })
                }}
              />
            </Form.Item>
          </Col>

          <Col xs={24} sm={16}>
            <Form.Item
              name="name"
              label="Tên khách hàng / Tên doanh nghiệp"
              rules={[
                { required: true, message: 'Vui lòng nhập tên khách hàng' },
                { max: 150, message: 'Tối đa 150 ký tự' },
              ]}
            >
              <Input placeholder="VD: Công ty CP Bách Hóa Xanh hoặc Nguyễn Văn A" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="taxCode"
              label="Mã số thuế"
              rules={[{ max: 30, message: 'Mã số thuế tối đa 30 ký tự' }]}
            >
              <Input
                placeholder="VD: 0310471746"
                prefix={<BankOutlined className="text-gray-400" />}
              />
            </Form.Item>
          </Col>

          <Col xs={24} sm={12}>
            <Form.Item
              name="contactName"
              label="Người liên hệ / Đại diện"
              rules={[{ max: 100, message: 'Tối đa 100 ký tự' }]}
            >
              <Input placeholder="VD: Trần Thị Mai (Trưởng phòng Thu mua)" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="phone"
              label="Số điện thoại"
              rules={[
                { max: 30, message: 'Tối đa 30 ký tự' },
                {
                  pattern: /^[0-9+() -]+$/,
                  message: 'Số điện thoại không hợp lệ',
                },
              ]}
            >
              <Input
                placeholder="VD: 02838123456"
                prefix={<PhoneOutlined className="text-gray-400" />}
              />
            </Form.Item>
          </Col>

          <Col xs={24} sm={12}>
            <Form.Item
              name="email"
              label="Email liên hệ"
              rules={[
                { type: 'email', message: 'Email không đúng định dạng' },
                { max: 100, message: 'Tối đa 100 ký tự' },
              ]}
            >
              <Input
                placeholder="VD: procurement@bachhoaxanh.com"
                prefix={<MailOutlined className="text-gray-400" />}
              />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item
          name="address"
          label="Địa chỉ giao hàng / Trụ sở"
          rules={[{ max: 255, message: 'Tối đa 255 ký tự' }]}
        >
          <Input placeholder="VD: 128 Trần Quang Khải, P. Tân Định, Quận 1, TP.HCM" />
        </Form.Item>

        <Divider className="my-3">
          <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
            Cấu hình hạn mức nợ & Điều khoản thanh toán
          </span>
        </Divider>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="creditLimit"
              label={
                <Space size={4}>
                  <span>Hạn mức công nợ tối đa (Credit Limit)</span>
                  <Tooltip title="Số tiền nợ tối đa cho phép khách hàng được nợ tại bất kỳ thời điểm nào. Khi nợ đạt 90% sẽ cảnh báo, vượt 100% hệ thống sẽ tự động chặn đơn bán hàng theo quy định quản lý công nợ.">
                    <InfoCircleOutlined className="text-blue-500" />
                  </Tooltip>
                </Space>
              }
              rules={[{ required: true, message: 'Vui lòng nhập hạn mức nợ' }]}
              extra={
                <div className="flex flex-wrap gap-1 mt-1">
                  <span className="text-xs text-gray-400 mr-1">Gợi ý nhanh:</span>
                  {[0, 20000000, 50000000, 100000000, 500000000].map((v) => (
                    <button
                      type="button"
                      key={v}
                      onClick={() => form.setFieldsValue({ creditLimit: v })}
                      className="text-[11px] bg-gray-100 hover:bg-blue-100 text-gray-700 px-1.5 py-0.5 rounded border border-gray-200"
                    >
                      {v === 0
                        ? '0 đ (Thanh toán ngay)'
                        : `${(v / 1000000).toLocaleString('vi-VN')} triệu`}
                    </button>
                  ))}
                </div>
              }
            >
              <InputNumber<number>
                className="w-full font-semibold text-blue-700"
                size="large"
                min={0}
                step={5000000}
                formatter={(val) =>
                  val !== undefined ? `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''
                }
                parser={(val) => {
                  const cleaned = val?.replace(/\$\s?|(,*)/g, '') ?? '0'
                  return Number(cleaned) || 0
                }}
                addonAfter="VNĐ"
              />
            </Form.Item>
          </Col>

          <Col xs={24} sm={12}>
            <Form.Item
              name="paymentTerms"
              label="Điều khoản thanh toán (Payment Terms)"
              rules={[{ max: 50, message: 'Tối đa 50 ký tự' }]}
            >
              <Select
                placeholder="Chọn hoặc nhập điều khoản"
                allowClear
                options={PAYMENT_TERM_OPTIONS}
              />
            </Form.Item>
          </Col>
        </Row>

        {!isEditing && (
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="initialDebt"
                label={
                  <Space size={4}>
                    <span>Công nợ ban đầu (Initial Debt)</span>
                    <Tooltip title="Nhập số dư nợ cũ chuyển sang từ hệ thống trước đây (nếu có). Khoản này sẽ được ghi nhận vào lịch sử biến động công nợ.">
                      <InfoCircleOutlined className="text-gray-400" />
                    </Tooltip>
                  </Space>
                }
              >
                <InputNumber<number>
                  className="w-full"
                  min={0}
                  formatter={(val) =>
                    val !== undefined ? `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''
                  }
                  parser={(val) => {
                    const cleaned = val?.replace(/\$\s?|(,*)/g, '') ?? '0'
                    return Number(cleaned) || 0
                  }}
                  addonAfter="VNĐ"
                />
              </Form.Item>
            </Col>
          </Row>
        )}

        {isEditing && (
          <div className="bg-amber-50/60 p-3 rounded-lg border border-amber-200 mb-4">
            <Row gutter={16} align="middle">
              <Col xs={24} sm={12}>
                <Form.Item
                  name="debtLocked"
                  valuePropName="checked"
                  label={
                    <span className="font-medium text-red-700">
                      Khóa nợ chặn bán hàng
                    </span>
                  }
                  className="mb-0"
                  extra="Khi bật cờ này, khách hàng sẽ bị chặn hoàn toàn không thể tạo hoặc duyệt đơn bán hàng SO mới."
                >
                  <Switch
                    checkedChildren="ĐANG KHÓA NỢ"
                    unCheckedChildren="Không khóa"
                    className="bg-gray-300"
                  />
                </Form.Item>
              </Col>

              <Col xs={24} sm={12}>
                <Form.Item
                  name="active"
                  valuePropName="checked"
                  label={<span className="font-medium text-gray-700">Trạng thái hợp tác</span>}
                  className="mb-0"
                  extra="Chuyển sang ngưng hoạt động nếu khách hàng đã ngừng giao dịch."
                >
                  <Switch
                    checkedChildren="Đang hợp tác"
                    unCheckedChildren="Ngưng hợp tác"
                    className="bg-gray-300"
                  />
                </Form.Item>
              </Col>
            </Row>
          </div>
        )}

        <Form.Item
          name="notes"
          label="Ghi chú về khách hàng"
          rules={[{ max: 500, message: 'Ghi chú tối đa 500 ký tự' }]}
        >
          <Input.TextArea
            rows={2}
            placeholder="Ghi chú đặc thù, chính sách chiết khấu riêng hoặc người phụ trách..."
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}
