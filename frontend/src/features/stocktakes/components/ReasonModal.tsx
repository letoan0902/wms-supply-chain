import { Form, Input, Modal } from 'antd'
import { useState, type ReactNode } from 'react'

interface Props {
  open: boolean
  title: string
  /** Giải thích hậu quả của thao tác (hộp xác nhận lần hai) */
  description: ReactNode
  label: string
  /** Bắt buộc nhập (từ chối) hay không (hủy, ghi chú duyệt) */
  required: boolean
  okText: string
  danger?: boolean
  placeholder?: string
  maxLength?: number
  onCancel: () => void
  /** Gửi lên máy chủ; ném lỗi thì hộp thoại giữ nguyên để người dùng thử lại */
  onSubmit: (text: string) => Promise<void>
}

/** Hộp xác nhận kèm ô lý do hoặc ghi chú cho hủy, từ chối, duyệt đợt kiểm kê. */
export default function ReasonModal({
  open,
  title,
  description,
  label,
  required,
  okText,
  danger,
  placeholder,
  maxLength = 500,
  onCancel,
  onSubmit,
}: Props) {
  const [form] = Form.useForm<{ text?: string }>()
  const [submitting, setSubmitting] = useState(false)

  const handleOk = async () => {
    let values: { text?: string }
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setSubmitting(true)
    try {
      await onSubmit(values.text?.trim() ?? '')
    } catch {
      // Lỗi đã được nơi gọi hiển thị; giữ hộp thoại mở
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={title}
      open={open}
      onCancel={() => {
        if (!submitting) onCancel()
      }}
      onOk={() => void handleOk()}
      okText={okText}
      cancelText="Đóng"
      confirmLoading={submitting}
      okButtonProps={{ danger, 'data-write-action': 'true' }}
      destroyOnHidden
    >
      <div className="mb-3">{description}</div>
      <Form form={form} layout="vertical">
        <Form.Item
          name="text"
          label={label}
          rules={[
            ...(required ? [{ required: true, whitespace: true, message: `Vui lòng nhập ${label.toLowerCase()}` }] : []),
            { max: maxLength, message: `${label} tối đa ${maxLength} ký tự` },
          ]}
        >
          <Input.TextArea rows={3} maxLength={maxLength} showCount placeholder={placeholder} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
