import {
  ApartmentOutlined,
  CheckCircleFilled,
  CustomerServiceOutlined,
  MailOutlined,
  PhoneOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Checkbox,
  Collapse,
  Form,
  Input,
  Select,
  Tag,
  Typography,
} from 'antd'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { FAQS } from '../pricingConfig'

const { Title, Text, Paragraph } = Typography

interface CompanyRegistrationValues {
  companyName: string
  contactName: string
  email: string
  phone: string
  warehouseScale: string
  primaryNeed: string
  agreement: boolean
}

export default function FaqSection() {
  const { message } = App.useApp()
  const [form] = Form.useForm<CompanyRegistrationValues>()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [registeredData, setRegisteredData] = useState<CompanyRegistrationValues | null>(null)

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

  // Xử lý gửi Form đăng ký dùng thử (Chỉ frontend, giả lập submit)
  const handleSubmit = (values: CompanyRegistrationValues) => {
    setIsSubmitting(true)
    setTimeout(() => {
      setIsSubmitting(false)
      setRegisteredData(values)
      message.success('Đăng ký tài khoản dùng thử doanh nghiệp thành công!')
    }, 900)
  }

  // Đăng ký lại / Reset form
  const handleResetForm = () => {
    setRegisteredData(null)
    form.resetFields()
  }

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
          {/* CỘT 1: Form Đăng ký tài khoản dùng thử cho Doanh nghiệp */}
          <div className="lg:col-span-6 bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 shadow-xs relative">
            {/* Header Form */}
            <div className="mb-6">
              <div className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 border border-emerald-200 px-3 py-0.5 text-xs font-medium text-emerald-700 mb-2.5">
                <ThunderboltOutlined className="text-emerald-600" />
                Miễn phí 30 ngày • Không cần thẻ tín dụng
              </div>
              <Title level={4} className="!mb-1 font-bold text-slate-900">
                Đăng ký Dùng thử cho Doanh nghiệp
              </Title>
              <Text type="secondary" className="text-xs sm:text-sm">
                Điền thông tin doanh nghiệp bên dưới để khởi tạo tài khoản quản trị và dữ liệu mẫu thử nghiệm tức thì.
              </Text>
            </div>

            {/* Trạng thái đã đăng ký thành công */}
            {registeredData ? (
              <div className="py-6 flex flex-col items-center text-center">
                <div className="w-14 h-14 rounded-full bg-emerald-50 border border-emerald-100 flex items-center justify-center text-emerald-600 text-3xl mb-4">
                  <CheckCircleFilled />
                </div>
                <h3 className="text-xl font-bold text-slate-900 mb-1">
                  Đăng ký dùng thử thành công!
                </h3>
                <p className="text-sm text-slate-600 max-w-md mb-6">
                  Không gian làm việc cho doanh nghiệp <strong className="text-slate-900">{registeredData.companyName}</strong> đã được thiết lập sẵn sàng với đầy đủ dữ liệu mẫu demo.
                </p>

                {/* Thông tin xác nhận */}
                <div className="w-full bg-slate-50 border border-slate-200/80 rounded-xl p-4 text-left text-xs space-y-2 mb-6">
                  <div className="flex justify-between py-1 border-b border-slate-200/60">
                    <span className="text-slate-500">Doanh nghiệp:</span>
                    <span className="font-semibold text-slate-800">{registeredData.companyName}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-200/60">
                    <span className="text-slate-500">Người đại diện:</span>
                    <span className="font-medium text-slate-800">{registeredData.contactName}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-200/60">
                    <span className="text-slate-500">Email quản trị:</span>
                    <span className="font-mono text-slate-800">{registeredData.email}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-200/60">
                    <span className="text-slate-500">Số điện thoại:</span>
                    <span className="font-mono text-slate-800">{registeredData.phone}</span>
                  </div>
                  <div className="flex justify-between py-1">
                    <span className="text-slate-500">Gói dùng thử:</span>
                    <Tag color="blue" className="text-[11px] font-semibold mr-0">
                      Gói Chuyên Nghiệp (30 ngày)
                    </Tag>
                  </div>
                </div>

                <div className="flex flex-col sm:flex-row gap-3 w-full">
                  <Link to="/login" className="flex-1">
                    <Button
                      type="primary"
                      className="w-full h-11 bg-blue-600 hover:!bg-blue-500 font-semibold rounded-lg shadow-sm"
                    >
                      Đăng nhập Hệ thống ngay
                    </Button>
                  </Link>
                  <Button
                    onClick={handleResetForm}
                    className="h-11 rounded-lg font-medium border-slate-300 text-slate-700 hover:!border-blue-600 hover:!text-blue-600"
                  >
                    Đăng ký công ty khác
                  </Button>
                </div>
              </div>
            ) : (
              /* Form nhập liệu thông tin công ty */
              <Form
                form={form}
                layout="vertical"
                onFinish={handleSubmit}
                requiredMark="optional"
                initialValues={{
                  warehouseScale: '1-2',
                  primaryNeed: 'inventory-location',
                  agreement: true,
                }}
              >
                {/* Tên doanh nghiệp */}
                <Form.Item
                  label={<span className="text-xs font-semibold text-slate-700">Tên công ty / Doanh nghiệp</span>}
                  name="companyName"
                  rules={[{ required: true, message: 'Vui lòng nhập tên công ty hoặc doanh nghiệp' }]}
                >
                  <Input
                    prefix={<ApartmentOutlined className="text-slate-400 text-xs" />}
                    placeholder="VD: Công ty Cổ phần Kho vận & Logistics Á Châu"
                    className="h-10 text-sm rounded-lg"
                  />
                </Form.Item>

                {/* Họ tên người liên hệ */}
                <Form.Item
                  label={<span className="text-xs font-semibold text-slate-700">Họ và tên người đại diện</span>}
                  name="contactName"
                  rules={[{ required: true, message: 'Vui lòng nhập họ và tên người liên hệ' }]}
                >
                  <Input
                    prefix={<UserOutlined className="text-slate-400 text-xs" />}
                    placeholder="VD: Nguyễn Văn An"
                    className="h-10 text-sm rounded-lg"
                  />
                </Form.Item>

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
                      { required: true, message: 'Vui lòng nhập số điện thoại' },
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

                {/* Quy mô kho bãi & Nhu cầu chính */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                  <Form.Item
                    label={<span className="text-xs font-semibold text-slate-700">Quy mô kho bãi</span>}
                    name="warehouseScale"
                  >
                    <Select
                      className="h-10 w-full"
                      options={[
                        { label: '1 - 2 kho bãi độc lập', value: '1-2' },
                        { label: '3 - 5 kho (chuỗi chi nhánh)', value: '3-5' },
                        { label: '6 - 10 kho vận', value: '6-10' },
                        { label: 'Trên 10 kho & Hub trung chuyển', value: '10+' },
                      ]}
                    />
                  </Form.Item>

                  <Form.Item
                    label={<span className="text-xs font-semibold text-slate-700">Nhu cầu trọng tâm</span>}
                    name="primaryNeed"
                  >
                    <Select
                      className="h-10 w-full"
                      options={[
                        { label: 'Tồn kho & Sơ đồ Ô/Kệ Bin-level', value: 'inventory-location' },
                        { label: 'Quét Barcode/QR & Lô HSD (FEFO)', value: 'barcode-batch' },
                        { label: 'Đơn Mua (PO) & Đơn Bán (SO)', value: 'po-so-orders' },
                        { label: 'Điều chuyển & Kiểm kê định kỳ', value: 'transfer-stocktake' },
                        { label: 'Báo cáo KPI & Tích hợp ERP', value: 'reports-api' },
                      ]}
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
                    Tôi đồng ý với <span className="text-blue-600 hover:underline">Điều khoản dịch vụ</span> và cam kết bảo mật dữ liệu doanh nghiệp theo chuẩn Multi-Tenant.
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
                    Kích hoạt Dùng thử Miễn phí 30 ngày
                  </Button>
                </Form.Item>

                <div className="flex items-center justify-center gap-2 text-[11px] text-slate-500 pt-1">
                  <SafetyCertificateOutlined className="text-emerald-600" />
                  <span>Dữ liệu được phân lập logic an toàn tuyệt đối theo từng công ty</span>
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
                  Đội ngũ kỹ sư chuỗi cung ứng của WMS luôn sẵn sàng hỗ trợ khảo sát sơ đồ ô/kệ và thiết kế quy trình mẫu riêng biệt cho doanh nghiệp của bạn.
                </p>
                <div className="flex flex-wrap items-center gap-3 text-xs font-semibold text-blue-700">
                  <span>Hotline: 1900 8888</span>
                  <span>•</span>
                  <span>Email: support@wms-supplychain.vn</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
