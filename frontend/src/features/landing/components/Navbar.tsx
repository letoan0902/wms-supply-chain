import { ArrowRightOutlined, CloseOutlined, MenuOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router-dom'

interface NavbarProps {
  onScrollTo: (sectionId: string) => void
}

export default function Navbar({ onScrollTo }: NavbarProps) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const navLinks = [
    { label: 'Tính năng', target: 'features' },
    { label: 'Bảng giá', target: 'pricing' },
    { label: 'So sánh gói', target: 'comparison' },
    { label: 'Khách hàng', target: 'testimonials' },
    { label: 'Hỏi đáp', target: 'faq' },
  ]

  const handleNavClick = (target: string) => {
    onScrollTo(target)
    setMobileMenuOpen(false)
  }

  return (
    <header className="sticky top-0 z-50 w-full border-b border-slate-200/80 bg-white/90 backdrop-blur-md transition-all">
      <div className="mx-auto flex h-18 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Brand Logo */}
        <Link to="/landing" className="flex items-center gap-3 group">
          <img
            src="/logo.png"
            alt="WMS Supply Chain"
            className="h-10 w-10 rounded-xl bg-blue-600/10 p-1 object-contain transition-transform group-hover:scale-105"
          />
          <div>
            <div className="text-base font-extrabold tracking-tight text-slate-900 group-hover:text-blue-600 transition-colors">
              WMS SUPPLY CHAIN
            </div>
            <div className="text-[11px] font-medium text-slate-500">
              Nền tảng Quản trị Kho Vận Đa Doanh nghiệp
            </div>
          </div>
        </Link>

        {/* Desktop Navigation Links */}
        <nav className="hidden md:flex items-center gap-8">
          {navLinks.map((link) => (
            <button
              key={link.target}
              onClick={() => handleNavClick(link.target)}
              className="cursor-pointer text-sm font-medium text-slate-600 transition-colors hover:text-blue-600"
            >
              {link.label}
            </button>
          ))}
        </nav>

        {/* Desktop Action Buttons */}
        <div className="hidden md:flex items-center gap-3">
          <Link to="/login">
            <Button
              type="text"
              className="text-sm font-semibold text-slate-700 hover:text-blue-600"
            >
              Đăng nhập
            </Button>
          </Link>
          <Link
            to="/register"
            className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold !text-white shadow-sm transition-all hover:bg-blue-700 hover:shadow"
          >
            Dùng thử 30 ngày
            <ArrowRightOutlined className="text-xs" />
          </Link>
        </div>

        {/* Mobile Hamburger Button */}
        <div className="flex md:hidden">
          <button
            type="button"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 focus:outline-none"
            aria-label="Toggle navigation menu"
          >
            {mobileMenuOpen ? <CloseOutlined className="text-xl" /> : <MenuOutlined className="text-xl" />}
          </button>
        </div>
      </div>

      {/* Mobile Drawer Navigation Menu */}
      {mobileMenuOpen && (
        <div className="border-b border-slate-200 bg-white px-4 pt-2 pb-6 md:hidden shadow-lg">
          <div className="flex flex-col space-y-3">
            {navLinks.map((link) => (
              <button
                key={link.target}
                onClick={() => handleNavClick(link.target)}
                className="text-left py-2 text-base font-medium text-slate-700 hover:text-blue-600"
              >
                {link.label}
              </button>
            ))}
            <div className="pt-4 border-t border-slate-100 flex flex-col gap-2.5">
              <Link to="/login" className="w-full">
                <Button block size="large" className="font-medium">
                  Đăng nhập
                </Button>
              </Link>
              <Link
                to="/register"
                className="w-full rounded-lg bg-blue-600 py-2.5 text-center text-sm font-semibold !text-white shadow hover:bg-blue-700"
              >
                Dùng thử 30 ngày miễn phí
              </Link>
            </div>
          </div>
        </div>
      )}
    </header>
  )
}

