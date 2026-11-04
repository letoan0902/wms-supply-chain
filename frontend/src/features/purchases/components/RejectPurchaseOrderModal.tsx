import { CloseCircleOutlined } from '@ant-design/icons'
import { App, Form, Input, Modal } from 'antd'
import { useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import * as purchasesApi from '../purchasesApi'
import type { PurchaseOrder } from '../types'

const { TextArea } = Input

interface Props {
  open: boolean
  order: PurchaseOrder | null
  onClose: () => void
  onSuccess: (updated: PurchaseOrder) => void
}

interface FormValues {
  reason: string
}

export default function RejectPurchaseOrderModal({
  open,
  order,
  onClose,
  onSuccess,
}: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()
  const [submitting, setSubmitting] = useState(false)

  const handleConfirmReject = async () => {
    if (!order) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      const updated = await purchasesApi.rejectPurchaseOrder(order.id, {
        reason: values.reason.trim(),
      })
      message.success(`Đã từ chối phê duyệt đơn mua hàng "${order.poNumber}"!`)
      form.resetFields()
      onSuccess(updated)
      onClose()
    } catch (err) {
      if ((err as { errorFields?: unknown })?.errorFields) return
      message.error(getApiErrorMessage(err, 'Lỗi khi từ chối đơn mua hàng'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-red-600 font-semibold">
          <CloseCircleOutlined />
          <span>Từ chối phê duyệt Đơn mua hàng: {order?.poNumber}</span>
        </div>
      }
      open={open}
      onCancel={() => {
        form.resetFields()
        onClose()
      }}
      onOk={handleConfirmReject}
      okText="Xác nhận từ chối"
      cancelText="Hủy"
      okButtonProps={{ danger: true, loading: submitting }}
      destroyOnClose
    >
      <div className="py-2 space-y-3">
        <p className="text-xs text-slate-500">
          Vui lòng nêu rõ lý do từ chối để nhân viên phụ trách mua hàng nắm được thông tin và điều chỉnh lại đơn:
        </p>

        <Form form={form} layout="vertical">
          <Form.Item
            name="reason"
            label="Lý do từ chối"
            rules={[
              { required: true, message: 'Vui lòng nhập lý do từ chối đơn hàng' },
              { min: 5, message: 'Lý do từ chối tối thiểu 5 ký tự' },
              { max: 500, message: 'Lý do từ chối tối đa 500 ký tự' },
            ]}
          >
            <TextArea
              rows={4}
              placeholder="Ví dụ: Đơn giá chưa khớp hợp đồng khung, vượt định mức ngân sách quý, số lượng quá cao..."
              maxLength={500}
              showCount
              autoFocus
            />
          </Form.Item>
        </Form>
      </div>
    </Modal>
  )
}
