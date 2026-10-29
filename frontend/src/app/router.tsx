import { lazy } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import LoginPage from '../features/auth/LoginPage'
import ProtectedRoute from '../features/auth/ProtectedRoute'
import NotFoundPage from '../pages/NotFoundPage'
import PlaceholderPage from '../pages/PlaceholderPage'
import MainLayout from './MainLayout'
import { navigationItems } from './navigation'

// Các chức năng chưa phát triển dùng trang giữ chỗ, thay bằng trang thật khi hoàn thành
const placeholderRoutes = navigationItems
  .filter((item) => item.sprint !== undefined)
  .map((item) => ({
    path: item.path.slice(1),
    element: <PlaceholderPage title={item.label} sprint={item.sprint!} />,
  }))

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
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
          ...placeholderRoutes,
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
