import { Alert, Button, Result, Spin, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../../services/api'
import AuthCardLayout from './AuthCardLayout'
import ResendActivationForm from './ResendActivationForm'
import { activateCompany, getAuthErrorCode, type ActivationResult } from './authApi'

type ActivationState =
  | { kind: 'loading' }
  | { kind: 'success'; result: ActivationResult }
  | { kind: 'invalid' }
  | { kind: 'error'; message: string }

/**
 * Trang /activate: đọc tham số `token` trên địa chỉ, gọi API kích hoạt doanh nghiệp đúng một lần, báo kết quả.
 * Liên kết sai, đã dùng hoặc hết hạn (TOKEN_INVALID) thì hiện form gửi lại email kích hoạt.
 */
export default function ActivatePage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')?.trim() ?? ''
  const [state, setState] = useState<ActivationState>(token ? { kind: 'loading' } : { kind: 'invalid' })
  // Mã chỉ dùng được một lần: chặn gọi lại khi React StrictMode chạy effect hai lần ở môi trường phát triển
  const requested = useRef(false)

  useEffect(() => {
    if (!token || requested.current) {
      return
    }
    requested.current = true
    activateCompany(token)
      .then((result) => setState({ kind: 'success', result }))
      .catch((error: unknown) => {
        if (getAuthErrorCode(error) === 'TOKEN_INVALID') {
          setState({ kind: 'invalid' })
        } else {
          setState({
            kind: 'error',
            message: getApiErrorMessage(error, 'Không kích hoạt được doanh nghiệp. Vui lòng thử lại sau.'),
          })
        }
      })
  }, [token])

  return (
    <AuthCardLayout>
      {state.kind === 'loading' && (
        <div className="flex flex-col items-center gap-3 py-10">
          <Spin size="large" />
          <Typography.Text type="secondary">Đang kích hoạt doanh nghiệp...</Typography.Text>
        </div>
      )}

      {state.kind === 'success' && (
        <Result
          status="success"
          title="Kích hoạt thành công"
          subTitle={
            <span>
              Doanh nghiệp <strong>{state.result.companyName}</strong> ({state.result.companyCode}) đã sẵn sàng. Bạn
              có thể đăng nhập bằng tài khoản quản trị đã đăng ký.
            </span>
          }
          extra={
            <Button
              type="primary"
              size="large"
              onClick={() =>
                navigate('/login', { replace: true, state: { username: state.result.username ?? undefined } })
              }
            >
              Đăng nhập
            </Button>
          }
        />
      )}

      {state.kind === 'invalid' && (
        <div>
          <Typography.Title level={4} style={{ marginBottom: 8 }}>
            Liên kết kích hoạt không dùng được
          </Typography.Title>
          <Alert
            type="warning"
            showIcon
            className="mb-4 rounded-lg"
            message="Liên kết không hợp lệ, đã được sử dụng hoặc đã hết hạn (liên kết có hiệu lực 24 giờ)."
            description="Nếu doanh nghiệp đã được kích hoạt, bạn chỉ cần đăng nhập. Nếu chưa, hãy nhập email đăng ký để nhận liên kết mới."
          />
          <ResendActivationForm />
          <div className="mt-4 text-center">
            <Link to="/login" className="text-xs font-medium text-blue-600 hover:underline">
              Quay lại trang Đăng nhập
            </Link>
          </div>
        </div>
      )}

      {state.kind === 'error' && (
        <Result
          status="error"
          title="Chưa kích hoạt được"
          subTitle={state.message}
          extra={
            <Button type="primary" onClick={() => window.location.reload()}>
              Thử lại
            </Button>
          }
        />
      )}
    </AuthCardLayout>
  )
}
