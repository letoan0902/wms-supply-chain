import { QuestionCircleOutlined } from '@ant-design/icons'
import { Collapse } from 'antd'
import { FAQS } from '../pricingConfig'

export default function FaqSection() {
  const items = FAQS.map((faq) => ({
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
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center mb-12">
          <div className="inline-flex items-center gap-2 rounded-full bg-blue-100 px-3.5 py-1 text-xs font-semibold text-blue-700 mb-3">
            <QuestionCircleOutlined />
            Giải Đáp Thắc Mắc
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            Câu hỏi thường gặp về gói dịch vụ
          </h2>
          <p className="mt-3 text-base text-slate-600">
            Nếu bạn có thêm câu hỏi chưa được giải đáp, hãy liên hệ ngay với đội ngũ hỗ trợ kỹ thuật của chúng tôi.
          </p>
        </div>

        {/* Accordion List */}
        <div className="rounded-2xl bg-white p-4 sm:p-6 border border-slate-200 shadow-xs">
          <Collapse
            items={items}
            defaultActiveKey={['faq-trial']}
            bordered={false}
            className="bg-transparent [&_.ant-collapse-item]:border-b [&_.ant-collapse-item]:border-slate-100 [&_.ant-collapse-item:last-child]:border-0"
          />
        </div>
      </div>
    </section>
  )
}

