import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { getToken } from '../../utils/storage'
import { loginPathWithRedirect } from './loginRedirect'

/**
 * Chặn truy cập các trang nội bộ khi chưa đăng nhập, ghi nhớ trang đang vào (đường dẫn và query) để quay lại sau
 * khi đăng nhập: trong state của router và trong tham số `redirect` (còn giữ được khi người dùng tải lại trang).
 */
export default function ProtectedRoute() {
  const location = useLocation()

  if (!getToken()) {
    return <Navigate to={loginPathWithRedirect(location)} replace state={{ from: location }} />
  }
  return <Outlet />
}
