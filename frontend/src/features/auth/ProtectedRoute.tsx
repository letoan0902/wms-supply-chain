import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { getToken } from '../../utils/storage'
import { loginPathWithRedirect } from './loginRedirect'

/** Trang giới thiệu công khai: nơi khách chưa đăng nhập được đưa tới khi mở địa chỉ gốc của hệ thống. */
const LANDING_PATH = '/landing'

/**
 * Chặn truy cập các trang nội bộ khi chưa đăng nhập.
 *  - Mở địa chỉ gốc `/` (khách gõ tên miền): chuyển tới trang giới thiệu, ở đó có nút đăng nhập và đăng ký.
 *  - Mở một trang nội bộ khác: chuyển tới trang đăng nhập và ghi nhớ trang đang vào (đường dẫn và query) để quay lại
 *    sau khi đăng nhập: trong state của router và trong tham số `redirect` (còn giữ được khi người dùng tải lại trang).
 */
export default function ProtectedRoute() {
  const location = useLocation()

  if (!getToken()) {
    if (location.pathname === '/') {
      return <Navigate to={LANDING_PATH} replace />
    }
    return <Navigate to={loginPathWithRedirect(location)} replace state={{ from: location }} />
  }
  return <Outlet />
}
