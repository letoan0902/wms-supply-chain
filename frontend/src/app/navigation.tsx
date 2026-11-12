import {
  ApartmentOutlined,
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  ContainerOutlined,
  CreditCardOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
  SwapOutlined,
  TeamOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons'
import type { ReactNode } from 'react'

export interface NavigationItem {
  /** Đường dẫn route, đồng thời là key của mục menu */
  path: string
  label: string
  icon: ReactNode
  /** Sprint dự kiến hoàn thành trang này. Bỏ trống nếu trang đã có nội dung thật. */
  sprint?: number
}

/**
 * Danh sách chức năng của hệ thống: dùng chung cho menu bên trái và router.
 * Số sprint theo kế hoạch trong docs/SPRINT_PLAN.md (sprint bắt đầu làm chức năng đó).
 * Khi một chức năng được làm xong, xóa trường `sprint` và thay PlaceholderPage trong router.tsx.
 */
export const navigationItems: NavigationItem[] = [
  { path: '/', label: 'Dashboard', icon: <DashboardOutlined /> },
  { path: '/products', label: 'Danh mục hàng hóa', icon: <AppstoreOutlined /> },
  { path: '/warehouses', label: 'Kho và vị trí', icon: <ContainerOutlined /> },
  { path: '/suppliers', label: 'Nhà cung cấp', icon: <ShopOutlined /> },
  { path: '/customers', label: 'Khách hàng', icon: <UsergroupAddOutlined /> },
  { path: '/inventory', label: 'Tồn kho', icon: <DatabaseOutlined /> },
  { path: '/transfers', label: 'Điều chuyển', icon: <SwapOutlined /> },
  { path: '/purchases', label: 'Mua hàng và nhập kho', icon: <ShoppingCartOutlined /> },
  { path: '/sales', label: 'Bán hàng và xuất kho', icon: <ShoppingOutlined /> },
  { path: '/stocktakes', label: 'Kiểm kê', icon: <AuditOutlined />, sprint: 4 },
  { path: '/reports', label: 'Báo cáo', icon: <BarChartOutlined /> },
  { path: '/reports/lot-trace', label: 'Truy vết theo lô', icon: <ApartmentOutlined /> },
  { path: '/users', label: 'Người dùng', icon: <TeamOutlined /> },
  { path: '/subscription', label: 'Gói dịch vụ', icon: <CreditCardOutlined /> },
  { path: '/settings/api-keys', label: 'API key', icon: <ApiOutlined /> },
]
