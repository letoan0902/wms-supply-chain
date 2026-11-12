import { Alert, Form, Input, InputNumber, Modal, Select } from 'antd'
import { useState } from 'react'
import type { BulkCreateBinLocationsPayload, Warehouse } from '../types'

interface Props {
  open: boolean
  warehouses: Warehouse[]
  initialWarehouseId?: number
  loading: boolean
  onClose: () => void
  onSubmit: (payload: BulkCreateBinLocationsPayload) => Promise<void>
}

interface FormValues {
  warehouseId: number
  zone: string
  rackPrefix: string
  rackFrom: number
  rackTo: number
  binPrefix: string
  binFrom: number
  binTo: number
  maxWeight?: number
}

export default function BulkCreateBinModal({
  open,
  warehouses,
  initialWarehouseId,
  loading,
  onClose,
  onSubmit,
}: Props) {
  const [form] = Form.useForm<FormValues>()
  const [previewCount, setPreviewCount] = useState<number>(20)

  const handleValuesChange = (_: unknown, allValues: Partial<FormValues>) => {
    const rackFrom = Number(allValues.rackFrom) || 1
    const rackTo = Number(allValues.rackTo) || 1
    const binFrom = Number(allValues.binFrom) || 1
    const binTo = Number(allValues.binTo) || 1

    if (rackTo >= rackFrom && binTo >= binFrom) {
      setPreviewCount((rackTo - rackFrom + 1) * (binTo - binFrom + 1))
    } else {
      setPreviewCount(0)
    }
  }

  const handleFinish = async (values: FormValues) => {
    await onSubmit({
      warehouseId: values.warehouseId,
      zone: values.zone.trim().toUpperCase(),
      rackPrefix: values.rackPrefix.trim().toUpperCase(),
      rackFrom: values.rackFrom,
      rackTo: values.rackTo,
      binPrefix: values.binPrefix.trim().toUpperCase(),
      binFrom: values.binFrom,
      binTo: values.binTo,
      maxWeight: values.maxWeight || 1000,
    })
  }

  return (
    <Modal
      title="Sinh dải vị trí ô/kệ tự động"
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={loading}
      okText={`Tạo ${previewCount} vị trí`}
      cancelText="Hủy"
      width={560}
      destroyOnClose
    >
      <div className="text-xs text-slate-500 mb-4">
        Tự động tạo hàng loạt vị trí lưu trữ theo dải kệ và dải ô trong một thao tác.
      </div>

      <Form
        form={form}
        layout="vertical"
        initialValues={{
          warehouseId: initialWarehouseId || warehouses[0]?.id,
          zone: 'ZA',
          rackPrefix: 'R',
          rackFrom: 1,
          rackTo: 4,
          binPrefix: 'B',
          binFrom: 1,
          binTo: 5,
          maxWeight: 1000,
        }}
        onValuesChange={handleValuesChange}
        onFinish={handleFinish}
      >
        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            name="warehouseId"
            label="Kho hàng"
            rules={[{ required: true, message: 'Vui lòng chọn kho' }]}
          >
            <Select
              options={warehouses.map((w) => ({
                label: `${w.code} - ${w.name}`,
                value: w.id,
              }))}
            />
          </Form.Item>

          <Form.Item
            name="zone"
            label="Khu vực (Zone)"
            rules={[
              { required: true, message: 'Nhập mã khu vực' },
              { max: 20, message: 'Tối đa 20 ký tự' },
            ]}
          >
            <Input placeholder="vd: ZA, ZB, KHOLANH" className="uppercase" />
          </Form.Item>
        </div>

        <div className="p-3 bg-slate-50/80 rounded-lg border border-slate-200/80 mb-3">
          <div className="text-xs font-medium text-slate-700 mb-2">Cấu hình dải kệ (Rack)</div>
          <div className="grid grid-cols-3 gap-3">
            <Form.Item
              name="rackPrefix"
              label="Tiền tố kệ"
              rules={[{ required: true, message: 'Bắt buộc' }]}
              className="mb-0"
            >
              <Input placeholder="R" className="uppercase" />
            </Form.Item>
            <Form.Item
              name="rackFrom"
              label="Từ số"
              rules={[{ required: true, message: 'Bắt buộc' }]}
              className="mb-0"
            >
              <InputNumber min={1} className="w-full" />
            </Form.Item>
            <Form.Item
              name="rackTo"
              label="Đến số"
              rules={[{ required: true, message: 'Bắt buộc' }]}
              className="mb-0"
            >
              <InputNumber min={1} className="w-full" />
            </Form.Item>
          </div>
        </div>

        <div className="p-3 bg-slate-50/80 rounded-lg border border-slate-200/80 mb-3">
          <div className="text-xs font-medium text-slate-700 mb-2">Cấu hình dải ô (Bin)</div>
          <div className="grid grid-cols-3 gap-3">
            <Form.Item
              name="binPrefix"
              label="Tiền tố ô"
              rules={[{ required: true, message: 'Bắt buộc' }]}
              className="mb-0"
            >
              <Input placeholder="B" className="uppercase" />
            </Form.Item>
            <Form.Item
              name="binFrom"
              label="Từ số"
              rules={[{ required: true, message: 'Bắt buộc' }]}
              className="mb-0"
            >
              <InputNumber min={1} className="w-full" />
            </Form.Item>
            <Form.Item
              name="binTo"
              label="Đến số"
              rules={[{ required: true, message: 'Bắt buộc' }]}
              className="mb-0"
            >
              <InputNumber min={1} className="w-full" />
            </Form.Item>
          </div>
        </div>

        <Form.Item
          name="maxWeight"
          label="Tải trọng tối đa mỗi ô (kg)"
          rules={[{ required: true, message: 'Nhập tải trọng' }]}
        >
          <InputNumber min={0} className="w-full" placeholder="1000" />
        </Form.Item>

        <Alert
          type="info"
          showIcon
          message={`Hệ thống sẽ sinh ${previewCount} vị trí lưu trữ phân cấp (Kho - Khu - Kệ - Ô).`}
          description="Các vị trí đã tồn tại từ trước sẽ tự động được bỏ qua để tránh trùng lặp."
        />
      </Form>
    </Modal>
  )
}
