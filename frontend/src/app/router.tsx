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
const LandingPage = lazy(() => import('../features/landing/LandingPage'))
const MockCheckoutPage = lazy(() => import('../features/subscription/MockCheckoutPage'))

// Các chức năng chưa phát triển dùng trang giữ chỗ, thay bằng trang thật khi hoàn thành
const placeholderRoutes = navigationItems
  .filter((item) => item.sprint !== undefined)
  .map((item) => ({
    path: item.path.slice(1),
    element: <PlaceholderPage title={item.label} sprint={item.sprint!} />,
  }))

export const router = createBrowserRouter([
  {
    path: '/landing',
    element: (
      <Suspense fallback={<PageLoading fullScreen tip="Đang tải trang giới thiệu..." />}>
        <LandingPage />
      </Suspense>
    ),
  },
  {
    path: '/pricing',
    element: (
      <Suspense fallback={<PageLoading fullScreen tip="Đang tải bảng giá dịch vụ..." />}>
        <LandingPage />
      </Suspense>
    ),
  },
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
    path: '/',
    element: <ProtectedRoute />,
    children: [
      {
        // Trang thanh toán giả lập (PAYOS_MOCK=true) đứng riêng, không có menu, giống một cổng thanh toán
        path: 'payment/mock',
        element: (
          <Suspense fallback={<PageLoading fullScreen tip="Đang tải trang thanh toán..." />}>
            <MockCheckoutPage />
          </Suspense>
        ),
      },
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
          // Quản lý Khách hàng / Đại lý & Cảnh báo hạn mức công nợ (WMS-08.4 - FR14)
          { path: 'customers', Component: lazy(() => import('../features/customers/CustomersPage')) },
          // Quản lý Đơn mua hàng (PO) & Nhập kho (WMS-09 - Sprint 2)
          { path: 'purchases', Component: lazy(() => import('../features/purchases/PurchasesPage')) },
          // Quản lý Đơn bán hàng (SO) (WMS-11 - Sprint 3)
          { path: 'sales', Component: lazy(() => import('../features/sales/SalesPage')) },
          { path: 'transfers', Component: lazy(() => import('../features/transfers/TransfersPage')) },
          // Quản lý Người dùng & Phân quyền RBAC (FR01, FR02 - Sprint 2)
          { path: 'users', Component: lazy(() => import('../features/users/UsersPage')) },
          // Báo cáo Nhập-Xuất-Tồn kho & Thống kê (UC-10 - SRS Mục 2.5)
          { path: 'reports', Component: lazy(() => import('../pages/ReportsPage')) },
          // API key cho hệ thống ngoài (chỉ ADMIN)
          { path: 'settings/api-keys', Component: lazy(() => import('../features/apiKeys/ApiKeysPage')) },
          // Hồ sơ cá nhân & Đổi mật khẩu
          { path: 'profile', Component: lazy(() => import('../features/profile/ProfilePage')) },
          // Gói dịch vụ, mức dùng và đổi gói của doanh nghiệp
          { path: 'subscription', Component: lazy(() => import('../features/subscription/SubscriptionPage')) },
          // Kết quả thanh toán: PAYOS_RETURN_URL và PAYOS_CANCEL_URL trỏ về đây
          { path: 'payment/result', Component: lazy(() => import('../features/subscription/PaymentResultPage')) },
          ...placeholderRoutes,
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
