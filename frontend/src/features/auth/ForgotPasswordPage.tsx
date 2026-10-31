import {
  ArrowLeftOutlined,
  MailOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Carousel,
  Col,
  Form,
  Input,
  Result,
  Row,
  Typography,
} from 'antd'
import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { getToken } from '../../utils/storage'

interface ForgotPasswordFormValues {
  username: string
  email: string
  fullName?: string
  reason?: string
}

const forgotSlides = [
  {
    image: 'https://forestrycareers.com/media/cudkvo24/logistics-manager-forestry.jpg',
    tag: 'Bảo Mật & Phân Quyền',
    title: 'Xác Thực Nghiêm Ngặt & An Toàn Dữ Liệu',
    description:
      'Mọi yêu cầu cấp lại mật khẩu đều được xác minh chặt chẽ bởi Quản trị viên (Admin) nhằm bảo vệ an toàn kho vận và tài sản doanh nghiệp.',
  },
  {
    image: 'https://mecaluxuk.cdnwm.com/blog/img/warehouse-manager.1.10.jpg',
    tag: 'Hỗ Trợ Nhân Sự Kho',
    title: 'Duy Trì Vận Hành Liên Tục & Không Gián Đoạn',
    description:
      'Đội ngũ Admin hỗ trợ khôi phục tài khoản nhân viên nhanh chóng, bảo đảm hoạt động nhập xuất và kiểm kê kho diễn ra đúng tiến độ.',
  },
  {
    image: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRiGsgzDuCAW_S9ZrHq1FNEBPgTkRFg4YjKrD1uJ9anCpB0PMUhTdygU836&s=10',
    tag: 'Quản Trị Người Dùng',
    title: 'Phân Quyền Tập Trung Theo Từng Kho Hàng',
    description:
      'Hệ thống WMS áp dụng mô hình phân quyền 6 vai trò kết hợp gán quyền theo kho, đảm bảo mỗi nhân sự truy cập đúng phạm vi trách nhiệm.',
  },
]

