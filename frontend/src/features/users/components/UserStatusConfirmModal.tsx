import {
  Alert,
  Form,
  Input,
  Modal,
  Space,
  Tag,
} from 'antd'
import { useEffect } from 'react'
import { CheckCircleOutlined, LockOutlined } from '@ant-design/icons'
import type { UserDetail } from '../types'
import { ROLE_CONFIG } from '../types'

interface Props {
  open: boolean
  user: UserDetail | null
  targetStatus: boolean
  confirmLoading: boolean
  onConfirm: (user: UserDetail, targetStatus: boolean, reason: string) => Promise<void>
  onCancel: () => void
}

export default function UserStatusConfirmModal({
  open,
  user,
  targetStatus,
  confirmLoading,
  onConfirm,
  onCancel,
}: Props) {
  const [form] = Form.useForm<{ reason: string }>()

  const isLocking = !targetStatus

  useEffect(() => {
    if (open) {
      form.resetFields()
      form.setFieldsValue({
        reason: targetStatus
          ? 'Kích hoạt lại tài khoản sau khi xác minh thông tin'
          : '',
      })
    }
  }, [open, targetStatus, form])

  if (!user) return null

  const handleOk = async () => {
    try {
      const values = await form.validateFields()
      await onConfirm(user, targetStatus, values.reason.trim())
    } catch {
      // Form validation error
    }
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          {isLocking ? (
            <div className="w-8 h-8 rounded-full bg-red-50 text-red-600 flex items-center justify-center shrink-0">
              <LockOutlined className="text-base" />
            </div>
          ) : (
            <div className="w-8 h-8 rounded-full bg-green-50 text-green-600 flex items-center justify-center shrink-0">
              <CheckCircleOutlined className="text-base" />
            </div>
          )}
          <div>
            <div className="text-base font-semibold text-gray-900">
              {isLocking ? 'Xác nhận khóa tài khoản' : 'Xác nhận mở khóa tài khoản'}
            </div>
            <div className="text-xs text-gray-500 font-normal">
              Thao tác cập nhật trạng thái hoạt động của nhân sự
            </div>
          </div>
        </div>
      }
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={confirmLoading}
      okText={isLocking ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
      okButtonProps={{
        danger: isLocking,
        className: !isLocking ? 'bg-blue-600' : undefined,
      }}
      cancelText="Hủy"
      width={500}
      destroyOnClose
    >
      <div className="mt-3 flex flex-col gap-3">
        {/* User Card Info */}
        <div className="p-3 bg-gray-50 rounded-lg border border-gray-100 flex items-center justify-between">
          <div>
            <div className="font-semibold text-gray-900">
              {user.fullName || user.username}
            </div>
            <div className="text-xs text-gray-500 font-mono">@{user.username} • {user.email}</div>
          </div>
          <Space size="small">
            {user.roles?.map((r) => (
              <Tag key={r} color={ROLE_CONFIG[r]?.color || 'default'} className="text-xs">
                {ROLE_CONFIG[r]?.label || r}
              </Tag>
            ))}
          </Space>
        </div>

        {/* Cảnh báo tác động */}
        <Alert
          type={isLocking ? 'warning' : 'info'}
          showIcon
          message={
            isLocking
              ? 'Tài khoản sau khi bị khóa sẽ không thể đăng nhập hoặc thực hiện bất kỳ thao tác nào trên hệ thống.'
              : 'Tài khoản sau khi mở khóa sẽ có thể đăng nhập lại và thực thi các quyền hạn bình thường.'
          }
          className="text-xs"
        />

        {/* Form nhập lý do */}
        <Form form={form} layout="vertical" className="mt-1">
          <Form.Item
            label={
              <span className="font-medium text-gray-700 text-xs">
                Lý do {isLocking ? 'khóa tài khoản' : 'mở khóa'} <span className="text-red-500">*</span>
              </span>
            }
            name="reason"
            rules={[
              {
                required: true,
                message: `Vui lòng nhập lý do ${isLocking ? 'khóa' : 'mở khóa'} tài khoản`,
              },
              {
                min: 5,
                message: 'Lý do phải có ít nhất 5 ký tự để lưu vết kiểm toán (Audit Log)',
              },
            ]}
          >
            <Input.TextArea
              rows={3}
              placeholder={
                isLocking
                  ? 'VD: Tạm khóa do nhân sự nghỉ việc / Nghi ngờ lộ mật khẩu / Theo yêu cầu của quản lý...'
                  : 'VD: Kích hoạt lại sau khi kiểm tra thông tin / Nhân sự đã đi làm trở lại...'
              }
              showCount
              maxLength={255}
            />
          </Form.Item>
        </Form>
      </div>
    </Modal>
  )
}
