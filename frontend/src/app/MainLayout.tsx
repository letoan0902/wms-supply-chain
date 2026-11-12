import {
  DownOutlined,
  EditOutlined,
  IdcardOutlined,
  KeyOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Button,
  Dropdown,
  Layout,
  Menu,
  Modal,
} from 'antd'
import type { MenuProps } from 'antd'
import { Suspense, useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import PageLoading from '../components/PageLoading'
import { clearAuth, getStoredUser } from '../utils/storage'
import { logout } from '../features/auth/authApi'
import NotificationBell from '../features/realtime/components/NotificationBell'
import { useWebSocket } from '../features/realtime/useWebSocket'
import { navigationItems } from './navigation'
import { ROLE_CONFIG } from '../features/users/types'
import SubscriptionLayer from '../features/subscription/components/SubscriptionLayer'
import { READ_ONLY_ALLOWED_CLASS } from '../features/subscription/readOnlyGuard'
import { useSubscriptionStore } from '../features/subscription/subscriptionStore'
import AiChatWidget from '../features/ai/components/AiChatWidget'
import { useCan } from '../features/auth/useCan'

const { Header, Sider, Content } = Layout

export default function MainLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  // Màn hình hẹp (dưới breakpoint lg): sidebar thu gọn hoàn toàn và khi mở sẽ phủ lên nội dung
  const [isNarrowScreen, setIsNarrowScreen] = useState(false)
  const [user, setUser] = useState(() => getStoredUser())
  const [logoutModalOpen, setLogoutModalOpen] = useState(false)
  const [logoutLoading, setLogoutLoading] = useState(false)

  const can = useCan()

  // Theo đặc tả phân quyền SRS 2.5: Giữ nguyên tất cả các mục trên thanh Sidebar
  // để người dùng nắm rõ cấu trúc tính năng. Nếu không có quyền, màn hình đích sẽ hiển thị thông báo 403.
  // Riêng mục có `anyOf` (Nhập dữ liệu bằng AI) chỉ hiện với vai trò được phép theo đặc tả của chức năng đó.
  const menuItems: MenuProps['items'] = navigationItems
    .filter((item) => !item.anyOf || item.anyOf.some((capability) => can(capability)))
    .map((item) => ({
      key: item.path,
      icon: item.icon,
      label: item.label,
    }))

  // Lắng nghe sự kiện khi người dùng cập nhật hồ sơ cá nhân để sync ngay lập tức lên Header
  useEffect(() => {
    const handleUserUpdate = () => {
      setUser(getStoredUser())
    }
    window.addEventListener('wms:user-updated', handleUserUpdate)
    return () => {
      window.removeEventListener('wms:user-updated', handleUserUpdate)
    }
  }, [])

  // Nối WebSocket nhận thông báo thời gian thực (MainLayout chỉ hiển thị sau khi đăng nhập)
  useWebSocket()

  // Mục menu đang chọn: mục có đường dẫn dài nhất khớp với đầu đường dẫn hiện tại
  // ('/products/12' thành '/products', '/settings/api-keys' giữ nguyên); không khớp mục nào thì lấy đoạn đầu.
  const selectedKey =
    navigationItems
      .filter((item) => item.path !== '/' && (pathname === item.path || pathname.startsWith(`${item.path}/`)))
      .sort((a, b) => b.path.length - a.path.length)[0]?.path ?? `/${pathname.split('/')[1]}`
  const currentTitle = navigationItems.find((item) => item.path === selectedKey)?.label

  // Xử lý đăng xuất thực tế
  const handleLogout = async () => {
    setLogoutLoading(true)
    try {
      await logout()
    } catch {
      // Bỏ qua lỗi nếu token đã hết hạn hoặc không kết nối được máy chủ
    } finally {
      setLogoutLoading(false)
      setLogoutModalOpen(false)
      clearAuth()
      useSubscriptionStore.getState().reset()
      navigate('/login', { replace: true })
    }
  }

  const displayName = user?.fullName || user?.username || 'Người dùng'
  const initial = displayName.charAt(0).toUpperCase()

  // Dropdown menu options cho tài khoản người dùng
  const userMenu: MenuProps['items'] = [
    {
      key: 'user-header',
      type: 'group',
      label: (
        <div className="py-1 px-1">
          <div className="font-semibold text-slate-900 text-sm">{displayName}</div>
          <div className="text-xs text-slate-500 font-normal">@{user?.username}</div>
          {user?.roles && user.roles.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-2">
              {user.roles.map((r) => {
                const cfg = ROLE_CONFIG[r] || { label: r, color: 'default' }
                return (
                  <span
                    key={r}
                    className="inline-flex items-center text-[11px] font-medium px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200/80"
                  >
                    {cfg.label}
                  </span>
                )
              })}
            </div>
          )}
        </div>
      ),
    },
    { type: 'divider' },
    {
      key: 'profile-info',
      icon: <IdcardOutlined className="text-slate-500" />,
      label: 'Thông tin tài khoản',
      onClick: () => navigate('/profile?tab=info'),
    },
    {
      key: 'profile-edit',
      icon: <EditOutlined className="text-slate-500" />,
      label: 'Cập nhật thông tin',
      onClick: () => navigate('/profile?tab=edit'),
    },
    {
      key: 'profile-password',
      icon: <KeyOutlined className="text-slate-500" />,
      label: 'Đổi mật khẩu',
      onClick: () => navigate('/profile?tab=password'),
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined className="text-rose-600" />,
      label: <span className="text-rose-600 font-medium">Đăng xuất</span>,
      className: 'hover:!bg-rose-50 hover:!text-rose-600 transition-colors',
      onClick: () => setLogoutModalOpen(true),
    },
  ]

  return (
    <Layout className="min-h-screen">
      <Sider
        collapsible
        trigger={null}
        breakpoint="lg"
        collapsedWidth={isNarrowScreen ? 0 : 72}
        onBreakpoint={setIsNarrowScreen}
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={256}
        className="border-r border-slate-800/80"
        style={
          isNarrowScreen
            ? { position: 'fixed', top: 0, left: 0, zIndex: 100, height: '100vh' }
            : { position: 'sticky', top: 0, height: '100vh' }
        }
      >
        <div
          className={`flex h-14 items-center px-4 transition-[padding,gap] duration-150 border-b border-slate-800/80 ${
            collapsed ? 'justify-center px-0' : 'gap-3'
          }`}
        >
          <div className="h-8 w-8 shrink-0 rounded-md bg-slate-800/90 border border-slate-700/60 p-1 flex items-center justify-center">
            <img
              src="/logo.png"
              alt="WMS Logo"
              className="h-full w-full object-contain"
            />
          </div>
          {!collapsed && (
            <div className="flex flex-col overflow-hidden whitespace-nowrap min-w-0">
              <span className="text-xs font-semibold text-slate-100 tracking-tight leading-tight">
                WMS Supply Chain
              </span>
              <span className="text-[11px] text-slate-400 font-normal leading-tight mt-0.5">
                Hệ thống Quản lý Kho
              </span>
            </div>
          )}
        </div>
        {/* Menu cuộn toàn chiều cao Sidebar sau khi bỏ trigger đáy */}
        <div className="overflow-y-auto" style={{ height: 'calc(100vh - 56px)' }}>
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

      <Layout className="bg-slate-50 min-h-screen">
        <Header
          className="sticky top-0 z-10 flex h-14 items-center justify-between border-b border-slate-200/80 bg-white"
          style={{ padding: '0 20px', height: 56, lineHeight: '56px' }}
        >
          <div className="flex items-center gap-3 min-w-0">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              className="h-8 w-8 text-slate-500 hover:text-slate-800 hover:bg-slate-100 flex items-center justify-center rounded-md"
              aria-label={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'}
            />
            <h1 className="text-base font-semibold text-slate-800 m-0 truncate">
              {currentTitle ?? 'WMS Supply Chain'}
            </h1>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            {user?.company?.name && (
              <span className="hidden max-w-64 truncate text-sm font-medium text-slate-600 md:inline" title={user.company.name}>
                {user.company.name}
              </span>
            )}
            <NotificationBell />
            {/* User profile trigger */}
            <Dropdown
              menu={{ items: userMenu }}
              placement="bottomRight"
              trigger={['click']}
              rootClassName={READ_ONLY_ALLOWED_CLASS}
            >
              <button
                type="button"
                className="flex cursor-pointer items-center gap-2.5 rounded-md border border-transparent px-2 py-1 text-left select-none transition-colors hover:bg-slate-50 hover:border-slate-200/60"
              >
                <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-semibold text-slate-700 border border-slate-200/80">
                  {initial || <UserOutlined className="text-slate-400" />}
                </div>
                <div className="hidden sm:flex flex-col">
                  <span className="text-xs font-medium text-slate-800 leading-tight">
                    {displayName}
                  </span>
                  <span className="text-[11px] text-slate-500 font-normal leading-tight">
                    @{user?.username}
                  </span>
                </div>
                <DownOutlined className="text-[9px] text-slate-400 ml-0.5" />
              </button>
            </Dropdown>
          </div>
        </Header>

        {/* Dải nhắc dùng thử, dải hết hạn, chế độ chỉ đọc và hộp thoại gói dịch vụ dùng chung */}
        <SubscriptionLayer />

        <Content className="p-4 sm:p-6">
          <Suspense fallback={<PageLoading tip="Đang tải dữ liệu phân hệ..." />}>
            <Outlet />
          </Suspense>
        </Content>
      </Layout>

      {/* Nút và khung chat trợ lý AI nổi ở góc phải dưới của mọi trang */}
      <AiChatWidget />

      {/* Modal Xác nhận Đăng xuất */}
      <Modal
        title={
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-full bg-slate-100 text-slate-700 flex items-center justify-center shrink-0 border border-slate-200/80">
              <LogoutOutlined className="text-sm" />
            </div>
            <div>
              <div className="text-sm font-semibold text-slate-900 leading-tight">
                Xác nhận đăng xuất
              </div>
              <div className="text-xs text-slate-500 font-normal mt-0.5">
                Kết thúc phiên làm việc trên hệ thống
              </div>
            </div>
          </div>
        }
        open={logoutModalOpen}
        onOk={handleLogout}
        onCancel={() => setLogoutModalOpen(false)}
        confirmLoading={logoutLoading}
        okText="Đăng xuất"
        okButtonProps={{ danger: true }}
        cancelText="Hủy bỏ"
        width={420}
        destroyOnClose
      >
        <div className="py-2 text-sm text-slate-600 leading-relaxed">
          Bạn có chắc chắn muốn đăng xuất khỏi tài khoản{' '}
          <strong className="text-slate-900 font-medium">@{user?.username}</strong>? Mọi tác vụ hoặc thay đổi chưa lưu có thể bị gián đoạn.
        </div>
      </Modal>
    </Layout>
  )
}
