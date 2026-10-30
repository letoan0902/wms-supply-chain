import { SwapOutlined } from '@ant-design/icons'
import {
  Alert,
  Button,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Switch,
  Typography,
} from 'antd'
import { useEffect } from 'react'
import type { Category, CreateProductPayload, Product, Uom, UpdateProductPayload } from '../types'

interface FormProductValues {
  sku: string
  barcode?: string
  name: string
  description?: string
  categoryId?: number
  uomId: number
  purchasePrice?: number
  sellingPrice?: number
  vatRate?: number
  imageUrl?: string
  manageByLot?: boolean
  manageByExpiration?: boolean
  safetyStock?: number
  reorderPoint?: number
  maxStock?: number
  active?: boolean
}

interface ProductFormModalProps {
  open: boolean
  editingProduct: Product | null
  categories: Category[]
  uoms: Uom[]
  submitting: boolean
  onCancel: () => void
  onSubmit: (payload: CreateProductPayload | UpdateProductPayload) => Promise<void>
  onOpenConversions?: (product: Product) => void
}

const { Text } = Typography

export default function ProductFormModal({
  open,
  editingProduct,
  categories,
  uoms,
  submitting,
  onCancel,
  onSubmit,
  onOpenConversions,
}: ProductFormModalProps) {
  const [form] = Form.useForm<FormProductValues>()

  useEffect(() => {
    if (open) {
      if (editingProduct) {
        form.setFieldsValue({
          sku: editingProduct.sku,
          barcode: editingProduct.barcode,
          name: editingProduct.name,
          description: editingProduct.description,
          categoryId: editingProduct.categoryId,
          uomId: editingProduct.uomId,
          purchasePrice: editingProduct.purchasePrice ?? 0,
          sellingPrice: editingProduct.sellingPrice ?? 0,
          vatRate: editingProduct.vatRate ?? 0,
          imageUrl: editingProduct.imageUrl,
          manageByLot: editingProduct.manageByLot ?? false,
          manageByExpiration: editingProduct.manageByExpiration ?? false,
          safetyStock: editingProduct.safetyStock ?? 0,
          reorderPoint: editingProduct.reorderPoint ?? 0,
          maxStock: editingProduct.maxStock ?? 0,
          active: editingProduct.active ?? true,
        })
      } else {
        form.resetFields()
        form.setFieldsValue({
          purchasePrice: 0,
          sellingPrice: 0,
          vatRate: 0,
          manageByLot: false,
          manageByExpiration: false,
          safetyStock: 0,
          reorderPoint: 0,
          maxStock: 0,
          active: true,
          uomId: uoms.length > 0 ? uoms[0].id : undefined,
        })
      }
    }
  }, [open, editingProduct, form, uoms])

  const handleFinish = async (values: FormProductValues) => {
    try {
      const payload: CreateProductPayload = {
        ...values,
        sku: values.sku.trim().toUpperCase(),
        barcode: values.barcode?.trim() || undefined,
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
        imageUrl: values.imageUrl?.trim() || undefined,
      }
      await onSubmit(payload)
      form.resetFields()
    } catch {
      // lỗi đã được xử lý ở component cha
    }
  }

  const imageUrl = Form.useWatch('imageUrl', form)

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <span className="text-lg font-semibold text-slate-800">
            {editingProduct ? 'Chỉnh sửa sản phẩm / SKU' : 'Thêm mới mã sản phẩm (SKU)'}
          </span>
          {editingProduct && (
            <span className="text-xs px-2 py-0.5 rounded bg-blue-100 text-blue-700 font-mono">
              {editingProduct.sku}
            </span>
          )}
        </div>
      }
      open={open}
      onCancel={onCancel}
      onOk={() => form.submit()}
      confirmLoading={submitting}
      width={780}
      okText={editingProduct ? 'Lưu thay đổi' : 'Tạo mới'}
      cancelText="Hủy bỏ"
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        initialValues={{
          purchasePrice: 0,
          sellingPrice: 0,
          vatRate: 0,
          manageByLot: false,
          manageByExpiration: false,
          safetyStock: 0,
          reorderPoint: 0,
          maxStock: 0,
          active: true,
        }}
      >
        {editingProduct && onOpenConversions && (
          <Alert
            type="info"
            showIcon
            className="mb-4"
            message="Cấu hình Quy đổi Đơn vị tính (FR06)"
            description={
              <div className="flex items-center justify-between mt-1">
                <span className="text-xs text-slate-600">
                  Thiết lập tỷ lệ đóng gói đa cấp (1 Thùng = 4 Lốc = 24 Chai) và tỷ lệ quy đổi tồn kho cho sản phẩm này.
                </span>
                <Button
                  size="small"
                  type="primary"
                  ghost
                  icon={<SwapOutlined />}
                  onClick={() => onOpenConversions(editingProduct)}
                >
                  Bảng quy đổi
                </Button>
              </div>
            }
          />
        )}

        <Divider orientation="left" plain className="!text-blue-600 !font-medium !text-sm">
          1. Thông tin cơ bản & Định danh
        </Divider>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="sku"
              label="Mã SKU (Duy nhất)"
              rules={[
                { required: true, message: 'Vui lòng nhập mã SKU' },
                {
                  pattern: /^[A-Za-z0-9-_]+$/,
                  message: 'Chỉ gồm chữ cái, số, dấu gạch ngang (-) và gạch dưới (_)',
                },
              ]}
              extra={<Text type="secondary" className="text-xs">VD: SKU-GAO-ST25-5KG, BEER-TIGER-330</Text>}
            >
              <Input
                placeholder="VD: SKU-SP-001"
                className="uppercase font-mono font-medium"
                maxLength={64}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="barcode"
              label="Mã vạch (Barcode / EAN-13)"
              rules={[{ max: 64, message: 'Tối đa 64 ký tự' }]}
              extra={<Text type="secondary" className="text-xs">Mã vạch in trên bao bì hàng hóa</Text>}
            >
              <Input placeholder="VD: 8938501234567" maxLength={64} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item
          name="name"
          label="Tên sản phẩm"
          rules={[{ required: true, message: 'Vui lòng nhập tên sản phẩm' }]}
        >
          <Input placeholder="VD: Gạo thơm ST25 Túi 5kg" maxLength={255} />
        </Form.Item>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="categoryId" label="Nhóm hàng / Danh mục">
              <Select
                placeholder="-- Chọn nhóm hàng --"
                allowClear
                options={categories.map((c) => ({
                  label: `${c.name} (${c.code})`,
                  value: c.id,
                }))}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="uomId"
              label="Đơn vị tính cơ sở (Base UoM)"
              rules={[{ required: true, message: 'Vui lòng chọn đơn vị tính' }]}
              extra={
                <Text type="secondary" className="text-xs">
                  Đơn vị nhỏ nhất dùng để lưu tồn kho (Chai, Cái, Lon...)
                </Text>
              }
            >
              <Select
                placeholder="-- Chọn đơn vị tính --"
                options={uoms.map((u) => ({
                  label: `${u.name} (${u.code})`,
                  value: u.id,
                }))}
              />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="description" label="Mô tả sản phẩm">
          <Input.TextArea rows={2} placeholder="Nhập mô tả chi tiết quy cách, xuất xứ..." maxLength={1000} />
        </Form.Item>

        <Divider orientation="left" plain className="!text-blue-600 !font-medium !text-sm">
          2. Giá cả & Thuế VAT
        </Divider>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="purchasePrice" label="Giá nhập tiêu chuẩn (VNĐ)">
              <InputNumber<number>
                className="w-full"
                min={0}
                step={1000}
                formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                parser={(val) => Number(val?.replace(/\$\s?|(,*)/g, '') || 0)}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="sellingPrice" label="Giá bán niêm yết (VNĐ)">
              <InputNumber<number>
                className="w-full"
                min={0}
                step={1000}
                formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                parser={(val) => Number(val?.replace(/\$\s?|(,*)/g, '') || 0)}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="vatRate" label="Thuế suất VAT (%)">
              <InputNumber className="w-full" min={0} max={100} step={1} addonAfter="%" />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left" plain className="!text-blue-600 !font-medium !text-sm">
          3. Chính sách kho & Định mức tồn
        </Divider>

        <Row gutter={16} className="bg-slate-50 p-3 rounded-lg mb-4">
          <Col span={12}>
            <Form.Item
              name="manageByLot"
              label="Quản lý theo Lô hàng (Batch/Lot)"
              valuePropName="checked"
              className="mb-0"
              extra="Bật cờ này để bắt buộc nhập số lô khi nhập kho (GRN)"
            >
              <Switch checkedChildren="Bật" unCheckedChildren="Tắt" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="manageByExpiration"
              label="Quản lý Hạn sử dụng (Exp Date)"
              valuePropName="checked"
              className="mb-0"
              extra="Bắt buộc theo dõi hạn sử dụng và xuất hàng theo FEFO"
            >
              <Switch checkedChildren="Bật" unCheckedChildren="Tắt" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              name="safetyStock"
              label="Tồn an toàn (Safety Stock)"
              extra="Cảnh báo khi tồn kho giảm xuống dưới mức này"
            >
              <InputNumber className="w-full" min={0} step={1} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item
              name="reorderPoint"
              label="Điểm đặt hàng lại (Reorder Point)"
              extra="Ngưỡng kích hoạt tạo đề xuất mua hàng (PO)"
            >
              <InputNumber className="w-full" min={0} step={1} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item
              name="maxStock"
              label="Tồn tối đa (Max Stock)"
              extra="Tránh lưu kho quá tải gây ứ đọng vốn"
            >
              <InputNumber className="w-full" min={0} step={1} />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16} className="items-center">
          <Col span={18}>
            <Form.Item name="imageUrl" label="URL Ảnh sản phẩm" rules={[{ max: 500 }]}>
              <Input placeholder="https://example.com/image.png" />
            </Form.Item>
          </Col>
          <Col span={6} className="flex justify-center">
            {imageUrl ? (
              <img
                src={imageUrl}
                alt="preview"
                className="w-16 h-16 object-cover rounded border border-slate-200"
                onError={(e) => {
                  ;(e.target as HTMLElement).style.display = 'none'
                }}
              />
            ) : (
              <div className="w-16 h-16 rounded border border-dashed border-slate-300 flex items-center justify-center text-xs text-slate-400">
                Ảnh xem trước
              </div>
            )}
          </Col>
        </Row>

        {editingProduct && (
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="active"
                label="Trạng thái kinh doanh"
                valuePropName="checked"
              >
                <Switch checkedChildren="Đang hoạt động" unCheckedChildren="Ngưng hoạt động" />
              </Form.Item>
            </Col>
          </Row>
        )}
      </Form>
    </Modal>
  )
}
