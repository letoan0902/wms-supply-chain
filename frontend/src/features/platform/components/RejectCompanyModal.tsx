import { Alert, App, Form, Input, Modal } from 'antd'
import { useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { rejectPlatformCompany, type PlatformCompanySummary } from '../platformApi'
import type { StatusTarget } from './CompanyStatusModal'

/** Độ dài tối đa của lý do từ chối (theo hợp đồng API). */
const REASON_MAX_LENGTH = 255

interface Props {
  /** Bản đăng ký đang chờ (PENDING) cần từ chối; null thì đóng */
  target: StatusTarget | null
  onCancel: () => void
  onDone: (updated: PlatformCompanySummary) => void
}

/** Hộp thoại từ chối một bản đăng ký đang chờ, lý do bắt buộc và chỉ ghi vào nhật ký của máy chủ. */
export default function RejectCompanyModal({ target, onCancel, onDone }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<{ reason: string }>()
  const [submitting, setSubmitting] = useState(false)

  const handleOk = async () => {
    if (!target) return
    let reason: string
    try {
      reason = (await form.validateFields()).reason.trim()
    } catch {
      return
    }
    setSubmitting(true)
    try {
      const updated = await rejectPlatformCompany(target.id, { reason })
      message.success(`Đã từ chối bản đăng ký của ${target.name}`)
      form.resetFields()
      onDone(updated)
    } catch (error) {
      message.error(getApiErrorMessage(error, 'Không từ chối được bản đăng ký'))
    } finally {
      setSubmitting(false)
    }
  }

  const handleCancel = () => {
    form.resetFields()
    onCancel()
  }

  return (
    <Modal
      open={target !== null}
      title="Từ chối bản đăng ký"
      okText="Từ chối"
      okButtonProps={{ danger: true }}
      cancelText="Hủy bỏ"
      confirmLoading={submitting}
      onOk={handleOk}
      onCancel={handleCancel}
      width={460}
      destroyOnClose
    >
      {target && (
        <div className="flex flex-col gap-3 py-1">
          <div className="text-sm text-slate-700">
            Doanh nghiệp <strong className="text-slate-900">{target.name}</strong>{' '}
            <span className="font-mono text-xs text-slate-500">({target.code})</span>
          </div>
          <Alert
            type="warning"
            showIcon
            message="Từ chối xong, người đăng ký không đăng nhập được."
            description="Doanh nghiệp chuyển sang Tạm khóa và không duyệt lại được. Người đăng ký nhận email báo kết quả, email không kèm lý do."
          />
          <Form form={form} layout="vertical" requiredMark={false}>
            <Form.Item
              label="Lý do (chỉ ghi vào nhật ký)"
              name="reason"
              className="mb-0"
              rules={[
                { required: true, whitespace: true, message: 'Nhập lý do từ chối' },
                { max: REASON_MAX_LENGTH, message: `Lý do tối đa ${REASON_MAX_LENGTH} ký tự` },
              ]}
            >
              <Input.TextArea
                rows={3}
                maxLength={REASON_MAX_LENGTH}
                showCount
                placeholder="Ví dụ: mã số thuế không khớp với tên doanh nghiệp"
              />
            </Form.Item>
          </Form>
        </div>
      )}
    </Modal>
  )
}
