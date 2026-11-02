import { Form, Input, InputNumber, Modal, Switch } from 'antd'
import { useEffect } from 'react'
import type { CreateWarehousePayload, UpdateWarehousePayload, Warehouse } from '../types'

interface Props {
  open: boolean
  warehouse: Warehouse | null
  loading: boolean
  onClose: () => void
  onSubmit: (values: CreateWarehousePayload | UpdateWarehousePayload) => Promise<void>
}

type WarehouseFormValues = CreateWarehousePayload

export default function WarehouseFormModal({
  open,
  warehouse,
  loading,
  onClose,
  onSubmit,
}: Props) {
  const [form] = Form.useForm<WarehouseFormValues>()
  const isEditing = Boolean(warehouse)

  useEffect(() => {
    if (open) {
      if (warehouse) {
        form.setFieldsValue({
          code: warehouse.code,
          name: warehouse.name,
          address: warehouse.address,
          managerName: warehouse.managerName,
          contactPhone: warehouse.contactPhone,
          capacity: warehouse.capacity,
          active: warehouse.active,
        })
      } else {
        form.resetFields()
        form.setFieldsValue({
          active: true,
          capacity: 500,
        })
      }
    }
  }, [open, warehouse, form])

  const handleFinish = async (values: WarehouseFormValues) => {
    await onSubmit(values)
  }

  return (
    <Modal
      title={isEditing ? `Chỉnh sửa kho: ${warehouse?.name}` : 'Thêm mới kho hàng'}
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={loading}
      okText={isEditing ? 'Lưu thay đổi' : 'Tạo kho'}
      cancelText="Hủy"
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        className="pt-2"
      >
        <Form.Item
          name="code"
          label="Mã kho (Warehouse Code)"
          rules={[
            { required: !isEditing, message: 'Vui lòng nhập mã kho' },
            {
              pattern: /^[A-Za-z0-9_-]+$/,
              message: 'Mã kho chỉ gồm chữ cái, chữ số và dấu gạch (-_)',
            },
            { max: 30, message: 'Mã kho tối đa 30 ký tự' },
          ]}
          tooltip="Mã định danh duy nhất toàn hệ thống, ví dụ: WH-HCM-01, WH-HN-02"
        >
          <Input
            placeholder="vd: WH-HCM-01"
            disabled={isEditing}
            className="uppercase font-mono"
          />
        </Form.Item>

        <Form.Item
          name="name"
          label="Tên kho"
          rules={[
            { required: true, message: 'Vui lòng nhập tên kho' },
            { max: 150, message: 'Tên kho tối đa 150 ký tự' },
          ]}
        >
          <Input placeholder="vd: Kho Tổng Miền Nam" />
        </Form.Item>

        <Form.Item
          name="address"
          label="Địa chỉ kho"
          rules={[{ max: 255, message: 'Địa chỉ tối đa 255 ký tự' }]}
        >
          <Input.TextArea rows={2} placeholder="Số nhà, đường, khu công nghiệp, tỉnh/thành..." />
        </Form.Item>

        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            name="managerName"
            label="Trưởng kho / Quản lý"
            rules={[{ max: 100, message: 'Tên trưởng kho tối đa 100 ký tự' }]}
          >
            <Input placeholder="Họ và tên" />
          </Form.Item>

          <Form.Item
            name="contactPhone"
            label="Số điện thoại liên hệ"
            rules={[{ max: 30, message: 'Số điện thoại tối đa 30 ký tự' }]}
          >
            <Input placeholder="0901234567" />
          </Form.Item>
        </div>

        <div className="grid grid-cols-2 gap-4 items-center">
          <Form.Item
            name="capacity"
            label="Sức chứa thiết kế (Vị trí / Pallet)"
            rules={[{ required: true, message: 'Vui lòng nhập sức chứa kho' }]}
          >
            <InputNumber min={0} className="w-full" placeholder="500" />
          </Form.Item>

          <Form.Item
            name="active"
            label="Trạng thái hoạt động"
            valuePropName="checked"
          >
            <Switch checkedChildren="Đang hoạt động" unCheckedChildren="Đang khóa" />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  )
}
