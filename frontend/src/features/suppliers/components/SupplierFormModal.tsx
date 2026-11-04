import {
  BankOutlined,
  ContactsOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import {
  AutoComplete,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Switch,
} from 'antd'
import { useEffect } from 'react'
import type { CreateSupplierPayload, Supplier, UpdateSupplierPayload } from '../types'

interface Props {
  open: boolean
  editingSupplier: Supplier | null
  submitting: boolean
  onCancel: () => void
  onSubmit: (values: CreateSupplierPayload | UpdateSupplierPayload) => Promise<void>
}

const PAYMENT_TERMS_OPTIONS = [
  { value: 'NET_15', label: 'NET 15 (Thanh toán sau 15 ngày)' },
  { value: 'NET_30', label: 'NET 30 (Thanh toán sau 30 ngày)' },
  { value: 'NET_45', label: 'NET 45 (Thanh toán sau 45 ngày)' },
  { value: 'NET_60', label: 'NET 60 (Thanh toán sau 60 ngày)' },
  { value: 'COD', label: 'COD (Thanh toán ngay khi giao hàng)' },
  { value: 'PREPAID', label: 'Thanh toán trước khi giao hàng (Trả trước 100%)' },
]

export default function SupplierFormModal({
  open,
  editingSupplier,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  const [form] = Form.useForm()
  const isEditing = Boolean(editingSupplier)

  useEffect(() => {
    if (open) {
      if (editingSupplier) {
        form.setFieldsValue({
          code: editingSupplier.code,
          name: editingSupplier.name,
          taxCode: editingSupplier.taxCode ?? '',
          contactName: editingSupplier.contactName ?? '',
          phone: editingSupplier.phone ?? '',
          email: editingSupplier.email ?? '',
          address: editingSupplier.address ?? '',
          bankAccount: editingSupplier.bankAccount ?? '',
          bankName: editingSupplier.bankName ?? '',
          paymentTerms: editingSupplier.paymentTerms ?? 'NET_30',
          creditLimit: editingSupplier.creditLimit ?? 0,
          active: editingSupplier.active,
        })
      } else {
        form.resetFields()
        form.setFieldsValue({
          paymentTerms: 'NET_30',
          creditLimit: 0,
          initialDebt: 0,
          active: true,
        })
      }
    }
  }, [open, editingSupplier, form])

  const handleFinish = async (values: Record<string, unknown>) => {
    if (isEditing) {
      const payload: UpdateSupplierPayload = {
        name: String(values.name).trim(),
        taxCode: values.taxCode ? String(values.taxCode).trim() : undefined,
        contactName: values.contactName ? String(values.contactName).trim() : undefined,
        email: values.email ? String(values.email).trim() : undefined,
        phone: values.phone ? String(values.phone).trim() : undefined,
        address: values.address ? String(values.address).trim() : undefined,
        bankAccount: values.bankAccount ? String(values.bankAccount).trim() : undefined,
        bankName: values.bankName ? String(values.bankName).trim() : undefined,
        paymentTerms: values.paymentTerms ? String(values.paymentTerms).trim() : undefined,
        creditLimit: Number(values.creditLimit ?? 0),
        active: Boolean(values.active ?? true),
      }
      await onSubmit(payload)
    } else {
      const payload: CreateSupplierPayload = {
        code: String(values.code).trim().toUpperCase(),
        name: String(values.name).trim(),
        taxCode: values.taxCode ? String(values.taxCode).trim() : undefined,
        contactName: values.contactName ? String(values.contactName).trim() : undefined,
        email: values.email ? String(values.email).trim() : undefined,
        phone: values.phone ? String(values.phone).trim() : undefined,
        address: values.address ? String(values.address).trim() : undefined,
        bankAccount: values.bankAccount ? String(values.bankAccount).trim() : undefined,
        bankName: values.bankName ? String(values.bankName).trim() : undefined,
        paymentTerms: values.paymentTerms ? String(values.paymentTerms).trim() : undefined,
        creditLimit: Number(values.creditLimit ?? 0),
        initialDebt: Number(values.initialDebt ?? 0),
      }
      await onSubmit(payload)
    }
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-lg font-semibold text-slate-800">
          <ContactsOutlined className="text-blue-600" />
          <span>{isEditing ? 'Cập nhật hồ sơ Nhà cung cấp' : 'Thêm mới Nhà cung cấp'}</span>
        </div>
      }
      open={open}
      onCancel={onCancel}
      onOk={() => form.submit()}
      confirmLoading={submitting}
      okText={isEditing ? 'Lưu thay đổi' : 'Thêm mới'}
      cancelText="Hủy bỏ"
      width={720}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        className="mt-4"
        requiredMark="optional"
      >
        {/* 1. Thông tin doanh nghiệp */}
        <div className="text-xs font-semibold text-blue-700 uppercase tracking-wider mb-2 flex items-center gap-1">
          <InfoCircleOutlined /> Thông tin doanh nghiệp
        </div>

        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Form.Item
              name="code"
              label="Mã nhà cung cấp"
              rules={[
                { required: !isEditing, message: 'Vui lòng nhập mã nhà cung cấp' },
                { max: 30, message: 'Tối đa 30 ký tự' },
                {
                  pattern: /^[a-zA-Z0-9_-]+$/,
                  message: 'Chỉ gồm chữ cái, số, gạch ngang (-) hoặc gạch dưới (_)',
                },
              ]}
              extra={isEditing ? 'Mã định danh không thể thay đổi' : 'Tự động viết hoa (VD: SUP-VINAMILK)'}
            >
              <Input
                placeholder="VD: SUP-VINAMILK"
                disabled={isEditing}
                style={{ textTransform: 'uppercase' }}
              />
            </Form.Item>
          </Col>

          <Col xs={24} sm={16}>
            <Form.Item
              name="name"
              label="Tên nhà cung cấp / Công ty"
              rules={[
                { required: true, message: 'Vui lòng nhập tên nhà cung cấp' },
                { max: 150, message: 'Tối đa 150 ký tự' },
              ]}
            >
              <Input placeholder="VD: Công ty Cổ phần Sữa Việt Nam" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="taxCode"
              label="Mã số thuế (MST)"
              rules={[{ max: 30, message: 'Tối đa 30 ký tự' }]}
            >
              <Input placeholder="VD: 0300588569" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="contactName"
              label="Người liên hệ / Đại diện"
              rules={[{ max: 100, message: 'Tối đa 100 ký tự' }]}
            >
              <Input placeholder="VD: Nguyễn Văn Bình" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="phone"
              label="Số điện thoại"
              rules={[{ max: 30, message: 'Tối đa 30 ký tự' }]}
            >
              <Input placeholder="VD: 02854155555 hoặc 0912345678" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="email"
              label="Email liên hệ"
              rules={[
                { type: 'email', message: 'Email không hợp lệ' },
                { max: 100, message: 'Tối đa 100 ký tự' },
              ]}
            >
              <Input placeholder="VD: contact@vinamilk.com.vn" />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item
          name="address"
          label="Địa chỉ trụ sở / Kho hàng"
          rules={[{ max: 255, message: 'Tối đa 255 ký tự' }]}
        >
          <Input placeholder="VD: Số 10 Tân Trào, P. Tân Phú, Quận 7, TP. Hồ Chí Minh" />
        </Form.Item>

        <Divider className="my-3" />

        {/* 2. Tài chính & Ngân hàng */}
        <div className="text-xs font-semibold text-blue-700 uppercase tracking-wider mb-2 flex items-center gap-1">
          <BankOutlined /> Thông tin tài khoản & Điều khoản công nợ
        </div>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="bankAccount"
              label="Số tài khoản ngân hàng"
              rules={[{ max: 50, message: 'Tối đa 50 ký tự' }]}
            >
              <Input placeholder="VD: 19034567890012" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="bankName"
              label="Tên ngân hàng & Chi nhánh"
              rules={[{ max: 100, message: 'Tối đa 100 ký tự' }]}
            >
              <Input placeholder="VD: Techcombank - Chi nhánh Sài Gòn" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="paymentTerms"
              label="Điều khoản thanh toán"
              rules={[{ max: 50, message: 'Tối đa 50 ký tự' }]}
            >
              <AutoComplete
                options={PAYMENT_TERMS_OPTIONS}
                placeholder="Chọn hoặc nhập điều khoản (VD: NET_30)"
                filterOption={(inputValue, option) =>
                  String(option?.label ?? '')
                    .toLowerCase()
                    .includes(inputValue.toLowerCase()) ||
                  String(option?.value ?? '')
                    .toLowerCase()
                    .includes(inputValue.toLowerCase())
                }
              />
            </Form.Item>
          </Col>

          <Col xs={24} sm={12}>
            <Form.Item
              name="creditLimit"
              label="Hạn mức nợ tối đa (Credit Limit - VNĐ)"
              rules={[{ required: true, message: 'Vui lòng nhập hạn mức nợ' }]}
              extra="Hạn mức tín dụng NCC cấp cho kho (0 nếu thanh toán ngay)"
            >
              <InputNumber<number>
                className="w-full"
                min={0}
                step={1000000}
                formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                parser={(val) => Number(val?.replace(/\$\s?|(,*)/g, '') || 0)}
                addonAfter="₫"
              />
            </Form.Item>
          </Col>
        </Row>

        {!isEditing && (
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="initialDebt"
                label="Công nợ ban đầu (VNĐ)"
                extra="Số dư công nợ còn nợ NCC tại thời điểm nhập hệ thống"
              >
                <InputNumber<number>
                  className="w-full"
                  min={0}
                  step={1000000}
                  formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                  parser={(val) => Number(val?.replace(/\$\s?|(,*)/g, '') || 0)}
                  addonAfter="₫"
                />
              </Form.Item>
            </Col>
          </Row>
        )}

        {isEditing && (
          <Row gutter={16} className="mt-2">
            <Col xs={24} sm={12}>
              <Form.Item
                name="active"
                label="Trạng thái hoạt động"
                valuePropName="checked"
                extra="Tắt để tạm dừng giao dịch và đặt hàng từ nhà cung cấp này"
              >
                <Switch checkedChildren="Đang hoạt động" unCheckedChildren="Tạm dừng" />
              </Form.Item>
            </Col>
          </Row>
        )}
      </Form>
    </Modal>
  )
}
