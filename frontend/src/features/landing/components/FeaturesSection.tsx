import {
  AppstoreOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  ScanOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import type { ReactNode } from 'react'
import { KEY_FEATURES } from '../pricingConfig'

const featureIcons: Record<string, ReactNode> = {
  'feat-multi-warehouse': <AppstoreOutlined className="text-2xl text-blue-600" />,
  'feat-supply-chain': <SwapOutlined className="text-2xl text-indigo-600" />,
  'feat-ai-extraction': <RobotOutlined className="text-2xl text-purple-600" />,
  'feat-barcode-label': <ScanOutlined className="text-2xl text-emerald-600" />,
  'feat-inventory-control': <DatabaseOutlined className="text-2xl text-amber-600" />,
  'feat-enterprise-security': <SafetyCertificateOutlined className="text-2xl text-cyan-600" />,
}

export default function FeaturesSection() {
  return (
    <section id="features" className="py-20 bg-slate-50">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Section Heading */}
        <div className="text-center max-w-3xl mx-auto">
          <div className="inline-flex items-center gap-2 rounded-full bg-blue-100 px-3.5 py-1 text-xs font-semibold text-blue-700 mb-3">
            Hệ sinh thái Quản trị Toàn diện
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            Tất cả công cụ bạn cần để vận hành kho hiện đại
          </h2>
          <p className="mt-4 text-base text-slate-600 sm:text-lg">
            Được thiết kế chuyên sâu cho các doanh nghiệp phân phối, sản xuất và bán lẻ với quy trình tự động hóa khép kín và phân tích chuyên sâu.
          </p>
        </div>

        {/* Feature Cards Grid */}
        <div className="mt-14 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {KEY_FEATURES.map((feature) => (
            <div
              key={feature.id}
              className="group relative flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-7 shadow-xs transition-all duration-300 hover:-translate-y-1 hover:border-blue-300 hover:shadow-xl"
            >
              <div>
                <div className="flex items-center justify-between">
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-slate-50 transition-colors group-hover:bg-blue-50">
                    {featureIcons[feature.id] ?? <AppstoreOutlined className="text-2xl text-blue-600" />}
                  </div>
                  {feature.badge && (
                    <span className="rounded-full bg-blue-50 border border-blue-200 px-3 py-0.5 text-xs font-semibold text-blue-600">
                      {feature.badge}
                    </span>
                  )}
                </div>

                <div className="mt-5">
                  <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    {feature.category}
                  </span>
                  <h3 className="mt-1 text-xl font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
                    {feature.title}
                  </h3>
                  <p className="mt-3 text-sm leading-relaxed text-slate-600">
                    {feature.description}
                  </p>
                </div>
              </div>

              {/* Bullet highlights */}
              <div className="mt-6 pt-5 border-t border-slate-100">
                <ul className="space-y-2 text-xs text-slate-600">
                  {feature.highlights.map((highlight, idx) => (
                    <li key={idx} className="flex items-center gap-2">
                      <CheckCircleOutlined className="text-emerald-500 text-xs shrink-0" />
                      <span>{highlight}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

