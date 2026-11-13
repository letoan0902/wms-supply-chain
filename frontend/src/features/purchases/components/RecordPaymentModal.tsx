import {
  BankOutlined,
  CheckCircleOutlined,
  DollarOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency } from '../../../utils/format'
import * as purchaseBillsApi from '../purchaseBillsApi'
import type { PurchaseBill } from '../purchaseBillTypes'

interface RecordPaymentModalProps {
  open: boolean
  bill: PurchaseBill | null
  onClose: () => void
  onSuccess: (updatedBill: PurchaseBill) => void
}

interface PaymentFormData {
  amount: number
  paymentMethod: string
  referenceCode?: string
  paymentDate?: string
  note?: string
}

export default function RecordPaymentModal({
  open,
  bill,
  onClose,
  onSuccess,
}: RecordPaymentModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<PaymentFormData>()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (open && bill) {
      form.setFieldsValue({
        amount: Math.max(0, bill.remainingAmount),
        paymentMethod: 'BANK_TRANSFER',
        referenceCode: '',
        paymentDate: dayjs().format('YYYY-MM-DD'),
        note: `Thanh toán tiền mua hàng theo hóa đơn ${bill.billNumber}`,
      })
    } else {
      form.resetFields()
    }
  }, [open, bill, form])

  const handlePayFull = () => {
    if (!bill) return
    form.setFieldValue('amount', Math.max(0, bill.remainingAmount))
  }

  const handleSubmit = async () => {
    if (!bill || submitting) return
    let values: PaymentFormData
    try {
      setSubmitting(true)
      values = await form.validateFields()
    } catch {
      setSubmitting(false)
      return
    }

    try {
      const updated = await purchaseBillsApi.recordBillPayment(bill.id, {
        amount: values.amount,
        paymentMethod: values.paymentMethod,
        referenceCode: values.referenceCode ? values.referenceCode.trim() : undefined,
        paymentDate: values.paymentDate || dayjs().format('YYYY-MM-DD'),
        note: values.note ? values.note.trim() : undefined,
      })

      message.success(
        `Đã ghi nhận thanh toán thành công ${formatCurrency(values.amount)} cho hóa đơn ${bill.billNumber}!`
      )
      onSuccess(updated)
      onClose()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể ghi nhận thanh toán'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center gap-2 text-slate-800">
          <DollarOutlined className="text-emerald-600 text-lg" />
          <span>Ghi Nhận Thanh Toán Hóa Đơn Mua Hàng</span>
        </div>
      }
      width={560}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose} disabled={submitting}>
          Đóng
        </Button>,
        <Button
          key="submit"
          type="primary"
          icon={<CheckCircleOutlined />}
          loading={submitting}
          onClick={handleSubmit}
          className="bg-emerald-600 hover:bg-emerald-700"
        >
          Xác nhận thanh toán
        </Button>,
      ]}
      destroyOnClose
    >
      {bill && (
        <div className="space-y-4 pt-2">
          {/* Tóm tắt thông tin hóa đơn */}
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs space-y-1.5">
            <div className="flex justify-between items-center">
              <span className="text-slate-500">Mã hóa đơn mua:</span>
              <strong className="text-sm font-mono text-slate-800">{bill.billNumber}</strong>
            </div>
            {bill.supplierName && (
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Nhà cung cấp:</span>
                <span className="font-semibold text-slate-700">{bill.supplierName}</span>
              </div>
            )}
            {bill.grnNumber && (
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Phiếu nhập kho (GRN):</span>
                <span className="font-mono text-emerald-700">{bill.grnNumber}</span>
              </div>
            )}
            <Divider className="my-1.5" />
            <div className="flex justify-between items-center">
              <span className="text-slate-500">Tổng tiền hóa đơn:</span>
              <span className="font-semibold text-slate-800">{formatCurrency(bill.totalAmount)}</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-slate-500">Đã thanh toán:</span>
              <span className="text-emerald-600 font-medium">{formatCurrency(bill.paidAmount)}</span>
            </div>
            <div className="flex justify-between items-center pt-1 border-t border-slate-200">
              <span className="text-slate-700 font-bold">Số tiền còn nợ:</span>
              <span className="text-base font-bold text-red-600">
                {formatCurrency(bill.remainingAmount)}
              </span>
            </div>
          </div>

          <Form form={form} layout="vertical">
            {/* 1. Số tiền thanh toán */}
            <Form.Item
              name="amount"
              label={
                <div className="flex items-center justify-between w-full">
                  <span>Số tiền thanh toán (VNĐ) <span className="text-red-500">*</span></span>
                  <Button
                    type="link"
                    size="small"
                    onClick={handlePayFull}
                    className="p-0 text-xs text-blue-600"
                  >
                    Thanh toán toàn bộ ({formatCurrency(bill.remainingAmount)})
                  </Button>
                </div>
              }
              rules={[
                { required: true, message: 'Vui lòng nhập số tiền thanh toán' },
                {
                  type: 'number',
                  min: 0.01,
                  message: 'Số tiền thanh toán phải lớn hơn 0',
                },
                {
                  validator: (_, val) => {
                    if (val && val > bill.remainingAmount) {
                      return Promise.reject(
                        new Error(
                          `Số tiền thanh toán không được vượt quá số nợ còn lại (${formatCurrency(bill.remainingAmount)})`
                        )
                      )
                    }
                    return Promise.resolve()
                  },
                },
              ]}
              className="mb-3"
            >
              <InputNumber
                className="w-full text-base font-semibold"
                placeholder="Nhập số tiền"
                formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                parser={(val) => Number(val?.replace(/\$\s?|(,*)/g, ''))}
                min={0.01}
                max={bill.remainingAmount}
                step={100000}
              />
            </Form.Item>

            {/* 2. Phương thức & Ngày thanh toán */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Form.Item
                name="paymentMethod"
                label="Phương thức thanh toán"
                rules={[{ required: true, message: 'Chọn phương thức' }]}
                className="mb-3"
              >
                <Select
                  options={[
                    { value: 'BANK_TRANSFER', label: 'Chuyển khoản ngân hàng (UNC)' },
                    { value: 'CASH', label: 'Tiền mặt' },
                    { value: 'CHEQUE', label: 'Séc / Hối phiếu' },
                    { value: 'OTHER', label: 'Hình thức khác' },
                  ]}
                />
              </Form.Item>

              <Form.Item
                name="paymentDate"
                label="Ngày thanh toán thực tế"
                rules={[{ required: true, message: 'Chọn ngày thanh toán' }]}
                // Form lưu chuỗi YYYY-MM-DD, DatePicker chỉ nhận đối tượng dayjs nên phải chuyển đổi hai chiều
                getValueProps={(val: string | undefined) => ({
                  value: val ? dayjs(val) : null,
                })}
                normalize={(val: dayjs.Dayjs | null) =>
                  val ? val.format('YYYY-MM-DD') : undefined
                }
                className="mb-3"
              >
                <DatePicker format="DD/MM/YYYY" className="w-full" />
              </Form.Item>
            </div>

            {/* 3. Mã tham chiếu / Ủy nhiệm chi */}
            <Form.Item
              name="referenceCode"
              label="Mã chứng từ tham chiếu (UNC / Số séc / Mã giao dịch ngân hàng)"
              className="mb-3"
            >
              <Input
                placeholder="Ví dụ: UNC-202611-0089 hoặc VCB-FT20261109"
                prefix={<BankOutlined className="text-slate-400" />}
                allowClear
              />
            </Form.Item>

            {/* 4. Ghi chú */}
            <Form.Item name="note" label="Ghi chú nội dung thanh toán" className="mb-0">
              <Input.TextArea
                rows={2}
                placeholder="Ghi chú chi tiết cho giao dịch thanh toán..."
                maxLength={500}
                showCount
              />
            </Form.Item>
          </Form>
        </div>
      )}
    </Modal>
  )
}
