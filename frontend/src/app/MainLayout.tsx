import {
  DownOutlined,
  EditOutlined,
  IdcardOutlined,
  KeyOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Dropdown,
  Layout,
  Menu,
  Modal,
  Tag,
  Typography,
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
          <div className="font-semibold text-gray-900 text-sm">{displayName}</div>
          <div className="text-xs text-gray-500 font-mono">{user?.username}</div>
          {user?.roles && user.roles.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-1.5">
              {user.roles.map((r) => {
                const cfg = ROLE_CONFIG[r] || { label: r, color: 'default' }
                return (
                  <Tag key={r} color={cfg.color} className="text-[11px] py-0 px-1.5 font-normal">
                    {cfg.label}
                  </Tag>
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
      icon: <IdcardOutlined className="text-gray-500" />,
      label: 'Thông tin tài khoản',
      onClick: () => navigate('/profile?tab=info'),
    },
    {
      key: 'profile-edit',
      icon: <EditOutlined className="text-gray-500" />,
      label: 'Cập nhật thông tin',
      onClick: () => navigate('/profile?tab=edit'),
    },
    {
      key: 'profile-password',
      icon: <KeyOutlined className="text-gray-500" />,
      label: 'Đổi mật khẩu',
      onClick: () => navigate('/profile?tab=password'),
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined className="text-red-500" />,
      label: <span className="text-red-600 font-medium">Đăng xuất</span>,
      className: 'hover:!bg-red-50 hover:!text-red-600 transition-colors',
      onClick: () => setLogoutModalOpen(true),
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
          className={`flex h-16 items-center px-4 transition-all duration-200 ${
            collapsed ? 'justify-center' : 'gap-3'
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
                className="flex cursor-pointer items-center gap-2.5 border-0 bg-transparent px-2.5 py-1.5 rounded-lg hover:bg-slate-100/70 transition-colors select-none text-left"
              >
                <div className="w-8 h-8 rounded-full border border-slate-200 bg-transparent flex items-center justify-center text-xs font-semibold text-slate-700 shrink-0">
                  {initial || <UserOutlined className="text-slate-400" />}
                </div>
                <div className="hidden sm:flex flex-col">
                  <span className="text-xs font-semibold text-gray-800 leading-tight">
                    {displayName}
                  </span>
                  <span className="text-[10px] text-gray-400 font-mono leading-tight">
                    @{user?.username}
                  </span>
                </div>
                <DownOutlined className="text-[10px] text-gray-400 ml-0.5" />
              </button>
            </Dropdown>
          </div>
        </Header>

        {/* Dải nhắc dùng thử, dải hết hạn, chế độ chỉ đọc và hộp thoại gói dịch vụ dùng chung */}
        <SubscriptionLayer />

        <Content className="p-4 md:p-6">
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
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-red-50 text-red-600 flex items-center justify-center shrink-0">
              <LogoutOutlined className="text-base" />
            </div>
            <div>
              <div className="text-base font-semibold text-gray-900">
                Xác nhận đăng xuất
              </div>
              <div className="text-xs text-gray-500 font-normal">
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
        width={440}
        destroyOnClose
      >
        <div className="py-2 text-sm text-gray-600">
          Bạn có chắc chắn muốn đăng xuất khỏi tài khoản{' '}
          <strong className="text-gray-900">{user?.username}</strong>? Mọi tác vụ hoặc thay đổi chưa lưu có thể bị mất.
        </div>
      </Modal>
    </Layout>
  )
}
