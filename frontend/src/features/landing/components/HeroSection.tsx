import {
  AppstoreOutlined,
  ArrowRightOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Button } from 'antd'


interface HeroSectionProps {
  onExplorePricing: () => void
  onExploreFeatures: () => void
  onRegisterTrial?: () => void
}

export default function HeroSection({ onExplorePricing, onExploreFeatures, onRegisterTrial }: HeroSectionProps) {
  return (
    <section className="relative overflow-hidden bg-gradient-to-b from-blue-50/60 via-white to-slate-50 pt-12 pb-20 lg:pt-20 lg:pb-28">
      {/* Background Decorative Blobs */}
      <div className="pointer-events-none absolute -top-40 right-0 -z-10 h-96 w-96 rounded-full bg-blue-200/40 blur-3xl" />
      <div className="pointer-events-none absolute top-1/2 left-0 -z-10 h-80 w-80 -translate-y-1/2 rounded-full bg-indigo-100/50 blur-3xl" />

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 items-center gap-12 lg:grid-cols-12 lg:gap-8">
          {/* CỘT TRÁI: Tiêu đề, Định vị giá trị & Nút kêu gọi hành động */}
          <div className="lg:col-span-7 text-center lg:text-left">
            {/* Pill Badge */}
            <div className="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-blue-50/80 px-4 py-1.5 text-xs font-semibold text-blue-700 shadow-xs mb-6">
              <span className="flex h-2 w-2 rounded-full bg-blue-600 animate-pulse" />
              <span>Nền tảng WMS SaaS Đa Doanh nghiệp • Tích hợp AI</span>
            </div>

            {/* Main Headline */}
            <h1 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-5xl lg:text-5xl xl:text-6xl leading-tight">
              Tối ưu Vận hành Kho Vận <br className="hidden sm:inline" />
              <span className="bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent">
                Bứt phá Chuỗi Cung ứng
              </span>
            </h1>

            {/* Subtitle */}
            <p className="mt-6 text-base text-slate-600 sm:text-lg leading-relaxed max-w-2xl mx-auto lg:mx-0">
              Giải pháp quản lý kho chuyên nghiệp trên nền tảng đám mây: Quản lý đa kho trực quan theo ô kệ
              (Zone-Rack-Bin), tự động hóa luồng Mua - Bán, in tem nhãn mã vạch và bóc tách dữ liệu siêu tốc bằng Trí tuệ Nhân tạo (AI).
            </p>

            {/* Action Buttons */}
            <div className="mt-8 flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-4">
              <div className="w-full sm:w-auto">
                <Button
                  type="primary"
                  size="large"
                  icon={<ThunderboltOutlined />}
                  onClick={onRegisterTrial}
                  className="h-12 w-full sm:w-auto px-7 rounded-xl text-base font-semibold shadow-md shadow-blue-500/20 hover:scale-105 transition-transform"
                >
                  Dùng thử 30 ngày Miễn phí
                </Button>
              </div>
              <Button
                size="large"
                onClick={onExplorePricing}
                icon={<ArrowRightOutlined />}
                className="h-12 w-full sm:w-auto px-6 rounded-xl text-base font-medium text-slate-700 hover:text-blue-600 border-slate-300 hover:border-blue-400"
              >
                Xem Bảng giá & Gói dịch vụ
              </Button>
            </div>

            {/* Trust Indicators */}
            <div className="mt-8 pt-6 border-t border-slate-200/80 grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs text-slate-600 text-left">
              <div className="flex items-center gap-2">
                <CheckCircleOutlined className="text-emerald-500 text-sm" />
                <span>Không cần thẻ tín dụng</span>
              </div>
              <div className="flex items-center gap-2">
                <SafetyCertificateOutlined className="text-blue-500 text-sm" />
                <span>Dữ liệu cách ly an toàn 100%</span>
              </div>
              <div className="flex items-center gap-2">
                <ThunderboltOutlined className="text-amber-500 text-sm" />
                <span>Kích hoạt chỉ trong 30 giây</span>
              </div>
            </div>
          </div>

          {/* CỘT PHẢI: Mockup Minh họa Dashboard Trực quan & Thẻ AI */}
          <div className="lg:col-span-5 relative">
            <div className="relative mx-auto w-full max-w-lg lg:max-w-none">
              {/* Main Visual Card */}
              <div className="rounded-2xl border border-slate-200/90 bg-white p-5 shadow-2xl shadow-slate-200/70 backdrop-blur-sm">
                {/* Mock Header */}
                <div className="flex items-center justify-between border-b border-slate-100 pb-4">
                  <div className="flex items-center gap-2.5">
                    <div className="flex gap-1.5">
                      <div className="h-3 w-3 rounded-full bg-red-400" />
                      <div className="h-3 w-3 rounded-full bg-amber-400" />
                      <div className="h-3 w-3 rounded-full bg-emerald-400" />
                    </div>
                    <span className="text-xs font-semibold text-slate-600">Trung tâm Điều hành Kho Vận</span>
                  </div>
                  <span className="rounded-md bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-600">
                    Thời gian thực
                  </span>
                </div>

                {/* Mock Metrics Grid */}
                <div className="mt-4 grid grid-cols-2 gap-3">
                  <div className="rounded-xl bg-blue-50/70 p-3.5 border border-blue-100">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-slate-600">Tổng giá trị tồn kho</span>
                      <DatabaseOutlined className="text-blue-600" />
                    </div>
                    <div className="mt-2 text-xl font-bold text-slate-900">4.850.000.000 đ</div>
                    <div className="mt-1 text-[11px] text-emerald-600 font-medium">↑ +12.4% so với tháng trước</div>
                  </div>

                  <div className="rounded-xl bg-indigo-50/70 p-3.5 border border-indigo-100">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-slate-600">Tỷ lệ lấp đầy kho</span>
                      <AppstoreOutlined className="text-indigo-600" />
                    </div>
                    <div className="mt-2 text-xl font-bold text-slate-900">82.5%</div>
                    <div className="mt-1 text-[11px] text-slate-500">18/20 kho đang hoạt động</div>
                  </div>
                </div>

                {/* Mock Warehouse Rack Preview */}
                <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50/60 p-3.5">
                  <div className="flex items-center justify-between text-xs font-semibold text-slate-700 mb-2">
                    <span>Sơ đồ vị trí Khu A (Zone A - Racks)</span>
                    <span className="text-blue-600 cursor-pointer" onClick={onExploreFeatures}>
                      Chi tiết →
                    </span>
                  </div>
                  <div className="grid grid-cols-4 gap-2 text-center text-[11px]">
                    <div className="rounded-lg bg-emerald-100 border border-emerald-300 p-2 font-medium text-emerald-800">
                      Rack A1 (Trống)
                    </div>
                    <div className="rounded-lg bg-blue-100 border border-blue-300 p-2 font-medium text-blue-800">
                      Rack A2 (75%)
                    </div>
                    <div className="rounded-lg bg-amber-100 border border-amber-300 p-2 font-medium text-amber-800">
                      Rack A3 (90%)
                    </div>
                    <div className="rounded-lg bg-rose-100 border border-rose-300 p-2 font-medium text-rose-800">
                      Rack A4 (Đầy)
                    </div>
                  </div>
                </div>

                {/* AI Assistant Floating Widget Mock */}
                <div className="mt-4 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 p-3 text-white shadow-md">
                  <div className="flex items-center gap-2">
                    <div className="rounded-lg bg-white/20 p-1.5">
                      <RobotOutlined className="text-base text-white" />
                    </div>
                    <div className="flex-1">
                      <div className="text-xs font-bold">Trợ lý AI Trích xuất Dữ liệu</div>
                      <div className="text-[11px] text-blue-100">
                        Đã tự động trích xuất 50 SKU từ file Excel trong 12 giây
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Floating Badge */}
              <div className="absolute -bottom-6 -left-6 rounded-xl border border-slate-200 bg-white p-3.5 shadow-xl hidden sm:flex items-center gap-3">
                <div className="rounded-lg bg-emerald-100 p-2 text-emerald-600">
                  <SafetyCertificateOutlined className="text-xl" />
                </div>
                <div>
                  <div className="text-xs font-bold text-slate-900">Chuẩn Doanh nghiệp SaaS</div>
                  <div className="text-[11px] text-slate-500">Mã hóa đa tầng & Cam kết 99.9% Uptime</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

