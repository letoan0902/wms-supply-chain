import { Alert, App, Form, Input, Modal } from 'antd'
import { useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { updatePlatformCompanyStatus, type PlatformCompanySummary } from '../platformApi'

/** Độ dài tối đa của lý do tạm khóa (theo hợp đồng API). */
const REASON_MAX_LENGTH = 255

export interface StatusTarget {
  id: number
  name: string
  code: string
  /** Trạng thái hiện tại: ACTIVE thì hỏi tạm khóa, SUSPENDED thì hỏi mở khóa */
  status: string
}

interface Props {
  target: StatusTarget | null
  onCancel: () => void
  onDone: (updated: PlatformCompanySummary) => void
}

/**
 * Hộp thoại xác nhận tạm khóa (từ ACTIVE) hoặc mở khóa (từ SUSPENDED) một doanh nghiệp. Tạm khóa có ô lý do tùy chọn,
 * máy chủ ghi lý do vào nhật ký.
 */
export default function CompanyStatusModal({ target, onCancel, onDone }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<{ reason?: string }>()
  const [submitting, setSubmitting] = useState(false)
  const locking = target?.status === 'ACTIVE'

  const handleOk = async () => {
    if (!target) return
    // Ô lý do chỉ có khi tạm khóa: mở khóa không đọc form (form không gắn vào hộp thoại)
    let reason: string | undefined
    if (locking) {
      try {
        reason = (await form.validateFields()).reason
      } catch {
        return
      }
    }
    setSubmitting(true)
    try {
      const trimmed = reason?.trim()
      const updated = await updatePlatformCompanyStatus(target.id, {
        status: locking ? 'SUSPENDED' : 'ACTIVE',
        ...(locking && trimmed ? { reason: trimmed } : {}),
      })
      message.success(locking ? `Đã tạm khóa doanh nghiệp ${target.name}` : `Đã mở khóa doanh nghiệp ${target.name}`)
      if (locking) form.resetFields()
      onDone(updated)
    } catch (error) {
      message.error(
        getApiErrorMessage(error, locking ? 'Không tạm khóa được doanh nghiệp' : 'Không mở khóa được doanh nghiệp'),
      )
    } finally {
      setSubmitting(false)
    }
  }

  const handleCancel = () => {
    if (locking) form.resetFields()
    onCancel()
  }

  return (
    <Modal
      open={target !== null}
      title={locking ? 'Tạm khóa doanh nghiệp' : 'Mở khóa doanh nghiệp'}
      okText={locking ? 'Tạm khóa' : 'Mở khóa'}
      okButtonProps={{ danger: locking }}
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
          {locking ? (
            <>
              <Alert
                type="warning"
                showIcon
                message="Người dùng của doanh nghiệp không đăng nhập được cho tới khi mở khóa."
                description="Phiên đang đăng nhập không gia hạn được và sẽ dừng khi hết hạn. Dữ liệu của doanh nghiệp được giữ nguyên."
              />
              <Form form={form} layout="vertical" requiredMark={false}>
                <Form.Item
                  label="Lý do (không bắt buộc)"
                  name="reason"
                  className="mb-0"
                  rules={[{ max: REASON_MAX_LENGTH, message: `Lý do tối đa ${REASON_MAX_LENGTH} ký tự` }]}
                >
                  <Input.TextArea
                    rows={3}
                    maxLength={REASON_MAX_LENGTH}
                    showCount
                    placeholder="Ví dụ: vi phạm điều khoản sử dụng, yêu cầu của doanh nghiệp"
                  />
                </Form.Item>
              </Form>
            </>
          ) : (
            <div className="text-sm text-slate-600 leading-relaxed">
              Sau khi mở khóa, người dùng của doanh nghiệp đăng nhập và làm việc lại bình thường.
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}
