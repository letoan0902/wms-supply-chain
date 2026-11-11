import {
  ArrowRightOutlined,
  GithubOutlined,
  TeamOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Button } from 'antd'
import { Link } from 'react-router-dom'

interface CtaFooterProps {
  onScrollTo: (sectionId: string) => void
  onRegisterTrial?: () => void
}

export default function CtaFooter({ onScrollTo, onRegisterTrial }: CtaFooterProps) {
  return (
    <footer className="bg-slate-900 text-slate-300">
      {/* Pre-footer Call to Action Banner */}
      <div className="relative border-b border-slate-800 bg-gradient-to-r from-blue-900/40 via-slate-900 to-indigo-900/40 py-16 sm:py-20">
        <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8 text-center">
          <div className="inline-flex items-center gap-2 rounded-full bg-blue-500/20 px-4 py-1.5 text-xs font-semibold text-blue-400 mb-6">
            <ThunderboltOutlined />
            Bắt đầu trong 30 giây • Hoàn toàn Miễn phí
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl lg:text-5xl">
            Sẵn sàng nâng tầm năng lực vận hành kho bãi?
          </h2>
          <p className="mt-4 text-base text-slate-300 sm:text-lg max-w-2xl mx-auto">
            Trải nghiệm trọn vẹn đặc quyền Gói Chuyên Nghiệp (Pro) trong 30 ngày. Không cần thẻ tín dụng,
            dễ dàng gia hạn linh hoạt bằng quét mã VietQR PayOS.
          </p>
          <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
            <Button
              type="primary"
              size="large"
              onClick={onRegisterTrial}
              className="h-12 w-full sm:w-auto px-8 rounded-xl font-bold bg-blue-600 hover:bg-blue-500 shadow-lg shadow-blue-600/30"
            >
              Đăng ký Dùng thử 30 ngày
              <ArrowRightOutlined className="text-xs" />
            </Button>
            <Link to="/login" className="w-full sm:w-auto">
              <Button
                size="large"
                ghost
                className="h-12 w-full sm:w-auto px-8 rounded-xl font-semibold border-slate-600 text-slate-200 hover:border-white hover:text-white"
              >
                Đăng nhập Hệ thống
              </Button>
            </Link>
          </div>
        </div>
      </div>

      {/* Main Footer Links */}
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-14">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-8">
          {/* Brand Info */}
          <div className="lg:col-span-2">
            <div className="flex items-center gap-3">
              <img
                src="/logo.png"
                alt="WMS Logo"
                className="h-10 w-10 rounded-xl bg-white p-1 object-contain"
              />
              <div>
                <div className="text-base font-extrabold text-white">WMS SUPPLY CHAIN</div>
                <div className="text-xs text-slate-400">Intelligent Logistics Platform</div>
              </div>
            </div>
            <p className="mt-4 text-xs leading-relaxed text-slate-400 max-w-sm">
              Nền tảng Quản trị Kho Vận SaaS và Chuỗi Cung ứng Đa Doanh nghiệp. Tích hợp Trí tuệ Nhân tạo bóc tách
              dữ liệu tự động và thanh toán bảo mật PayOS.
            </p>
            <div className="mt-4 space-y-1.5 text-xs text-slate-400">
              <div className="flex items-center gap-2">
                <TeamOutlined className="text-blue-400" />
                <span>Đồ án cuối môn của Nhóm 3</span>
              </div>
              <div className="flex items-center gap-2">
                <GithubOutlined className="text-blue-400" />
                <a
                  href="https://github.com/letoan0902/wms-supply-chain"
                  target="_blank"
                  rel="noreferrer"
                  className="text-slate-400 hover:text-white transition-colors"
                >
                  github.com/letoan0902/wms-supply-chain
                </a>
              </div>
            </div>
          </div>

          {/* Quick Links */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-4">
              Khám Phá
            </h4>
            <ul className="space-y-2.5 text-xs">
              <li>
                <button
                  onClick={() => onScrollTo('features')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  Tính năng cốt lõi
                </button>
              </li>
              <li>
                <button
                  onClick={() => onScrollTo('pricing')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  Bảng giá dịch vụ
                </button>
              </li>
              <li>
                <button
                  onClick={() => onScrollTo('comparison')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  So sánh chi tiết gói
                </button>
              </li>
              <li>
                <button
                  onClick={() => onScrollTo('testimonials')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  Đánh giá khách hàng
                </button>
              </li>
            </ul>
          </div>

          {/* SaaS Plans */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-4">
              Gói Dịch Vụ
            </h4>
            <ul className="space-y-2.5 text-xs">
              <li>
                <button
                  onClick={() => onScrollTo('pricing')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  Gói Khởi Đầu (Free)
                </button>
              </li>
              <li>
                <button
                  onClick={() => onScrollTo('pricing')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  Gói Chuyên Nghiệp (Pro)
                </button>
              </li>
              <li>
                <button
                  onClick={() => onScrollTo('pricing')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  Gói Doanh Nghiệp (Enterprise)
                </button>
              </li>
              <li>
                <button
                  onClick={() => onScrollTo('faq')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  Chính sách Dùng thử 30 ngày
                </button>
              </li>
            </ul>
          </div>

          {/* Portal & Support */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-4">
              Cổng Truy Cập
            </h4>
            <ul className="space-y-2.5 text-xs">
              <li>
                <Link to="/login" className="text-slate-400 hover:text-white transition-colors">
                  Đăng nhập tài khoản
                </Link>
              </li>
              <li>
                <Link to="/forgot-password" className="text-slate-400 hover:text-white transition-colors">
                  Khôi phục mật khẩu
                </Link>
              </li>
              <li>
                <button
                  onClick={() => onScrollTo('faq')}
                  className="cursor-pointer text-slate-400 hover:text-white transition-colors"
                >
                  Câu hỏi thường gặp
                </button>
              </li>
            </ul>
          </div>
        </div>

        {/* Copyright notice */}
        <div className="mt-12 pt-8 border-t border-slate-800 text-center text-xs text-slate-500">
          © 2026 WMS Supply Chain Platform. Bản quyền thuộc về hệ thống quản trị kho vận thông minh. Mọi quyền được bảo lưu.
        </div>
      </div>
    </footer>
  )
}
