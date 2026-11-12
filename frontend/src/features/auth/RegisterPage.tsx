import {
  ApartmentOutlined,
  IdcardOutlined,
  LockOutlined,
  MailOutlined,
  NumberOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Checkbox, Descriptions, Form, Input, Result, Typography } from 'antd'
import type { FormRule as Rule } from 'antd'
import axios from 'axios'
import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage, isHandledByGlobalDialog } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { getToken } from '../../utils/storage'
import AuthCardLayout from './AuthCardLayout'
import ResendActivationForm from './ResendActivationForm'
import { registerCompany, type CompanyRegistrationResult } from './authApi'
import {
  REGISTER_LIMITS as L,
  USERNAME_PATTERN,
  buildRegisterPayload,
  isBlank,
  isValidEmail,
  mapRegisterError,
  type ErrorBody,
  type RegisterCompanyFormValues,
  type RegisterField,
} from './registerForm'

const FALLBACK_ERROR = 'Đăng ký doanh nghiệp không thành công. Vui lòng thử lại.'

/** Gói khách chọn ở trang bảng giá (`/register?plan=free|pro`): chỉ để giải thích, máy chủ luôn bắt đầu bằng dùng thử. */
const PLAN_NOTES: Record<string, string> = {
  free:
    'Bạn đã chọn gói Khởi Đầu. Mọi doanh nghiệp mới đều bắt đầu với 30 ngày dùng thử đầy đủ tính năng; ' +
    'sau khi đăng nhập, quản trị viên có thể chuyển về gói Khởi Đầu miễn phí ở trang Gói dịch vụ bất cứ lúc nào.',
  pro:
    'Bạn đã chọn gói Chuyên Nghiệp. Doanh nghiệp mới được dùng thử 30 ngày với giới hạn như gói Chuyên Nghiệp; ' +
    'thanh toán để tiếp tục dùng ở trang Gói dịch vụ sau khi đăng nhập.',
}

/** Bắt buộc nhập, chuỗi chỉ có khoảng trắng coi như trống (@NotBlank của máy chủ). */
function requiredRule(message: string): Rule {
  return {
    validator: (_, value?: string) => (isBlank(value) ? Promise.reject(new Error(message)) : Promise.resolve()),
  }
}

/** Độ dài sau khi bỏ khoảng trắng hai đầu (giá trị thật sự được gửi lên máy chủ). */
function lengthRule(min: number, max: number, message: string): Rule {
  return {
    validator: (_, value?: string) => {
      const length = value?.trim().length ?? 0
      if (length === 0 || (length >= min && length <= max)) return Promise.resolve()
      return Promise.reject(new Error(message))
    },
  }
}

const fieldLabel = (text: string) => <span className="text-xs font-semibold text-slate-700">{text}</span>

/**
 * Trang /register: đăng ký doanh nghiệp mới (doanh nghiệp, tài khoản quản trị đầu tiên, 30 ngày dùng thử).
 * Quy tắc kiểm tra trên form chép từ RegisterCompanyRequest của auth-service (xem registerForm.ts).
 * Máy chủ yêu cầu kích hoạt qua email thì ở lại trang và hiện hướng dẫn mở email; không thì chuyển sang đăng nhập.
 */
