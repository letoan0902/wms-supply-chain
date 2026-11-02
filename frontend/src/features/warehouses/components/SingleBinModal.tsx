import { Form, Input, InputNumber, Modal, Select, Switch } from 'antd'
import { useEffect } from 'react'
import type { CreateBinLocationPayload, Warehouse } from '../types'

interface Props {
  open: boolean
  warehouses: Warehouse[]
  initialWarehouseId?: number
  loading: boolean
  onClose: () => void
  onSubmit: (payload: CreateBinLocationPayload) => Promise<void>
}

interface SingleBinFormValues {
  warehouseId: number
  zone: string
  rack: string
  bin: string
  maxWeight?: number
  active: boolean
}

export default function SingleBinModal({
  open,
  warehouses,
  initialWarehouseId,
  loading,
  onClose,
  onSubmit,
}: Props) {
  const [form] = Form.useForm<SingleBinFormValues>()

  useEffect(() => {
    if (open) {
      form.resetFields()
      form.setFieldsValue({
        warehouseId: initialWarehouseId || warehouses[0]?.id,
        zone: 'ZA',
        rack: 'R01',
        bin: 'B01',
        maxWeight: 1000,
        active: true,
      })
    }
  }, [open, initialWarehouseId, warehouses, form])

  const handleFinish = async (values: SingleBinFormValues) => {
    await onSubmit({
      warehouseId: values.warehouseId,
      zone: values.zone.trim().toUpperCase(),
      rack: values.rack.trim().toUpperCase(),
      bin: values.bin.trim().toUpperCase(),
      maxWeight: values.maxWeight || 1000,
      active: values.active,
    })
  }

  return (
    <Modal
      title="Thêm vị trí Ô/Kệ đơn lẻ (WMS-06.1)"
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={loading}
      okText="Thêm vị trí"
      cancelText="Hủy"
      destroyOnClose
    >
      <Form form={form} layout="vertical" onFinish={handleFinish} className="pt-2">
        <Form.Item
          name="warehouseId"
          label="Kho bãi"
          rules={[{ required: true, message: 'Vui lòng chọn kho' }]}
        >
          <Select
            options={warehouses.map((w) => ({
              label: `${w.code} - ${w.name}`,
              value: w.id,
            }))}
          />
        </Form.Item>

        <div className="grid grid-cols-3 gap-3">
          <Form.Item
            name="zone"
            label="Khu (Zone)"
            rules={[
              { required: true, message: 'Bắt buộc' },
              { max: 20, message: 'Tối đa 20 ký tự' },
            ]}
          >
            <Input placeholder="ZA" className="uppercase font-mono" />
          </Form.Item>

          <Form.Item
            name="rack"
            label="Kệ (Rack)"
            rules={[
              { required: true, message: 'Bắt buộc' },
              { max: 20, message: 'Tối đa 20 ký tự' },
            ]}
          >
            <Input placeholder="R01" className="uppercase font-mono" />
          </Form.Item>

          <Form.Item
            name="bin"
            label="Ô (Bin)"
            rules={[
              { required: true, message: 'Bắt buộc' },
              { max: 20, message: 'Tối đa 20 ký tự' },
            ]}
          >
            <Input placeholder="B01" className="uppercase font-mono" />
          </Form.Item>
        </div>

        <div className="grid grid-cols-2 gap-4 items-center">
          <Form.Item name="maxWeight" label="Tải trọng tối đa (kg)">
            <InputNumber min={0} className="w-full" placeholder="1000" />
          </Form.Item>

          <Form.Item name="active" label="Trạng thái" valuePropName="checked">
            <Switch checkedChildren="Hoạt động" unCheckedChildren="Tạm khóa" />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  )
}
