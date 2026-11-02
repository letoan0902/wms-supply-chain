import {
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  ContainerOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
  SwapOutlined,
  TeamOutlined,
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
  { path: '/inventory', label: 'Tồn kho', icon: <DatabaseOutlined />, sprint: 3 },
  { path: '/transfers', label: 'Điều chuyển', icon: <SwapOutlined />, sprint: 3 },
  { path: '/purchases', label: 'Mua hàng và nhập kho', icon: <ShoppingCartOutlined />, sprint: 2 },
  { path: '/sales', label: 'Bán hàng và xuất kho', icon: <ShoppingOutlined />, sprint: 3 },
  { path: '/stocktakes', label: 'Kiểm kê', icon: <AuditOutlined />, sprint: 4 },
  { path: '/reports', label: 'Báo cáo', icon: <BarChartOutlined />, sprint: 3 },
  { path: '/users', label: 'Người dùng', icon: <TeamOutlined />, sprint: 2 },
]
