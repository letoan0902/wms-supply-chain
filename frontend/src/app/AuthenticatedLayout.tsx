import { usePlatformAdmin } from '../features/auth/usePlatformAdmin'
import MainLayout from './MainLayout'
import PlatformLayout from './PlatformLayout'

/**
 * Chọn bố cục theo loại tài khoản: quản trị nền tảng dùng PlatformLayout (không gắn thành phần nào của doanh
 * nghiệp), người dùng doanh nghiệp dùng MainLayout như trước. Trang nào thuộc khu vực nào do AreaGuards quyết định.
 */
export default function AuthenticatedLayout() {
  return usePlatformAdmin() ? <PlatformLayout /> : <MainLayout />
}
