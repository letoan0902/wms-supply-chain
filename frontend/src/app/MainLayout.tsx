import { LogoutOutlined, UserOutlined } from '@ant-design/icons'
import { Avatar, Dropdown, Layout, Menu, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { Suspense, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import PageLoading from '../components/PageLoading'
import { clearAuth, getStoredUser } from '../utils/storage'
import { logout } from '../features/auth/authApi'
import NotificationBell from '../features/realtime/components/NotificationBell'
import { useWebSocket } from '../features/realtime/useWebSocket'
import { navigationItems } from './navigation'

const { Header, Sider, Content } = Layout

const menuItems: MenuProps['items'] = navigationItems.map((item) => ({
  key: item.path,
  icon: item.icon,
  label: item.label,
}))

export default function MainLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  // Màn hình hẹp (dưới breakpoint lg): sidebar thu gọn hoàn toàn và khi mở sẽ phủ lên nội dung
  const [isNarrowScreen, setIsNarrowScreen] = useState(false)
  const user = getStoredUser()
  // Nối WebSocket nhận thông báo thời gian thực (MainLayout chỉ hiển thị sau khi đăng nhập)
  useWebSocket()

  // Đường dẫn gốc của mục menu đang chọn: '/' giữ nguyên, '/products/12' thành '/products'
  const selectedKey = `/${pathname.split('/')[1]}`
  const currentTitle = navigationItems.find((item) => item.path === selectedKey)?.label

  const handleLogout = async () => {
    try {
      await logout()
    } catch {
      // Bỏ qua lỗi nếu token đã hết hạn hoặc không kết nối được máy chủ
    } finally {
      clearAuth()
      navigate('/login', { replace: true })
    }
  }

  const userMenu: MenuProps['items'] = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Đăng xuất',
      onClick: handleLogout,
    },
  ]

  return (
    <Layout className="min-h-screen">
      <Sider
        collapsible
        breakpoint="lg"
        collapsedWidth={isNarrowScreen ? 0 : 80}
        onBreakpoint={setIsNarrowScreen}
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={300}
        style={
          isNarrowScreen
            ? { position: 'fixed', top: 0, left: 0, zIndex: 100, height: '100vh' }
            : { position: 'sticky', top: 0, height: '100vh' }
        }
      >
        <div
          className={`flex h-16 items-center px-4 transition-all duration-200 ${collapsed ? 'justify-center' : 'gap-3'
            }`}
        >
          <img
            src="/logo.png"
            alt="WMS Logo"
            className="h-12 w-12 shrink-0 rounded-lg bg-white object-contain p-1 shadow-sm"
          />
          {!collapsed && (
            <div className="flex flex-col overflow-hidden whitespace-nowrap">
              <span className="text-sm font-bold tracking-wide text-white">WMS SUPPLY CHAIN</span>
              <span className="text-[10px] text-slate-400">Warehouse Management</span>
            </div>
          )}
        </div>
        {/* 64px logo + 48px nút thu gọn; menu cuộn riêng khi màn hình thấp */}
        <div className="overflow-y-auto" style={{ height: 'calc(100vh - 112px)' }}>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => {
              navigate(key)
              if (isNarrowScreen) setCollapsed(true)
            }}
          />
        </div>
      </Sider>

      <Layout>
        <Header
          className="flex items-center justify-between gap-3"
          style={{ background: '#fff', padding: '0 24px' }}
        >
          <Typography.Title level={4} ellipsis className="min-w-0" style={{ margin: 0 }}>
            {currentTitle ?? 'WMS Supply Chain'}
          </Typography.Title>
          <div className="flex shrink-0 items-center gap-2 sm:gap-4">
            <NotificationBell />
            <Dropdown menu={{ items: userMenu }} placement="bottomRight">
              <button
                type="button"
                className="flex cursor-pointer items-center gap-2 border-0 bg-transparent"
              >
                <Avatar size="small" icon={<UserOutlined />} />
                <span className="hidden sm:inline">
                  {user?.fullName ?? user?.username ?? 'Người dùng'}
                </span>
              </button>
            </Dropdown>
          </div>
        </Header>

        <Content className="p-4 md:p-6">
          <Suspense fallback={<PageLoading tip="Đang tải dữ liệu phân hệ..." />}>
            <Outlet />
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  )
}
