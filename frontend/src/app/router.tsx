import { lazy, Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import PageLoading from '../components/PageLoading'
import ProtectedRoute from '../features/auth/ProtectedRoute'
import NotFoundPage from '../pages/NotFoundPage'
import PlaceholderPage from '../pages/PlaceholderPage'
import MainLayout from './MainLayout'
import { navigationItems } from './navigation'

const LoginPage = lazy(() => import('../features/auth/LoginPage'))
const ForgotPasswordPage = lazy(() => import('../features/auth/ForgotPasswordPage'))

// Các chức năng chưa phát triển dùng trang giữ chỗ, thay bằng trang thật khi hoàn thành
const placeholderRoutes = navigationItems
  .filter((item) => item.sprint !== undefined)
  .map((item) => ({
    path: item.path.slice(1),
    element: <PlaceholderPage title={item.label} sprint={item.sprint!} />,
  }))

export const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <Suspense fallback={<PageLoading fullScreen tip="Đang tải trang đăng nhập..." />}>
        <LoginPage />
      </Suspense>
    ),
  },
  {
    path: '/forgot-password',
    element: (
      <Suspense fallback={<PageLoading fullScreen tip="Đang tải trang..." />}>
        <ForgotPasswordPage />
      </Suspense>
    ),
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <MainLayout />,
        children: [
          // Dashboard chứa Recharts (nặng) nên tải theo yêu cầu để giảm kích thước bundle ban đầu
          { index: true, Component: lazy(() => import('../pages/DashboardPage')) },
          // Quản lý đa kho & sơ đồ vị trí ô/kệ (WMS-05, WMS-06 - Sprint 1)
          { path: 'warehouses', Component: lazy(() => import('../features/warehouses/WarehousesPage')) },
          // Quản lý danh mục hàng hóa, SKU & quy đổi đơn vị (WMS-07 - Sprint 1)
          { path: 'products', Component: lazy(() => import('../features/products/ProductsPage')) },
          // Quản lý đối tác Nhà cung cấp & Công nợ (WMS-08.3 - Sprint 1 & 2)
          { path: 'suppliers', Component: lazy(() => import('../features/suppliers/SuppliersPage')) },
          // Quản lý Đơn mua hàng (PO) & Nhập kho (WMS-09 - Sprint 2)
          { path: 'purchases', Component: lazy(() => import('../features/purchases/PurchasesPage')) },
          // Quản lý Người dùng & Phân quyền RBAC (FR01, FR02 - Sprint 2)
          { path: 'users', Component: lazy(() => import('../features/users/UsersPage')) },
          ...placeholderRoutes,
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
