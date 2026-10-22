import { LogoutOutlined, UserOutlined } from '@ant-design/icons'
import { Avatar, Dropdown, Layout, Menu, Spin, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { Suspense, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { clearAuth, getStoredUser } from '../utils/storage'
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

  // Đường dẫn gốc của mục menu đang chọn: '/' giữ nguyên, '/products/12' thành '/products'
  const selectedKey = `/${pathname.split('/')[1]}`
  const currentTitle = navigationItems.find((item) => item.path === selectedKey)?.label

  const handleLogout = () => {
    clearAuth()
    navigate('/login', { replace: true })
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
        width={256}
        style={
          isNarrowScreen
            ? { position: 'fixed', top: 0, left: 0, zIndex: 100, height: '100vh' }
            : { position: 'sticky', top: 0, height: '100vh' }
        }
      >
        <div className="flex h-16 items-center justify-center px-4 text-center text-lg font-semibold text-white">
          {collapsed ? 'WMS' : 'WMS Supply Chain'}
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
          className="flex items-center justify-between"
          style={{ background: '#fff', padding: '0 24px' }}
        >
          <Typography.Title level={4} style={{ margin: 0 }}>
            {currentTitle ?? 'WMS Supply Chain'}
          </Typography.Title>
          <Dropdown menu={{ items: userMenu }} placement="bottomRight">
            <button
              type="button"
              className="flex cursor-pointer items-center gap-2 border-0 bg-transparent"
            >
              <Avatar size="small" icon={<UserOutlined />} />
              <span>{user?.fullName ?? user?.username ?? 'Người dùng'}</span>
            </button>
          </Dropdown>
        </Header>

        <Content className="p-4 md:p-6">
          <Suspense
            fallback={
              <div className="flex justify-center py-20">
                <Spin size="large" />
              </div>
            }
          >
            <Outlet />
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  )
}
