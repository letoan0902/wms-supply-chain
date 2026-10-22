import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { getToken } from '../../utils/storage'

/** Chặn truy cập các trang nội bộ khi chưa đăng nhập, ghi nhớ trang đang vào để quay lại sau khi đăng nhập. */
export default function ProtectedRoute() {
  const location = useLocation()

  if (!getToken()) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <Outlet />
}