export default function ForgotPasswordPage() {
  const { message } = App.useApp()
  const [form] = Form.useForm<ForgotPasswordFormValues>()
  const [submitting, setSubmitting] = useState(false)
  const [isSuccess, setIsSuccess] = useState(false)
  const [submittedData, setSubmittedData] = useState<ForgotPasswordFormValues | null>(null)

  // Đã đăng nhập thì quay về trang chủ
  if (getToken()) {
    return <Navigate to="/" replace />
  }

  const handleFinish = async (values: ForgotPasswordFormValues) => {
    setSubmitting(true)
    try {
      // Giả lập gửi yêu cầu đến Admin (Frontend - chưa tích hợp API Backend)
      await new Promise((resolve) => setTimeout(resolve, 800))
      setSubmittedData(values)
      setIsSuccess(true)
      message.success('Đã gửi yêu cầu cấp lại mật khẩu tới Quản trị viên!')
    } catch {
      message.error('Không thể gửi yêu cầu. Vui lòng thử lại sau.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-white">
      {/* CỘT TRÁI: Form Quên mật khẩu - Nền Ombre tinh tế, không sinh thanh cuộn */}
      <div className="flex h-full w-full flex-col justify-between bg-gradient-to-br from-slate-50 via-blue-50/25 to-slate-100/70 px-8 py-8 sm:px-14 lg:w-5/12 lg:px-12 xl:w-2/5 xl:px-16">
        {/* Phần đầu: Nhãn chỉ hiển thị trên Mobile (ẩn trên Desktop để không trùng logo) */}
        <div className="flex items-center gap-2.5 lg:invisible">
          <img
            src="/logo.png"
            alt="WMS Logo"
            className="h-9 w-9 rounded-lg bg-white object-contain p-1 shadow-xs"
          />
          <div>
            <span className="text-base font-bold text-slate-800">WMS Supply Chain</span>
            <span className="ml-2 hidden rounded bg-blue-100 px-1.5 py-0.5 text-[10px] font-semibold text-blue-700 sm:inline">
              SECURITY
            </span>
          </div>
        </div>

        {/* Khung giữa: Form nhập liệu hoặc Result thông báo thành công */}
        <div className="mx-auto w-full max-w-sm">
          {isSuccess ? (
            <div className="rounded-xl border border-white/60 bg-white/80 p-6 shadow-sm backdrop-blur-sm">
              <Result
                status="success"
                title={<span className="text-xl font-bold text-slate-800">Đã gửi yêu cầu</span>}
                subTitle={
                  <div className="space-y-2 text-xs text-slate-600">
                    <p>
                      Yêu cầu của tài khoản <strong>{submittedData?.username}</strong> ({submittedData?.email}) đã được gửi tới <strong>Admin</strong>.
                    </p>
                    <p className="text-slate-400">
                      Quản trị viên sẽ xác minh và gửi hướng dẫn kích hoạt qua email của bạn.
                    </p>
                  </div>
                }
                extra={[
                  <Link to="/login" key="login" className="block">
                    <Button type="primary" size="large" block className="h-10 rounded-lg">
                      Quay lại Đăng nhập
                    </Button>
                  </Link>,
                  <Button
                    key="resend"
                    type="link"
                    size="small"
                    block
                    className="mt-2 text-xs"
                    onClick={() => {
                      setIsSuccess(false)
                      setSubmittedData(null)
                      form.resetFields()
                    }}
                  >
                    Gửi một yêu cầu khác
                  </Button>,
                ]}
              />
            </div>
          ) : (
            <div>
              <div className="mb-5">
                <Typography.Title level={3} style={{ marginBottom: 4, fontWeight: 700 }}>
                  Quên mật khẩu
                </Typography.Title>
                <Typography.Text type="secondary" className="text-xs">
                  Gửi yêu cầu tới Quản trị viên (Admin) để cấp lại mật khẩu tài khoản.
                </Typography.Text>
              </div>

              <Form<ForgotPasswordFormValues>
                form={form}
                layout="vertical"
                requiredMark={false}
                onFinish={handleFinish}
                autoComplete="off"
              >
                <Form.Item
                  label={<span className="text-xs font-semibold text-slate-700">Tên đăng nhập</span>}
                  name="username"
                  className="mb-3"
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
                  label={<span className="text-xs font-semibold text-slate-700">Email nhận xác nhận</span>}
                  name="email"
                  className="mb-3"
                  rules={[
                    { required: true, message: 'Vui lòng nhập email đăng ký' },
                    { type: 'email', message: 'Địa chỉ email không đúng định dạng' },
                  ]}
                >
                  <Input
                    size="large"
                    prefix={<MailOutlined className="text-slate-400" />}
                    placeholder="Ví dụ: yourname@wms.vn"
                    autoComplete="email"
                    className="rounded-lg"
                  />
                </Form.Item>

                <Row gutter={10}>
                  <Col span={12}>
                    <Form.Item
                      label={<span className="text-xs font-semibold text-slate-700">Họ và tên</span>}
                      name="fullName"
                      className="mb-3"
                    >
                      <Input
                        size="large"
                        placeholder="Họ tên đối chiếu"
                        className="rounded-lg"
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      label={<span className="text-xs font-semibold text-slate-700">Lý do yêu cầu</span>}
                      name="reason"
                      className="mb-3"
                    >
                      <Input
                        size="large"
                        placeholder="Quên MK, đổi máy..."
                        className="rounded-lg"
                      />
                    </Form.Item>
                  </Col>
                </Row>

                {/* Chính sách bảo mật đặt ở cuối form trước nút gửi */}
                <Alert
                  type="info"
                  showIcon
                  message={
                    <span className="text-[11px] leading-tight text-slate-600">
                      <strong>Chính sách:</strong> Yêu cầu cần được Admin phê duyệt để đảm bảo an toàn truy cập dữ liệu kho.
                    </span>
                  }
                  className="mb-4 rounded-lg border-blue-100 bg-blue-50/70 py-1.5"
                />

                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  block
                  icon={<SendOutlined />}
                  loading={submitting}
                  className="h-11 rounded-lg font-medium shadow-sm transition-all hover:shadow"
                >
                  Gửi yêu cầu tới Admin
                </Button>

                <div className="mt-4 text-center">
                  <Link
                    to="/login"
                    className="inline-flex items-center gap-1.5 text-xs text-slate-500 transition-colors hover:text-blue-600"
                  >
                    <ArrowLeftOutlined /> Quay lại trang Đăng nhập
                  </Link>
                </div>
              </Form>
            </div>
          )}
        </div>

        {/* Chân trang bản quyền */}
        <div className="text-center text-xs text-slate-400">
          © 2026 WMS Supply Chain System. All rights reserved.
        </div>
      </div>

      {/* CỘT PHẢI: Slide hình ảnh minh họa mục tiêu an toàn & quản trị (đối xứng ngược với LoginPage) */}
      <div className="relative hidden h-full select-none bg-slate-950 lg:block lg:w-7/12 xl:w-3/5">
        <Carousel
          autoplay
          effect="fade"
          autoplaySpeed={4500}
          className="h-full [&_.slick-slider]:h-full [&_.slick-list]:h-full [&_.slick-track]:h-full [&_.slick-slide]:h-full [&_.slick-slide>div]:h-full [&_.slick-dots]:!bottom-10 [&_.slick-dots]:!left-12 [&_.slick-dots]:!justify-start [&_.slick-dots_li_button]:!h-1.5 [&_.slick-dots_li_button]:!w-8 [&_.slick-dots_li_button]:!rounded-full [&_.slick-dots_li_button]:!bg-white/50 [&_.slick-dots_li.slick-active_button]:!bg-white"
        >
          {forgotSlides.map((slide, index) => (
            <div key={index} className="relative h-screen w-full">
              <img
                src={slide.image}
                alt={slide.title}
                className="h-full w-full object-cover"
              />
              {/* Lớp phủ gradient tối dần xuống dưới */}
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/45 to-slate-900/20" />

              {/* Thông tin thông điệp an toàn thông tin & bảo mật */}
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

        {/* Logo góc trên bên trái của banner hình ảnh */}
        <div className="absolute left-10 top-8 z-20 flex items-center gap-3 text-white drop-shadow-md">
          <img
            src="/logo.png"
            alt="WMS Logo"
            className="h-11 w-11 rounded-xl bg-white object-contain p-1 shadow-lg"
          />
          <div>
            <div className="text-base font-extrabold tracking-wide text-white">WMS SUPPLY CHAIN</div>
            <div className="text-[11px] text-slate-300">Identity & Access Management</div>
          </div>
        </div>
      </div>
    </div>
  )
}