export default function RegisterPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [form] = Form.useForm<RegisterCompanyFormValues>()
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [result, setResult] = useState<CompanyRegistrationResult | null>(null)

  const planNote = PLAN_NOTES[searchParams.get('plan')?.toLowerCase() ?? '']

  // Đã đăng nhập thì vào thẳng hệ thống (giống trang đăng nhập)
  if (getToken()) {
    return <Navigate to="/" replace />
  }

  const handleFinish = async (values: RegisterCompanyFormValues) => {
    setSubmitting(true)
    setFormError(null)
    try {
      const payload = buildRegisterPayload(values)
      const registration = await registerCompany(payload)
      if (registration.activationRequired) {
        setResult(registration)
        return
      }
      // Máy chủ tắt yêu cầu kích hoạt (máy phát triển không có mail): doanh nghiệp hoạt động ngay
      message.success('Đăng ký thành công. Doanh nghiệp có 30 ngày dùng thử, mời bạn đăng nhập.')
      navigate('/login', { replace: true, state: { username: registration.admin?.username ?? payload.username } })
    } catch (error) {
      if (isHandledByGlobalDialog(error)) return
      if (axios.isAxiosError(error) && error.response) {
        const mapping = mapRegisterError(error.response.status, error.response.data as ErrorBody | undefined, FALLBACK_ERROR)
        const fields = (Object.entries(mapping.fieldErrors) as [RegisterField, string][]).map(([name, text]) => ({
          name,
          errors: [text],
        }))
        if (fields.length > 0) {
          form.setFields(fields)
          form.scrollToField(fields[0].name)
        }
        if (mapping.message) setFormError(mapping.message)
      } else {
        setFormError(getApiErrorMessage(error, FALLBACK_ERROR))
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (result) {
    const username = result.admin?.username
    return (
      <AuthCardLayout wide>
        <Result
          status="success"
          title="Đăng ký thành công"
          className="!px-0 !pt-2 !pb-4"
          subTitle={
            <span>
              Doanh nghiệp <strong>{result.company?.name}</strong> đã được tạo với 30 ngày dùng thử
              {result.subscription?.trialEndsAt ? ` đến ${formatDateTime(result.subscription.trialEndsAt)}` : ''}.
            </span>
          }
        />

        <Alert
          type="info"
          showIcon
          className="mb-5 rounded-lg"
          message={<span className="text-sm font-semibold">Kích hoạt doanh nghiệp qua email</span>}
          description={
            <span className="text-sm text-slate-600">
              Hệ thống đã gửi email kích hoạt tới <strong>{result.admin?.email}</strong>. Mở email đó và bấm liên kết
              kích hoạt (liên kết có hiệu lực 24 giờ), sau đó đăng nhập bằng tên đăng nhập{' '}
              <strong className="font-mono">{username}</strong>. Không thấy email thì kiểm tra thư mục thư rác hoặc gửi
              lại bên dưới.
            </span>
          }
        />

        <Descriptions size="small" column={1} bordered className="mb-5">
          <Descriptions.Item label="Mã doanh nghiệp">
            <span className="font-mono">{result.company?.code}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Tên đăng nhập quản trị">
            <span className="font-mono">{username}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Hết hạn dùng thử">{formatDateTime(result.subscription?.trialEndsAt)}</Descriptions.Item>
        </Descriptions>

        <ResendActivationForm initialEmail={result.admin?.email} />

        <div className="mt-4 text-center">
          <Link
            to="/login"
            state={{ username }}
            className="text-xs font-semibold text-blue-600 transition-colors hover:text-blue-700 hover:underline"
          >
            Đến trang Đăng nhập
          </Link>
        </div>
      </AuthCardLayout>
    )
  }

  return (
    <AuthCardLayout wide>
      <div className="mb-5">
        <Typography.Title level={3} style={{ marginBottom: 6, fontWeight: 700 }}>
          Đăng ký doanh nghiệp
        </Typography.Title>
        <Typography.Text type="secondary" className="text-sm">
          Tạo doanh nghiệp và tài khoản quản trị đầu tiên, nhận ngay 30 ngày dùng thử. Không cần thẻ thanh toán.
        </Typography.Text>
      </div>

      {planNote && <Alert type="info" showIcon className="mb-4 rounded-lg text-xs" message={planNote} />}

      <Form<RegisterCompanyFormValues>
        form={form}
        layout="vertical"
        requiredMark="optional"
        onFinish={handleFinish}
        autoComplete="off"
        initialValues={{ agreement: false }}
      >
        <div className="mb-1 text-xs font-bold uppercase tracking-wider text-slate-400">Thông tin doanh nghiệp</div>
        <Form.Item
          label={fieldLabel('Tên doanh nghiệp')}
          name="companyName"
          rules={[
            requiredRule('Vui lòng nhập tên doanh nghiệp'),
            lengthRule(L.companyNameMin, L.companyNameMax, 'Tên doanh nghiệp phải từ 2 đến 150 ký tự'),
          ]}
        >
          <Input
            prefix={<ApartmentOutlined className="text-slate-400" />}
            placeholder="Ví dụ: Công ty TNHH Kho Vận Mẫu"
            className="rounded-lg"
          />
        </Form.Item>

        <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
          <Form.Item
            label={fieldLabel('Mã số thuế')}
            name="taxCode"
            rules={[lengthRule(0, L.taxCodeMax, 'Mã số thuế tối đa 30 ký tự')]}
          >
            <Input prefix={<NumberOutlined className="text-slate-400" />} placeholder="Ví dụ: 0101234567" className="rounded-lg" />
          </Form.Item>
          <Form.Item
            label={fieldLabel('Số điện thoại')}
            name="phone"
            rules={[lengthRule(0, L.phoneMax, 'Số điện thoại tối đa 30 ký tự')]}
          >
            <Input
              prefix={<PhoneOutlined className="text-slate-400" />}
              placeholder="Ví dụ: 0901234567"
              autoComplete="tel"
              className="rounded-lg"
            />
          </Form.Item>
        </div>

        <div className="mb-1 text-xs font-bold uppercase tracking-wider text-slate-400">Tài khoản quản trị</div>
        <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
          <Form.Item
            label={fieldLabel('Họ và tên người quản trị')}
            name="fullName"
            rules={[
              requiredRule('Vui lòng nhập họ và tên người quản trị'),
              lengthRule(0, L.fullNameMax, 'Họ và tên tối đa 100 ký tự'),
            ]}
          >
            <Input
              prefix={<UserOutlined className="text-slate-400" />}
              placeholder="Ví dụ: Nguyễn Văn An"
              autoComplete="name"
              className="rounded-lg"
            />
          </Form.Item>
          <Form.Item
            label={fieldLabel('Tên đăng nhập')}
            name="username"
            rules={[
              requiredRule('Vui lòng nhập tên đăng nhập'),
              lengthRule(L.usernameMin, L.usernameMax, 'Tên đăng nhập từ 3 đến 50 ký tự'),
              {
                validator: (_, value?: string) =>
                  isBlank(value) || USERNAME_PATTERN.test(value!.trim())
                    ? Promise.resolve()
                    : Promise.reject(
                        new Error('Tên đăng nhập chỉ chứa chữ cái, chữ số, dấu chấm, gạch dưới hoặc gạch ngang'),
                      ),
              },
            ]}
          >
            <Input
              prefix={<IdcardOutlined className="text-slate-400" />}
              placeholder="Ví dụ: an.nguyen"
              autoComplete="username"
              className="rounded-lg"
            />
          </Form.Item>
        </div>

        <Form.Item
          label={fieldLabel('Email')}
          name="email"
          extra={<span className="text-[11px]">Liên kết kích hoạt doanh nghiệp được gửi tới email này.</span>}
          rules={[
            requiredRule('Vui lòng nhập email'),
            {
              validator: (_, value?: string) =>
                isBlank(value) || isValidEmail(value!.trim())
                  ? Promise.resolve()
                  : Promise.reject(new Error('Email không đúng định dạng')),
            },
            lengthRule(0, L.emailMax, 'Email tối đa 100 ký tự'),
          ]}
        >
          <Input
            prefix={<MailOutlined className="text-slate-400" />}
            placeholder="Ví dụ: an@congty.vn"
            autoComplete="email"
            className="rounded-lg"
          />
        </Form.Item>

        <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
          <Form.Item
            label={fieldLabel('Mật khẩu')}
            name="password"
            rules={[
              requiredRule('Vui lòng nhập mật khẩu'),
              {
                validator: (_, value?: string) => {
                  const length = value?.length ?? 0
                  if (isBlank(value) || (length >= L.passwordMin && length <= L.passwordMax)) return Promise.resolve()
                  return Promise.reject(new Error('Mật khẩu từ 6 đến 72 ký tự'))
                },
              },
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
            label={fieldLabel('Nhập lại mật khẩu')}
            name="confirmPassword"
            dependencies={['password']}
            rules={[
              { required: true, message: 'Vui lòng nhập lại mật khẩu' },
              ({ getFieldValue }) => ({
                validator(_, value?: string) {
                  if (!value || getFieldValue('password') === value) return Promise.resolve()
                  return Promise.reject(new Error('Mật khẩu nhập lại không khớp'))
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<LockOutlined className="text-slate-400" />}
              placeholder="Nhập lại mật khẩu"
              autoComplete="new-password"
              className="rounded-lg"
            />
          </Form.Item>
        </div>

        <Form.Item
          name="agreement"
          valuePropName="checked"
          className="mb-4"
          rules={[
            {
              validator: (_, value?: boolean) =>
                value ? Promise.resolve() : Promise.reject(new Error('Vui lòng đồng ý với điều khoản sử dụng')),
            },
          ]}
        >
          <Checkbox className="text-xs text-slate-600">
            Tôi đồng ý với Điều khoản dịch vụ và chịu trách nhiệm về thông tin doanh nghiệp đã khai báo.
          </Checkbox>
        </Form.Item>

        {formError && <Alert type="error" showIcon message={formError} className="mb-4 rounded-lg" />}

        <Button
          type="primary"
          htmlType="submit"
          size="large"
          block
          loading={submitting}
          disabled={submitting}
          className="h-11 rounded-lg font-medium shadow-sm"
        >
          Đăng ký và nhận 30 ngày dùng thử
        </Button>

        <div className="mt-3 flex items-center justify-center gap-2 text-[11px] text-slate-500">
          <SafetyCertificateOutlined className="text-emerald-600" />
          <span>Dữ liệu của mỗi doanh nghiệp được tách riêng, doanh nghiệp khác không xem được</span>
        </div>

        <div className="mt-4 text-center">
          <span className="text-xs text-slate-500">Doanh nghiệp đã có tài khoản? </span>
          <Link
            to="/login"
            className="text-xs font-bold text-blue-600 transition-colors hover:text-blue-700 hover:underline"
          >
            Đăng nhập
          </Link>
        </div>
      </Form>
    </AuthCardLayout>
  )
}
