import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { App, Button, Card, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { getApiErrorMessage, getErrorStatus } from '../../services/api'
import type { LoginRequest } from '../../types/auth'
import { getToken, saveAuth } from '../../utils/storage'
import { login } from './authApi'

interface LocationState {
  from?: { pathname: string }
}

export default function LoginPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const [submitting, setSubmitting] = useState(false)

  const redirectTo = (location.state as LocationState | null)?.from?.pathname ?? '/'

  // Đã đăng nhập thì vào thẳng hệ thống
  if (getToken()) {
    return <Navigate to={redirectTo} replace />
  }

  const handleFinish = async (values: LoginRequest) => {
    setSubmitting(true)
    try {
      const result = await login(values)
      saveAuth(result.accessToken, { username: result.username, roles: result.roles })
      message.success('Đăng nhập thành công')
      navigate(redirectTo, { replace: true })
    } catch (error) {
      const fallback =
        getErrorStatus(error) === 401
          ? 'Tên đăng nhập hoặc mật khẩu không đúng.'
          : 'Đăng nhập thất bại. Vui lòng thử lại.'
      message.error(getApiErrorMessage(error, fallback))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <Card className="w-full max-w-md shadow-md">
        <div className="mb-6 text-center">
          <Typography.Title level={3} style={{ marginBottom: 4 }}>
            WMS Supply Chain
          </Typography.Title>
          <Typography.Text type="secondary">Hệ thống quản lý kho và chuỗi cung ứng</Typography.Text>
        </div>

        <Form<LoginRequest> layout="vertical" requiredMark={false} onFinish={handleFinish}>
          <Form.Item
            label="Tên đăng nhập"
            name="username"
            rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
          >
            <Input
              size="large"
              prefix={<UserOutlined />}
              placeholder="Nhập tên đăng nhập"
              autoComplete="username"
            />
          </Form.Item>

          <Form.Item
            label="Mật khẩu"
            name="password"
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
          >
            <Input.Password
              size="large"
              prefix={<LockOutlined />}
              placeholder="Nhập mật khẩu"
              autoComplete="current-password"
            />
          </Form.Item>

          <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
            Đăng nhập
          </Button>
        </Form>
      </Card>
    </div>
  )
}
