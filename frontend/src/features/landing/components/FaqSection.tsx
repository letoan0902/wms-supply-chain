import {
  ApartmentOutlined,
  ClockCircleOutlined,
  CustomerServiceOutlined,
  IdcardOutlined,
  LockOutlined,
  MailOutlined,
  NumberOutlined,
  PhoneOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Checkbox, Collapse, Form, Input, Tag, Typography } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { FAQS } from '../pricingConfig'
import { registerCompany } from '../../auth/authApi'
import { getApiErrorMessage } from '../../../services/api'

const { Title, Text, Paragraph } = Typography

interface RegistrationFormValues {
  companyName: string
  taxCode?: string
  username: string
  password: string
  email: string
  phone?: string
  agreement: boolean
}

export default function FaqSection() {
  const { message } = App.useApp()
  const [form] = Form.useForm<RegistrationFormValues>()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [submittedData, setSubmittedData] = useState<{
    companyName: string
    username: string
    email: string
  } | null>(null)

  const handleSubmit = async (values: RegistrationFormValues) => {
    setIsSubmitting(true)
    setErrorMessage(null)
    try {
      await registerCompany({
        companyName: values.companyName.trim(),
        taxCode: values.taxCode?.trim() || undefined,
        username: values.username.trim(),
        fullName: values.email.substring(10, values.email.length),
        password: values.password,
        email: values.email.trim(),
        phone: values.phone?.trim() || undefined,
      })
      setSubmittedData({
        companyName: values.companyName.trim(),
        username: values.username.trim(),
        email: values.email.trim(),
      })
      message.success('Gửi yêu cầu đăng ký thành công')
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err, 'Đăng ký doanh nghiệp không thành công. Vui lòng thử lại.')
      setErrorMessage(msg)
    } finally {
      setIsSubmitting(false)
    }
  }

  // Danh sách FAQ cho Collapse
  const faqItems = FAQS.map((faq) => ({
    key: faq.id,
    label: (
      <span className="text-sm font-semibold text-slate-800">
        {faq.question}
      </span>
    ),
    children: (
      <p className="text-xs sm:text-sm leading-relaxed text-slate-600">
        {faq.answer}
      </p>
    ),
  }))

  return (
    <section id="faq" className="py-20 bg-slate-50 border-t border-slate-200">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center mb-14 max-w-3xl mx-auto">
          <div className="inline-flex items-center gap-2 rounded-full bg-blue-100/80 px-3.5 py-1 text-xs font-semibold text-blue-700 mb-3 border border-blue-200/60">
            <QuestionCircleOutlined />
            Giải Đáp Thắc Mắc & Trải Nghiệm Dùng Thử
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            Sẵn sàng chuyển đổi số cho kho bãi của bạn
          </h2>
          <p className="mt-3 text-base text-slate-600">
            Đăng ký nhận ngay 30 ngày dùng thử trọn vẹn Gói Chuyên Nghiệp (Pro) cho công ty,
            hoặc tra cứu các câu hỏi thường gặp về cách vận hành hệ thống.
          </p>
        </div>

        {/* 2-Column Split Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-10 items-start">
          {/* CỘT 1: Form đăng ký dùng thử 30 ngày */}
          <div
            id="trial-register"
            className="lg:col-span-6 bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 shadow-xs relative scroll-mt-24"
          >
            {/* Tiêu đề khung */}
            <div className="mb-6">
              <div className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 border border-emerald-200 px-3 py-0.5 text-xs font-medium text-emerald-700 mb-2.5">
                <ThunderboltOutlined className="text-emerald-600" />
                Miễn phí 30 ngày • Phê duyệt nhanh chóng
              </div>
              <Title level={4} className="!mb-1 font-bold text-slate-900">
                Đăng ký Dùng thử cho Doanh nghiệp
              </Title>
              <Text type="secondary" className="text-xs sm:text-sm">
                Điền thông tin doanh nghiệp để quản trị viên hệ thống phê duyệt và cấp tài khoản dùng thử 30 ngày.
              </Text>
            </div>

            {submittedData ? (
              <div className="py-6 px-4 bg-emerald-50/50 rounded-xl border border-emerald-200 text-center space-y-4">
                <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto text-2xl">
                  <ClockCircleOutlined />
                </div>
                <div>
                  <h4 className="text-base font-bold text-slate-900">Yêu cầu đã được gửi đi thành công</h4>
                  <p className="text-xs text-slate-600 mt-1 max-w-md mx-auto">
                    Thông tin đăng ký của doanh nghiệp <strong>{submittedData.companyName}</strong> đã được gửi tới Quản trị viên hệ thống. Chúng tôi sẽ kiểm tra và kích hoạt tài khoản trong thời gian sớm nhất.
                  </p>
                </div>
                <div className="bg-white p-3.5 rounded-lg border border-emerald-100 text-left text-xs space-y-1.5 max-w-sm mx-auto">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Tên doanh nghiệp:</span>
                    <span className="font-semibold text-slate-800">{submittedData.companyName}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Tên đăng nhập:</span>
                    <span className="font-mono font-medium text-slate-800">{submittedData.username}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Email quản trị:</span>
                    <span className="font-medium text-slate-800">{submittedData.email}</span>
                  </div>
                  <div className="flex justify-between items-center pt-1 border-t border-slate-100">
                    <span className="text-slate-500">Trạng thái:</span>
                    <Tag color="warning" className="font-semibold text-[11px] m-0">
                      Chờ Quản trị viên duyệt (Pending)
                    </Tag>
                  </div>
                </div>
                <div className="pt-2">
                  <Button
                    type="default"
                    onClick={() => {
                      setSubmittedData(null)
                      form.resetFields()
                    }}
                    className="text-xs font-medium"
                  >
                    Gửi yêu cầu cho doanh nghiệp khác
                  </Button>
                </div>
              </div>
            ) : (
              <Form
                form={form}
                layout="vertical"
                onFinish={handleSubmit}
                requiredMark="optional"
                scrollToFirstError
              >
                {errorMessage && (
                  <Alert
                    type="error"
                    showIcon
                    message={errorMessage}
                    className="mb-4 text-xs"
                    closable
                    onClose={() => setErrorMessage(null)}
                  />
                )}

                {/* Tên công ty & Mã số thuế */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                  <Form.Item
                    label={<span className="text-xs font-semibold text-slate-700">Tên công ty / Doanh nghiệp</span>}
                    name="companyName"
                    rules={[
                      { required: true, message: 'Vui lòng nhập tên công ty' },
                      { min: 2, max: 100, message: 'Tên công ty từ 2 đến 100 ký tự' },
                    ]}
                  >
                    <Input
                      prefix={<ApartmentOutlined className="text-slate-400 text-xs" />}
                      placeholder="Công ty CP Vận tải Á Châu"
                      className="h-10 text-sm rounded-lg"
                    />
                  </Form.Item>

                  <Form.Item
                    label={<span className="text-xs font-semibold text-slate-700">Mã số thuế (nếu có)</span>}
                    name="taxCode"
                    rules={[
                      { min: 10, max: 14, message: 'Mã số thuế từ 10 đến 14 ký tự' },
                    ]}
                  >
                    <Input
                      prefix={<NumberOutlined className="text-slate-400 text-xs" />}
                      placeholder="0101234567"
                      className="h-10 text-sm rounded-lg"
                    />
                  </Form.Item>
                </div>

                {/* Tên đăng nhập & Mật khẩu của tài khoản quản trị */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                  <Form.Item
                    label={<span className="text-xs font-semibold text-slate-700">Tên đăng nhập quản trị</span>}
                    name="username"
                    rules={[
                      { required: true, message: 'Vui lòng nhập tên đăng nhập' },
                      { min: 3, max: 50, message: 'Tên đăng nhập từ 3 đến 50 ký tự' },
                      {
                        pattern: /^[a-zA-Z0-9._-]+$/,
                        message: 'Chỉ dùng chữ cái, chữ số, dấu chấm, gạch dưới hoặc gạch ngang',
                      },
                    ]}
                  >
                    <Input
                      prefix={<IdcardOutlined className="text-slate-400 text-xs" />}
                      placeholder="an.nguyen"
                      autoComplete="username"
                      className="h-10 text-sm rounded-lg"
                    />
                  </Form.Item>

                  <Form.Item
                    label={<span className="text-xs font-semibold text-slate-700">Mật khẩu</span>}
                    name="password"
                    rules={[
                      { required: true, message: 'Vui lòng nhập mật khẩu' },
                      { min: 6, max: 72, message: 'Mật khẩu từ 6 đến 72 ký tự' },
                    ]}
                  >
                    <Input.Password
                      prefix={<LockOutlined className="text-slate-400 text-xs" />}
                      autoComplete="new-password"
                      className="h-10 text-sm rounded-lg"
                    />
                  </Form.Item>
                </div>

                {/* Email công ty & Số điện thoại */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                  <Form.Item
                    label={<span className="text-xs font-semibold text-slate-700">Email công việc</span>}
                    name="email"
                    rules={[
                      { required: true, message: 'Vui lòng nhập email công việc' },
                      { type: 'email', message: 'Địa chỉ email không hợp lệ' },
                    ]}
                  >
                    <Input
                      prefix={<MailOutlined className="text-slate-400 text-xs" />}
                      placeholder="an.nguyen@company.vn"
                      className="h-10 text-sm rounded-lg"
                    />
                  </Form.Item>

                  <Form.Item
                    label={<span className="text-xs font-semibold text-slate-700">Số điện thoại liên hệ</span>}
                    name="phone"
                    rules={[
                      { pattern: /^[0-9+\s-]{9,15}$/, message: 'Số điện thoại không hợp lệ' },
                    ]}
                  >
                    <Input
                      prefix={<PhoneOutlined className="text-slate-400 text-xs" />}
                      placeholder="0912 345 678"
                      className="h-10 text-sm rounded-lg"
                    />
                  </Form.Item>
                </div>

                {/* Điều khoản sử dụng */}
                <Form.Item
                  name="agreement"
                  valuePropName="checked"
                  rules={[
                    {
                      validator: (_, value) =>
                        value ? Promise.resolve() : Promise.reject(new Error('Vui lòng đồng ý với điều khoản sử dụng')),
                    },
                  ]}
                  className="mb-4"
                >
                  <Checkbox className="text-xs text-slate-600">
                    Tôi đồng ý với <span className="text-blue-600 hover:underline">Điều khoản dịch vụ</span> của hệ thống.
                  </Checkbox>
                </Form.Item>

                {/* Submit button */}
                <Form.Item className="mb-2">
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={isSubmitting}
                    className="w-full h-11 bg-blue-600 hover:!bg-blue-500 font-bold rounded-lg text-sm shadow-sm"
                  >
                    Gửi yêu cầu đăng ký dùng thử 30 ngày
                  </Button>
                </Form.Item>

                <div className="flex items-center justify-center gap-2 text-[11px] text-slate-500 pt-1">
                  <SafetyCertificateOutlined className="text-emerald-600" />
                  <span>Dữ liệu của mỗi doanh nghiệp được tách riêng biệt và bảo mật tuyệt đối</span>
                </div>
              </Form>
            )}
          </div>

          {/* CỘT 2: Câu hỏi thường gặp (FAQ) */}
          <div className="lg:col-span-6 flex flex-col gap-4">
            <div className="rounded-2xl bg-white p-5 sm:p-7 border border-slate-200 shadow-xs">
              <div className="mb-4">
                <Title level={4} className="!mb-1 font-bold text-slate-900">
                  Câu hỏi thường gặp về gói dịch vụ
                </Title>
                <Paragraph className="!mb-0 text-xs sm:text-sm text-slate-600">
                  Tổng hợp giải đáp các băn khoăn phổ biến nhất của các đơn vị kho bãi và chuỗi bán lẻ.
                </Paragraph>
              </div>

              {/* Accordion FAQ List */}
              <Collapse
                items={faqItems}
                defaultActiveKey={['faq-trial']}
                bordered={false}
                className="bg-transparent [&_.ant-collapse-item]:border-b [&_.ant-collapse-item]:border-slate-100 [&_.ant-collapse-item:last-child]:border-0 [&_.ant-collapse-header]:!py-3.5"
              />
            </div>

            {/* Support Callout Box */}
            <div className="rounded-2xl bg-blue-50/70 border border-blue-200/80 p-5 flex items-start gap-4">
              <div className="w-10 h-10 rounded-xl bg-blue-600 text-white flex items-center justify-center shrink-0 shadow-xs">
                <CustomerServiceOutlined className="text-lg" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-900 mb-1">
                  Cần tư vấn giải pháp kho đặc thù cho doanh nghiệp?
                </h4>
                <p className="text-xs text-slate-600 mb-2.5 leading-relaxed">
                  Gói Doanh nghiệp (nhiều kho, tích hợp riêng) được tư vấn theo nhu cầu: chọn "Liên hệ Doanh nghiệp" ở mục Bảng giá để gửi yêu cầu.
                </p>
                <div className="flex flex-wrap items-center gap-3 text-xs font-semibold text-blue-700">
                  <Link to="/login" className="hover:underline">
                    Đã có tài khoản doanh nghiệp? Đăng nhập
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
