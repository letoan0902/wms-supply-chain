import { LockOutlined } from '@ant-design/icons'
import { Alert, App, Button, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../../services/api'
import AuthCardLayout from './AuthCardLayout'
import { getAuthErrorCode, resetPassword } from './authApi'

interface ResetPasswordFormValues {
  newPassword: string
  confirmPassword: string
}

/**
 * Trang /reset-password: đặt mật khẩu mới bằng mã trong liên kết email (tham số `token`). Thành công thì chuyển sang
 * trang đăng nhập; liên kết sai, đã dùng hoặc quá 30 phút (TOKEN_INVALID) thì hướng dẫn xin liên kết mới.
 */
export default function ResetPasswordPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')?.trim() ?? ''
  const [submitting, setSubmitting] = useState(false)
  const [tokenInvalid, setTokenInvalid] = useState(!token)
  const [error, setError] = useState<string | null>(null)

  const handleFinish = async (values: ResetPasswordFormValues) => {
    setSubmitting(true)
    setError(null)
    try {
      await resetPassword(token, values.newPassword)
      message.success('Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.')
      navigate('/login', { replace: true })
    } catch (err) {
      if (getAuthErrorCode(err) === 'TOKEN_INVALID') {
        setTokenInvalid(true)
      } else {
        setError(getApiErrorMessage(err, 'Không đặt lại được mật khẩu. Vui lòng thử lại sau.'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCardLayout>
      <Typography.Title level={4} style={{ marginBottom: 4 }}>
        Đặt lại mật khẩu
      </Typography.Title>

      {tokenInvalid ? (
        <div className="mt-3">
          <Alert
            type="warning"
            showIcon
            className="mb-4 rounded-lg"
            message="Liên kết không hợp lệ, đã được sử dụng hoặc đã hết hạn."
            description="Liên kết đặt lại mật khẩu chỉ dùng được một lần và có hiệu lực 30 phút. Hãy yêu cầu một liên kết mới."
          />
          <Link to="/forgot-password">
            <Button type="primary" block className="rounded-lg">
              Yêu cầu liên kết mới
            </Button>
          </Link>
        </div>
      ) : (
        <>
          <Typography.Text type="secondary" className="text-sm">
            Nhập mật khẩu mới cho tài khoản của bạn. Sau khi đổi, mọi phiên đăng nhập cũ phải đăng nhập lại.
          </Typography.Text>
          <Form<ResetPasswordFormValues>
            layout="vertical"
            requiredMark={false}
            onFinish={handleFinish}
            autoComplete="off"
            className="mt-4"
          >
            <Form.Item
              label={<span className="text-xs font-semibold text-slate-700">Mật khẩu mới</span>}
              name="newPassword"
              className="mb-3"
              rules={[
                { required: true, message: 'Vui lòng nhập mật khẩu mới' },
                { min: 6, max: 72, message: 'Mật khẩu mới phải từ 6 đến 72 ký tự' },
              ]}
            >
              <Input.Password
                prefix={<LockOutlined className="text-slate-400" />}
                placeholder="Từ 6 đến 72 ký tự"
                autoComplete="new-password"
                className="rounded-lg"
              />
            </Form.Item>
            <Form.Item
              label={<span className="text-xs font-semibold text-slate-700">Nhập lại mật khẩu mới</span>}
              name="confirmPassword"
              dependencies={['newPassword']}
              className="mb-4"
              rules={[
                { required: true, message: 'Vui lòng nhập lại mật khẩu mới' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve()
                    }
                    return Promise.reject(new Error('Mật khẩu nhập lại không khớp'))
                  },
                }),
              ]}
            >
              <Input.Password
                prefix={<LockOutlined className="text-slate-400" />}
                placeholder="Nhập lại mật khẩu mới"
                autoComplete="new-password"
                className="rounded-lg"
              />
            </Form.Item>
            {error && <Alert type="error" showIcon message={error} className="mb-3 rounded-lg" />}
            <Button type="primary" htmlType="submit" size="large" block loading={submitting} className="rounded-lg">
              Đặt mật khẩu mới
            </Button>
          </Form>
        </>
      )}

      <div className="mt-4 text-center">
        <Link to="/login" className="text-xs font-medium text-blue-600 hover:underline">
          Quay lại trang Đăng nhập
        </Link>
      </div>
    </AuthCardLayout>
  )
}
