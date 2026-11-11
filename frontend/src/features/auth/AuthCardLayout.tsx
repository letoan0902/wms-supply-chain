import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface AuthCardLayoutProps {
  children: ReactNode
}

/** Khung chung cho các trang xác thực nhỏ (kích hoạt doanh nghiệp, đặt lại mật khẩu): một thẻ ở giữa màn hình. */
export default function AuthCardLayout({ children }: AuthCardLayoutProps) {
  return (
    <div className="flex min-h-screen w-full flex-col items-center justify-between bg-gradient-to-br from-slate-50 via-blue-50/25 to-slate-100/70 px-4 py-8">
      <Link to="/landing" className="flex items-center gap-2.5">
        <img
          src="/logo.png"
          alt="WMS Logo"
          className="h-9 w-9 rounded-lg bg-white object-contain p-1 shadow-xs"
        />
        <span className="text-base font-bold text-slate-800">WMS Supply Chain</span>
      </Link>

      <div className="my-8 w-full max-w-md rounded-xl border border-white/60 bg-white/90 p-6 shadow-sm backdrop-blur-sm sm:p-8">
        {children}
      </div>

      <div className="text-center text-xs text-slate-400">
        © 2026 WMS Supply Chain System. All rights reserved.
      </div>
    </div>
  )
}
