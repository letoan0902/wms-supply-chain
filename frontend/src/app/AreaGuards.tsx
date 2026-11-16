import { Navigate, Outlet } from 'react-router-dom'
import ForbiddenResult from '../components/ForbiddenResult'
import { PLATFORM_HOME } from '../features/auth/platformAccess'
import { usePlatformAdmin } from '../features/auth/usePlatformAdmin'

/**
 * Trang nghiệp vụ của doanh nghiệp (`/`, `/products`, `/subscription`, ...): quản trị nền tảng được chuyển về trang chủ
 * khu vực nền tảng, không mở trang nào gọi API của doanh nghiệp.
 */
export function CompanyArea() {
  return usePlatformAdmin() ? <Navigate to={PLATFORM_HOME} replace /> : <Outlet />
}

/** Trang khu vực nền tảng (`/platform/**`): người dùng doanh nghiệp thấy trang không có quyền sẵn có. */
export function PlatformArea() {
  return usePlatformAdmin() ? (
    <Outlet />
  ) : (
    <ForbiddenResult description="Khu vực quản trị nền tảng chỉ dành cho quản trị viên nền tảng." />
  )
}
