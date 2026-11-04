import { DollarCircleOutlined } from '@ant-design/icons'
import { Form, Input, InputNumber, Modal, Radio } from 'antd'
import { useEffect } from 'react'
import type { RecordSupplierTransactionPayload, Supplier, SupplierTransactionType } from '../types'

interface Props {
  open: boolean
  supplier: Supplier | null
  submitting: boolean
  onCancel: () => void
  onSubmit: (payload: RecordSupplierTransactionPayload) => Promise<void>
}

const TRANSACTION_TYPES: { value: SupplierTransactionType; label: string; desc: string }[] = [
  {
    value: 'PURCHASE_INVOICE',
    label: 'Hóa đơn nhập hàng (+ Nợ)',
    desc: 'Nhập hàng từ NCC chưa thanh toán, làm tăng số dư nợ phải trả',
  },
  {
    value: 'PAYMENT',
    label: 'Thanh toán tiền hàng (- Nợ)',
    desc: 'Chi tiền mặt hoặc chuyển khoản thanh toán tiền hàng, làm giảm công nợ',
  },
  {
    value: 'RETURN',
    label: 'Trả hàng cho NCC (- Nợ)',
    desc: 'Xuất trả lại hàng lỗi/hỏng cho NCC, giảm trừ công nợ phải trả',
  },
  {
    value: 'ADJUSTMENT',
    label: 'Điều chỉnh số dư (+/- Nợ)',
    desc: 'Điều chỉnh số dư công nợ do đối soát chênh lệch sổ sách',
  },
]

export default function RecordTransactionModal({
  open,
  supplier,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  const [form] = Form.useForm()

  useEffect(() => {
    if (open) {
      form.resetFields()
      form.setFieldsValue({
        type: 'PAYMENT',
        amount: 0,
      })
    }
  }, [open, form])

  const handleFinish = async (values: Record<string, unknown>) => {
    const payload: RecordSupplierTransactionPayload = {
      type: values.type as SupplierTransactionType,
      amount: Number(values.amount),
      referenceCode: values.referenceCode ? String(values.referenceCode).trim() : undefined,
      note: values.note ? String(values.note).trim() : undefined,
    }
    await onSubmit(payload)
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-base font-semibold text-slate-800">
          <DollarCircleOutlined className="text-amber-500" />
          <span>Ghi nhận giao dịch công nợ NCC: {supplier?.code} - {supplier?.name}</span>
        </div>
      }
      open={open}
      onCancel={onCancel}
      onOk={() => form.submit()}
      confirmLoading={submitting}
      okText="Ghi nhận giao dịch"
      cancelText="Đóng"
      width={560}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        className="mt-4"
        requiredMark="optional"
      >
        <Form.Item
          name="type"
          label="Loại giao dịch công nợ"
          rules={[{ required: true, message: 'Vui lòng chọn loại giao dịch' }]}
        >
          <Radio.Group className="flex flex-col gap-2">
            {TRANSACTION_TYPES.map((t) => (
              <Radio key={t.value} value={t.value} className="items-start">
                <div>
                  <div className="font-medium text-slate-800 text-sm">{t.label}</div>
                  <div className="text-xs text-slate-400">{t.desc}</div>
                </div>
              </Radio>
            ))}
          </Radio.Group>
        </Form.Item>

        <Form.Item
          name="amount"
          label="Số tiền phát sinh (VNĐ)"
          rules={[
            { required: true, message: 'Vui lòng nhập số tiền' },
            {
              type: 'number',
              min: 1,
              message: 'Số tiền phải lớn hơn 0',
            },
          ]}
        >
          <InputNumber<number>
            className="w-full"
            step={1000000}
            formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
            parser={(val) => Number(val?.replace(/\$\s?|(,*)/g, '') || 0)}
            addonAfter="₫"
          />
        </Form.Item>

        <Form.Item
          name="referenceCode"
          label="Mã chứng từ tham chiếu"
          rules={[{ max: 50, message: 'Tối đa 50 ký tự' }]}
          extra="Ví dụ: Số hóa đơn (HD-00129), Số ủy nhiệm chi (UNC-Vietcombank-881)..."
        >
          <Input placeholder="VD: UNC-VCB-20261103" />
        </Form.Item>

        <Form.Item
          name="note"
          label="Ghi chú diễn giải"
          rules={[{ max: 255, message: 'Tối đa 255 ký tự' }]}
        >
          <Input.TextArea rows={3} placeholder="Nội dung giải trình hoặc chi tiết đợt thanh toán..." />
        </Form.Item>
      </Form>
    </Modal>
  )
}
