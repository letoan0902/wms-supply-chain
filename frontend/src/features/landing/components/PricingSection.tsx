import {
  CheckCircleFilled,
  QrcodeOutlined,
  RocketOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Button, Modal } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { type BillingCycle, PRICING_PLANS, type PricingPlan } from '../pricingConfig'

interface PricingSectionProps {
  onScrollToComparison?: () => void
}

/** Trang đăng ký kèm gói khách chọn trên bảng giá (trang đăng ký giải thích gói đó bắt đầu thế nào). */
function registerPathFor(plan: PricingPlan): string {
  if (plan.tier === 'free') return '/register?plan=free'
  if (plan.tier === 'pro') return '/register?plan=pro'
  return '/register'
}

export default function PricingSection({ onScrollToComparison }: PricingSectionProps) {
  const [billingCycle, setBillingCycle] = useState<BillingCycle>('monthly')
  const [enterpriseModalOpen, setEnterpriseModalOpen] = useState(false)

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('vi-VN').format(amount) + ' đ'
  }

  const handlePlanAction = (plan: PricingPlan) => {
    if (plan.tier === 'enterprise') {
      setEnterpriseModalOpen(true)
    }
  }

  return (
    <section id="pricing" className="py-24 bg-white relative overflow-hidden">
      {/* Decorative gradient glow */}
      <div className="pointer-events-none absolute top-1/3 left-1/2 -z-10 h-96 w-96 -translate-x-1/2 rounded-full bg-blue-100/50 blur-3xl" />

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto">
          <div className="inline-flex items-center gap-2 rounded-full bg-blue-50 border border-blue-200 px-3.5 py-1 text-xs font-semibold text-blue-700 mb-3">
            <ThunderboltOutlined className="text-blue-600" />
            Bảng Giá Minh Bạch & Tối Ưu Chi Phí
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl lg:text-5xl">
            Chọn gói dịch vụ phù hợp với quy mô của bạn
          </h2>
          <p className="mt-4 text-base text-slate-600 sm:text-lg">
            Mọi doanh nghiệp mới đăng ký đều được <strong className="text-blue-600">tặng 30 ngày dùng thử trọn vẹn Gói Chuyên Nghiệp</strong>.
            Thanh toán an toàn, linh hoạt qua VietQR PayOS.
          </p>

          {/* Billing Cycle Switcher */}
          <div className="mt-10 inline-flex items-center rounded-xl bg-slate-100 p-1.5 border border-slate-200">
            <button
              type="button"
              onClick={() => setBillingCycle('monthly')}
              className={`cursor-pointer rounded-lg px-5 py-2 text-sm font-semibold transition-all ${
                billingCycle === 'monthly'
                  ? 'bg-white text-slate-900 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Thanh toán Hàng tháng
            </button>
            <button
              type="button"
              onClick={() => setBillingCycle('yearly')}
              className={`cursor-pointer flex items-center gap-2 rounded-lg px-5 py-2 text-sm font-semibold transition-all ${
                billingCycle === 'yearly'
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <span>Thanh toán Hàng năm</span>
              <span
                className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
                  billingCycle === 'yearly'
                    ? 'bg-amber-400 text-slate-900'
                    : 'bg-emerald-100 text-emerald-700'
                }`}
              >
                Tiết kiệm 20%
              </span>
            </button>
          </div>
        </div>

        {/* Pricing Cards Grid */}
        <div className="mt-16 grid grid-cols-1 gap-8 lg:grid-cols-3 lg:items-stretch">
          {PRICING_PLANS.map((plan) => {
            const isPro = plan.isPopular
            const isContactOnly = plan.tier === 'enterprise'
            const displayPricePerMonth =
              plan.monthlyPrice === 0
                ? 0
                : billingCycle === 'yearly'
                  ? Math.round(plan.yearlyPrice / 12)
                  : plan.monthlyPrice

            return (
              <div
                key={plan.id}
                className={`relative flex flex-col justify-between rounded-3xl p-8 transition-all duration-300 ${
                  isPro
                    ? 'border-2 border-blue-600 bg-white shadow-2xl shadow-blue-500/15 lg:-translate-y-3'
                    : 'border border-slate-200 bg-white shadow-sm hover:border-slate-300 hover:shadow-lg'
                }`}
              >
                {/* Popular Badge */}
                {plan.badge && (
                  <div className="absolute -top-4 left-1/2 -translate-x-1/2">
                    <span
                      className={`inline-block rounded-full px-4 py-1 text-xs font-bold shadow-sm ${
                        isPro
                          ? 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-blue-500/30'
                          : 'bg-slate-800 text-white'
                      }`}
                    >
                      {plan.badge}
                    </span>
                  </div>
                )}

                <div>
                  {/* Plan Name & Tagline */}
                  <div className="flex items-center justify-between">
                    <h3 className="text-2xl font-bold text-slate-900">{plan.name}</h3>
                    {isPro && (
                      <span className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-600">
                        Trial 30 ngày
                      </span>
                    )}
                  </div>
                  <p className="mt-2 text-xs font-medium text-slate-500 min-h-[32px]">
                    {plan.tagline}
                  </p>

                  {/* Price Box */}
                  <div className="mt-6 border-b border-slate-100 pb-6">
                    <div className="flex items-baseline gap-1">
                      <span className="text-4xl font-extrabold tracking-tight text-slate-900 sm:text-5xl">
                        {isContactOnly
                          ? 'Liên hệ'
                          : displayPricePerMonth === 0
                            ? 'Miễn phí'
                            : formatCurrency(displayPricePerMonth)}
                      </span>
                      {!isContactOnly && displayPricePerMonth > 0 && (
                        <span className="text-sm font-semibold text-slate-500">/ tháng</span>
                      )}
                    </div>

                    <div className="mt-2 text-xs text-slate-500 min-h-[18px]">
                      {isContactOnly ? (
                        <span>Báo giá theo quy mô triển khai</span>
                      ) : plan.monthlyPrice === 0 ? (
                        <span>Miễn phí vĩnh viễn cho kho nhỏ</span>
                      ) : billingCycle === 'yearly' ? (
                        <span className="text-emerald-600 font-semibold">
                          Thanh toán {formatCurrency(plan.yearlyPrice)} / năm (Tiết kiệm 20%)
                        </span>
                      ) : (
                        <span>Thanh toán linh hoạt từng tháng</span>
                      )}
                    </div>
                  </div>

                  {/* Quota Limits Highlights */}
                  <div className="mt-6 space-y-2.5 rounded-2xl bg-slate-50 p-4 border border-slate-100 text-xs">
                    <div className="flex items-center justify-between text-slate-700">
                      <span className="font-medium text-slate-500">Tài khoản User:</span>
                      <strong className="text-slate-900 font-semibold">{plan.limits.users}</strong>
                    </div>
                    <div className="flex items-center justify-between text-slate-700">
                      <span className="font-medium text-slate-500">Quy mô kho:</span>
                      <strong className="text-slate-900 font-semibold">{plan.limits.warehouses}</strong>
                    </div>
                    <div className="flex items-center justify-between text-slate-700">
                      <span className="font-medium text-slate-500">Số lượng SKU:</span>
                      <strong className="text-slate-900 font-semibold">{plan.limits.skus}</strong>
                    </div>
                    <div className="flex items-center justify-between text-slate-700">
                      <span className="font-medium text-slate-500">Trí tuệ AI:</span>
                      <strong className="text-blue-600 font-semibold">{plan.limits.aiRequests}</strong>
                    </div>
                    <div className="flex items-center justify-between text-slate-700">
                      <span className="font-medium text-slate-500">API Key Tích hợp:</span>
                      <strong className={plan.limits.apiAccess ? 'text-emerald-600' : 'text-slate-400'}>
                        {plan.limits.apiAccess ? 'Hỗ trợ đầy đủ' : 'Không hỗ trợ'}
                      </strong>
                    </div>
                  </div>

                  {/* Highlights list */}
                  <div className="mt-6">
                    <div className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-3">
                      Đặc quyền bao gồm:
                    </div>
                    <ul className="space-y-3 text-xs text-slate-600">
                      {plan.highlightFeatures.map((feat, idx) => (
                        <li key={idx} className="flex items-start gap-2.5">
                          <CheckCircleFilled
                            className={`mt-0.5 text-sm shrink-0 ${
                              isPro ? 'text-blue-600' : 'text-emerald-500'
                            }`}
                          />
                          <span>{feat}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>

                {/* Card CTA Button */}
                <div className="mt-8 pt-4">
                  {plan.tier === 'enterprise' ? (
                    <Button
                      type="default"
                      size="large"
                      block
                      onClick={() => handlePlanAction(plan)}
                      className="h-12 rounded-xl font-bold border-slate-300 text-slate-800 hover:border-blue-600 hover:text-blue-600"
                    >
                      {plan.ctaText}
                    </Button>
                  ) : (
                    <Link to={registerPathFor(plan)} className="block w-full">
                      <Button
                        type={isPro ? 'primary' : 'default'}
                        size="large"
                        block
                        className={`h-12 rounded-xl font-bold ${
                          isPro
                            ? 'bg-blue-600 hover:bg-blue-700 shadow-md shadow-blue-500/25'
                            : 'border-slate-300 text-slate-800 hover:border-blue-600 hover:text-blue-600'
                        }`}
                      >
                        {plan.ctaText}
                      </Button>
                    </Link>
                  )}
                  {plan.ctaSubtext && (
                    <div className="mt-2 text-center text-[11px] text-slate-400">
                      {plan.ctaSubtext}
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {/* Comparison Table Link & Trust Banner */}
        <div className="mt-16 rounded-2xl border border-slate-200 bg-slate-50/80 p-6 sm:p-8 flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-4 text-left">
            <div className="rounded-xl bg-blue-100 p-3 text-blue-600 shrink-0">
              <QrcodeOutlined className="text-2xl" />
            </div>
            <div>
              <div className="text-sm font-bold text-slate-900">
                Thanh toán chuyển khoản tự động qua VietQR PayOS
              </div>
              <div className="text-xs text-slate-500 mt-0.5">
                Kích hoạt ngay lập tức sau khi quét mã QR. Dữ liệu được bảo toàn vĩnh viễn ngay cả khi hết hạn dùng thử.
              </div>
            </div>
          </div>

          {onScrollToComparison && (
            <Button
              type="link"
              onClick={onScrollToComparison}
              className="text-sm font-semibold text-blue-600 hover:text-blue-700 shrink-0"
            >
              Xem bảng so sánh chi tiết tính năng →
            </Button>
          )}
        </div>
      </div>

      {/* Enterprise Contact Modal */}
      <Modal
        title={
          <div className="flex items-center gap-2">
            <RocketOutlined className="text-blue-600 text-xl" />
            <span>Tư vấn Gói Doanh Nghiệp (Enterprise)</span>
          </div>
        }
        open={enterpriseModalOpen}
        onCancel={() => setEnterpriseModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setEnterpriseModalOpen(false)}>
            Đóng
          </Button>,
          <Link key="register" to="/register">
            <Button type="primary" className="bg-blue-600">
              Đăng ký tài khoản trước
            </Button>
          </Link>,
        ]}
      >
        <div className="py-4 space-y-3 text-slate-600 text-sm">
          <p>
            Gói Doanh Nghiệp dành cho đơn vị cần không giới hạn tài nguyên và API Key để tích hợp với phần mềm khác.
          </p>
          <div className="rounded-xl bg-blue-50 p-4 border border-blue-100 text-xs text-slate-700 space-y-1.5">
            <div>
              Trong phạm vi đồ án, gói này chưa mở bán trực tuyến. Bạn có thể đăng ký tài khoản để dùng thử đầy đủ
              tính năng của Gói Chuyên Nghiệp trong 30 ngày.
            </div>
          </div>
        </div>
      </Modal>
    </section>
  )
}
