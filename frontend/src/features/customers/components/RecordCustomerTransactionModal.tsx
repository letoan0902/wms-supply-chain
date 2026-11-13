import { DollarCircleOutlined } from '@ant-design/icons'
import { Form, Input, InputNumber, Modal, Radio } from 'antd'
import dayjs from 'dayjs'
import { useEffect } from 'react'
import type {
  Customer,
  CustomerTransactionType,
  RecordCustomerTransactionPayload,
} from '../types'

interface RecordCustomerTransactionModalProps {
  open: boolean
  customer: Customer | null
  submitting: boolean
  onCancel: () => void
  onSubmit: (payload: RecordCustomerTransactionPayload) => Promise<void>
}

const TRANSACTION_TYPES: { value: CustomerTransactionType; label: string; desc: string }[] = [
  {
    value: 'PAYMENT',
    label: 'Thu tiền khách hàng (- Nợ)',
    desc: 'Thu tiền mặt hoặc nhận chuyển khoản ngân hàng từ khách, làm giảm số dư nợ phải thu',
  },
  {
    value: 'SALES_INVOICE',
    label: 'Hóa đơn bán hàng (+ Nợ)',
    desc: 'Xuất hàng bán nợ trả chậm cho khách hàng, làm tăng công nợ phải thu',
  },
  {
    value: 'RETURN',
    label: 'Khách hàng trả lại hàng (- Nợ)',
    desc: 'Nhập lại hàng do khách trả, giảm trừ trực tiếp vào công nợ',
  },
  {
    value: 'ADJUSTMENT',
    label: 'Điều chỉnh số dư (+ Nợ)',
    desc: 'Điều chỉnh tăng số dư công nợ theo biên bản đối soát định kỳ',
  },
]

const formatCurrency = (val: number): string =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val)

export default function RecordCustomerTransactionModal({
  open,
  customer,
  submitting,
  onCancel,
  onSubmit,
}: RecordCustomerTransactionModalProps) {
  const [form] = Form.useForm()

  useEffect(() => {
    if (open) {
      form.resetFields()
      form.setFieldsValue({
        type: 'PAYMENT',
        amount: customer?.currentDebt && customer.currentDebt > 0 ? customer.currentDebt : 0,
        referenceCode: `PT-${dayjs().format('YYYYMMDD-HHmmss')}`,
      })
    }
  }, [open, customer, form])

  const handleFinish = async (values: Record<string, unknown>) => {
    const payload: RecordCustomerTransactionPayload = {
      type: values.type as CustomerTransactionType,
      amount: Number(values.amount),
      referenceCode: values.referenceCode ? String(values.referenceCode).trim() : undefined,
      note: values.note ? String(values.note).trim() : undefined,
    }
    await onSubmit(payload)
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-base font-semibold text-gray-800">
          <DollarCircleOutlined className="text-emerald-500" />
          <span>Ghi nhận phát sinh công nợ: {customer?.name} ({customer?.code})</span>
        </div>
      }
      open={open}
      onCancel={onCancel}
      onOk={() => form.submit()}
      confirmLoading={submitting}
      okText="Ghi nhận giao dịch"
      cancelText="Đóng"
      width={580}
      destroyOnClose
    >
      {customer && (
        <div className="p-3 bg-slate-50/80 rounded-lg border border-slate-200/80 mb-4 flex justify-between items-center">
          <div>
            <span className="text-xs text-slate-500 block">Công nợ hiện tại:</span>
            <span className="text-sm font-semibold text-slate-800 tabular-nums">
              {formatCurrency(customer.currentDebt)}
            </span>
          </div>
          <div className="text-right">
            <span className="text-xs text-slate-500 block">Hạn mức tín dụng:</span>
            <span className="text-sm font-semibold text-slate-800 tabular-nums">
              {formatCurrency(customer.creditLimit)}
            </span>
          </div>
        </div>
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        requiredMark="optional"
      >
        <Form.Item
          name="type"
          label={<span className="font-medium text-gray-700">Loại giao dịch công nợ</span>}
          rules={[{ required: true, message: 'Vui lòng chọn loại giao dịch' }]}
        >
          <Radio.Group
            className="flex flex-col gap-2"
            onChange={(e) => {
              const val = e.target.value as CustomerTransactionType
              if (val === 'PAYMENT') {
                form.setFieldsValue({
                  referenceCode: `PT-${dayjs().format('YYYYMMDD-HHmmss')}`,
                })
              } else if (val === 'SALES_INVOICE') {
                form.setFieldsValue({
                  referenceCode: `INV-${dayjs().format('YYYYMMDD-HHmmss')}`,
                })
              }
            }}
          >
            {TRANSACTION_TYPES.map((t) => (
              <Radio key={t.value} value={t.value} className="items-start">
                <div>
                  <div className="font-medium text-gray-800 text-sm">{t.label}</div>
                  <div className="text-xs text-gray-500">{t.desc}</div>
                </div>
              </Radio>
            ))}
          </Radio.Group>
        </Form.Item>

        <Form.Item
          name="amount"
          label={<span className="font-medium text-gray-700">Số tiền giao dịch (VNĐ)</span>}
          rules={[
            { required: true, message: 'Vui lòng nhập số tiền' },
            {
              type: 'number',
              min: 1,
              message: 'Số tiền phải lớn hơn 0',
            },
            ({ getFieldValue }) => ({
              validator(_, value) {
                const type = getFieldValue('type')
                const currentDebt = customer?.currentDebt ?? 0
                if (type === 'PAYMENT' || type === 'RETURN') {
                  if (currentDebt <= 0) {
                    return Promise.reject(
                      new Error('Khách hàng hiện không có nợ (công nợ = 0 đ), không thể lập phiếu thu tiền!')
                    )
                  }
                  if (value > currentDebt) {
                    return Promise.reject(
                      new Error(
                        `Số tiền (${formatCurrency(value)}) không được lớn hơn công nợ hiện tại (${formatCurrency(currentDebt)})`
                      )
                    )
                  }
                }
                return Promise.resolve()
              },
            }),
          ]}
        >
          <InputNumber<number>
            className="w-full text-base font-semibold"
            size="large"
            formatter={(val) =>
              val !== undefined ? `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''
            }
            parser={(val) => {
              const cleaned = val?.replace(/\$\s?|(,*)/g, '') ?? '0'
              return Number(cleaned) || 0
            }}
            addonAfter="VNĐ"
            placeholder="0"
          />
        </Form.Item>

        <Form.Item
          name="referenceCode"
          label={<span className="font-medium text-gray-700">Mã chứng từ / Hóa đơn tham chiếu</span>}
          rules={[{ max: 50, message: 'Mã chứng từ tối đa 50 ký tự' }]}
        >
          <Input maxLength={50} placeholder="VD: PT-20261105-183000..." />
        </Form.Item>

        <Form.Item
          name="note"
          label={<span className="font-medium text-gray-700">Ghi chú</span>}
          rules={[{ max: 500, message: 'Tối đa 500 ký tự' }]}
        >
          <Input.TextArea rows={3} placeholder="Nội dung diễn giải chi tiết giao dịch..." />
        </Form.Item>
      </Form>
    </Modal>
  )
}
