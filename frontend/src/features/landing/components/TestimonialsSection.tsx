import { StarFilled } from '@ant-design/icons'
import { TESTIMONIALS } from '../pricingConfig'

export default function TestimonialsSection() {
  return (
    <section id="testimonials" className="py-20 bg-white">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-2 rounded-full bg-emerald-50 border border-emerald-200 px-3.5 py-1 text-xs font-semibold text-emerald-700 mb-3">
            Sự Hài Lòng Của Khách Hàng
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            Được tin dùng bởi hơn 500+ doanh nghiệp kho vận
          </h2>
          <p className="mt-4 text-base text-slate-600">
            Xem các nhà quản lý chuỗi cung ứng chia sẻ về sự thay đổi vượt trội sau khi áp dụng WMS Supply Chain.
          </p>
        </div>

        {/* Testimonials Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {TESTIMONIALS.map((t) => (
            <div
              key={t.id}
              className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-7 shadow-xs hover:border-blue-300 hover:shadow-lg transition-all"
            >
              <div>
                {/* Rating Stars */}
                <div className="flex items-center gap-1 text-amber-400 mb-4">
                  {[...Array(t.rating)].map((_, i) => (
                    <StarFilled key={i} className="text-sm" />
                  ))}
                </div>

                {/* Metric Badge */}
                <div className="mb-4 inline-block rounded-lg bg-blue-50 px-3 py-1.5 border border-blue-100">
                  <div className="text-lg font-extrabold text-blue-600">{t.metric}</div>
                  <div className="text-[11px] text-slate-500 font-medium">{t.metricLabel}</div>
                </div>

                {/* Quote */}
                <p className="text-sm leading-relaxed text-slate-700 italic">
                  "{t.quote}"
                </p>
              </div>

              {/* Author Info */}
              <div className="mt-6 pt-5 border-t border-slate-100 flex items-center gap-3">
                <img
                  src={t.avatar}
                  alt={t.author}
                  className="h-11 w-11 rounded-full object-cover border border-slate-200"
                />
                <div>
                  <div className="text-sm font-bold text-slate-900">{t.author}</div>
                  <div className="text-xs text-slate-500">{t.role}</div>
                  <div className="text-[11px] font-medium text-blue-600">{t.company}</div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

