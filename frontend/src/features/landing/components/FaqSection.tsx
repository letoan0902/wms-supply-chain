import {
  ArrowRightOutlined,
  CheckCircleFilled,
  CustomerServiceOutlined,
  MailOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Button, Collapse, Typography } from 'antd'
import { Link } from 'react-router-dom'
import { FAQS } from '../pricingConfig'

const { Title, Text, Paragraph } = Typography

/** Các bước đăng ký hiển thị trong khung kêu gọi dùng thử (form thật nằm ở trang /register). */
const REGISTRATION_STEPS = [
  'Điền thông tin doanh nghiệp và tài khoản quản trị đầu tiên ở trang Đăng ký doanh nghiệp.',
  'Mở email đăng ký, bấm liên kết kích hoạt (hiệu lực 24 giờ).',
  'Đăng nhập và bắt đầu nhập dữ liệu kho, 30 ngày dùng thử tính từ lúc đăng ký.',
]

export default function FaqSection() {
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
          {/* CỘT 1: Kêu gọi đăng ký dùng thử, dẫn sang trang /register */}
          <div
            id="trial-register"
            className="lg:col-span-6 bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 shadow-xs relative scroll-mt-24"
          >
            {/* Tiêu đề khung */}
            <div className="mb-6">
              <div className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 border border-emerald-200 px-3 py-0.5 text-xs font-medium text-emerald-700 mb-2.5">
                <ThunderboltOutlined className="text-emerald-600" />
                Miễn phí 30 ngày • Không cần thẻ tín dụng
              </div>
              <Title level={4} className="!mb-1 font-bold text-slate-900">
                Đăng ký Dùng thử cho Doanh nghiệp
              </Title>
              <Text type="secondary" className="text-xs sm:text-sm">
                Chỉ một form để tạo doanh nghiệp, tài khoản quản trị đầu tiên và nhận 30 ngày dùng thử.
              </Text>
            </div>

            {/* Các bước đăng ký và nút chuyển sang trang đăng ký */}
            <ol className="mb-6 space-y-3">
              {REGISTRATION_STEPS.map((step, index) => (
                <li key={step} className="flex items-start gap-3 text-sm text-slate-600">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-50 text-xs font-bold text-blue-700 border border-blue-100">
                    {index + 1}
                  </span>
                  <span className="leading-relaxed">{step}</span>
                </li>
              ))}
            </ol>

            <div className="mb-6 rounded-xl bg-slate-50 border border-slate-200/80 p-4 text-xs text-slate-600 space-y-2">
              <div className="flex items-start gap-2">
                <CheckCircleFilled className="mt-0.5 text-emerald-500" />
                <span>Doanh nghiệp, tài khoản quản trị và thuê bao dùng thử được tạo cùng lúc.</span>
              </div>
              <div className="flex items-start gap-2">
                <MailOutlined className="mt-0.5 text-blue-500" />
                <span>Không nhận được email kích hoạt? Có thể gửi lại ngay trên trang đăng ký hoặc trang đăng nhập.</span>
              </div>
            </div>

            <Link to="/register" className="block">
              <Button
                type="primary"
                icon={<ArrowRightOutlined />}
                iconPosition="end"
                className="w-full h-11 bg-blue-600 hover:!bg-blue-500 font-bold rounded-lg text-sm shadow-sm"
              >
                Đăng ký doanh nghiệp, dùng thử 30 ngày
              </Button>
            </Link>

            <div className="flex items-center justify-center gap-2 text-[11px] text-slate-500 pt-3">
              <SafetyCertificateOutlined className="text-emerald-600" />
              <span>Dữ liệu của mỗi doanh nghiệp được tách riêng, doanh nghiệp khác không xem được</span>
            </div>
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
