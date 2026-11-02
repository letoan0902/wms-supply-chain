import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { App, Button, Carousel, Checkbox, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { getApiErrorMessage, getErrorStatus } from '../../services/api'
import type { LoginRequest } from '../../types/auth'
import { getToken, saveAuth } from '../../utils/storage'
import { login } from './authApi'

interface LocationState {
  from?: { pathname: string }
}

const slides = [
  {
    image: 'https://mecaluxuk.cdnwm.com/blog/img/warehouse-manager.1.10.jpg',
    tag: 'Tối ưu Vận hành Kho',
    title: 'Quản trị Đa Kho & Sơ đồ Ô/Kệ Toàn diện',
    description:
      'Kiểm soát trực quan vị trí Zone, Rack, Bin theo thời gian thực; tối ưu hóa diện tích lưu trữ và tốc độ luân chuyển hàng.',
  },
  {
    image: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQDi_STULZ9VoDlJH1_iA7Z0iauwo-ABUzjnw8PS3PDDQHMXCYgt3KWOz0&s=10',
    tag: 'Chuỗi Cung ứng Liền mạch',
    title: 'Tự động hóa Luồng Mua hàng & Xuất bán',
    description:
      'Đồng bộ từ Đơn mua (PO) đến Phiếu nhập (GRN), Đơn bán (SO) đến Phiếu xuất (GDN) chính xác và nhanh chóng.',
  },
  {
    image: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRiGsgzDuCAW_S9ZrHq1FNEBPgTkRFg4YjKrD1uJ9anCpB0PMUhTdygU836&s=10',
    tag: 'Công nghệ Định danh',
    title: 'Quản lý Mã vạch Barcode & QR Thông minh',
    description:
      'Quản lý SKU đa cấp, in tem nhãn tiêu chuẩn và quy đổi đơn vị đo lường linh hoạt chỉ với một thao tác.',
  },
  {
    image: 'https://forestrycareers.com/media/cudkvo24/logistics-manager-forestry.jpg',
    tag: 'Dữ liệu Thời gian thực',
    title: 'Giám sát Tồn kho & Cảnh báo Kịp thời',
    description:
      'Minh bạch tồn kho thực tế, cảnh báo tồn an toàn và phát hiện hàng cận date/hàng tồn chậm luân chuyển.',
  },
]

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
      saveAuth(result.accessToken, { username: result.username, roles: result.roles }, result.refreshToken)
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
    <div className="flex h-screen w-screen overflow-hidden bg-white">
      {/* CỘT TRÁI: Slide hình ảnh minh họa mục tiêu & giải pháp hệ thống */}
      <div className="relative hidden h-full select-none bg-slate-950 lg:block lg:w-7/12 xl:w-3/5">
        <Carousel
          autoplay
          effect="fade"
          autoplaySpeed={4500}
          className="h-full [&_.slick-slider]:h-full [&_.slick-list]:h-full [&_.slick-track]:h-full [&_.slick-slide]:h-full [&_.slick-slide>div]:h-full [&_.slick-dots]:!bottom-10 [&_.slick-dots]:!left-12 [&_.slick-dots]:!justify-start [&_.slick-dots_li_button]:!h-1.5 [&_.slick-dots_li_button]:!w-8 [&_.slick-dots_li_button]:!rounded-full [&_.slick-dots_li_button]:!bg-white/50 [&_.slick-dots_li.slick-active_button]:!bg-white"
        >
          {slides.map((slide, index) => (
            <div key={index} className="relative h-screen w-full">
              <img
                src={slide.image}
                alt={slide.title}
                className="h-full w-full object-cover"
              />
              {/* Lớp phủ gradient tối dần xuống dưới để tôn chữ */}
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/45 to-slate-900/20" />

              {/* Thông tin mục tiêu hệ thống */}
              <div className="absolute bottom-20 left-12 right-16 z-10 text-white">
                <span className="mb-3 inline-block rounded-full bg-blue-600/90 px-3.5 py-1 text-xs font-semibold uppercase tracking-wider text-white shadow-sm backdrop-blur-md">
                  {slide.tag}
                </span>
                <h2 className="mb-2 text-2xl font-bold leading-snug text-white drop-shadow-md xl:text-3xl">
                  {slide.title}
                </h2>
                <p className="max-w-xl text-sm leading-relaxed text-slate-200 drop-shadow xl:text-base">
                  {slide.description}
                </p>
              </div>
            </div>
          ))}
        </Carousel>

        {/* Logo góc trên bên trái của banner */}
        <div className="absolute left-10 top-8 z-20 flex items-center gap-3 text-white drop-shadow-md">
          <img
            src="/logo.png"
            alt="WMS Logo"
            className="h-15 w-15 rounded-xl bg-white object-contain p-1 shadow-lg"
          />
          <div>
            <div className="text-base font-extrabold tracking-wide text-white">WMS SUPPLY CHAIN</div>
            <div className="text-[11px] text-slate-300">Intelligent Logistics Platform</div>
          </div>
        </div>
      </div>

      {/* CỘT PHẢI: Form Đăng nhập - Nền Ombre hài hòa liên kết với hình ảnh, không sinh thanh cuộn */}
      <div className="flex h-full w-full flex-col justify-between bg-gradient-to-br from-slate-50 via-blue-50/25 to-slate-100/70 px-8 py-8 sm:px-14 lg:w-5/12 lg:px-12 xl:w-2/5 xl:px-16">
        {/* Phần đầu: Nhãn hiển thị trên Mobile (khi ẩn slide trái) */}
        <div className="flex items-center gap-2.5 lg:invisible">
          <img
            src="/logo.png"
            alt="WMS Logo"
            className="h-9 w-9 rounded-lg bg-white object-contain p-1 shadow-xs"
          />
          <span className="text-base font-bold text-slate-800">WMS Supply Chain</span>
        </div>

        {/* Khung giữa: Form nhập liệu */}
        <div className="mx-auto w-full max-w-sm">
          <div className="mb-6">
            <Typography.Title level={3} style={{ marginBottom: 6, fontWeight: 700 }}>
              Đăng nhập
            </Typography.Title>
            <Typography.Text type="secondary" className="text-sm">
              Nhập tài khoản để truy cập hệ thống quản trị kho.
            </Typography.Text>
          </div>

          <Form<LoginRequest>
            layout="vertical"
            requiredMark={false}
            onFinish={handleFinish}
            autoComplete="off"
          >
            <Form.Item
              label={<span className="text-xs font-semibold text-slate-700">Tên đăng nhập</span>}
              name="username"
              className="mb-4"
              rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
            >
              <Input
                size="large"
                prefix={<UserOutlined className="text-slate-400" />}
                placeholder="Ví dụ: admin, manager..."
                autoComplete="username"
                className="rounded-lg"
              />
            </Form.Item>

            <Form.Item
              label={<span className="text-xs font-semibold text-slate-700">Mật khẩu</span>}
              name="password"
              className="mb-2"
              rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
            >
              <Input.Password
                size="large"
                prefix={<LockOutlined className="text-slate-400" />}
                placeholder="Nhập mật khẩu"
                autoComplete="current-password"
                className="rounded-lg"
              />
            </Form.Item>

            <div className="mb-5 flex items-center justify-between pt-1">
              <Checkbox className="text-xs text-slate-600">
                Ghi nhớ đăng nhập
              </Checkbox>
              <Link
                to="/forgot-password"
                className="text-xs font-medium text-blue-600 transition-colors hover:text-blue-700 hover:underline"
              >
                Quên mật khẩu?
              </Link>
            </div>

            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={submitting}
              className="h-11 rounded-lg font-medium shadow-sm transition-all hover:shadow"
            >
              Đăng nhập
            </Button>
          </Form>

          {/* Hướng dẫn tài khoản kiểm thử mặc định */}
          <div className="mt-5 rounded-lg border border-slate-100 bg-slate-50/70 p-3 text-center text-xs text-slate-500">
            Tài khoản mặc định: <span className="font-semibold text-slate-700">admin</span> | Mật khẩu: <span className="font-semibold text-slate-700">Demopassword</span>
          </div>
        </div>

        {/* Chân trang bản quyền */}
        <div className="text-center text-xs text-slate-400">
          © 2026 WMS Supply Chain System. All rights reserved.
        </div>
      </div>
    </div>
  )
}
