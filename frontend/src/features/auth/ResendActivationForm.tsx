import { MailOutlined } from '@ant-design/icons'
import { Alert, Button, Form, Input } from 'antd'
import { useState } from 'react'
import { getApiErrorMessage } from '../../services/api'
import { resendActivation } from './authApi'

interface ResendActivationFormProps {
  /** Email điền sẵn (nếu biết) */
  initialEmail?: string
}

/**
 * Form "Gửi lại email kích hoạt": gọi POST /api/auth/companies/resend-activation. Máy chủ luôn trả cùng một thông báo
 * (không cho biết email có tồn tại hay không), form hiển thị nguyên thông báo đó.
 */
export default function ResendActivationForm({ initialEmail }: ResendActivationFormProps) {
  const [submitting, setSubmitting] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleFinish = async (values: { email: string }) => {
    setSubmitting(true)
    setError(null)
    try {
      setNotice(await resendActivation(values.email.trim()))
    } catch (err) {
      setError(getApiErrorMessage(err, 'Không gửi được yêu cầu. Vui lòng thử lại sau.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (notice) {
    return <Alert type="success" showIcon message={notice} className="rounded-lg" />
  }

  return (
    <Form<{ email: string }>
      layout="vertical"
      requiredMark={false}
      initialValues={{ email: initialEmail }}
      onFinish={handleFinish}
      autoComplete="off"
    >
      <Form.Item
        label={<span className="text-xs font-semibold text-slate-700">Email đã dùng để đăng ký</span>}
        name="email"
        className="mb-3"
        rules={[
          { required: true, message: 'Vui lòng nhập email' },
          { type: 'email', message: 'Địa chỉ email không đúng định dạng' },
        ]}
      >
        <Input
          prefix={<MailOutlined className="text-slate-400" />}
          placeholder="Ví dụ: ten@congty.vn"
          autoComplete="email"
          className="rounded-lg"
        />
      </Form.Item>
      {error && <Alert type="error" showIcon message={error} className="mb-3 rounded-lg" />}
      <Button type="primary" htmlType="submit" block loading={submitting} className="rounded-lg">
        Gửi lại email kích hoạt
      </Button>
    </Form>
  )
}
