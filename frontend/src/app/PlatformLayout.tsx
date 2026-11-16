import AppShell from './AppShell'
import { platformNavigationItems } from './navigation'

/** Tên hiển thị trên thanh trên, ở vị trí tên doanh nghiệp của người dùng doanh nghiệp. */
const PLATFORM_LABEL = 'Quản trị nền tảng'

/**
 * Bố cục khu vực quản trị nền tảng: cùng khung với khu vực doanh nghiệp nhưng chỉ có ba mục menu và không gắn thành
 * phần nào gọi API của doanh nghiệp (chuông cảnh báo tồn kho, WebSocket thông báo, lớp thuê bao, trợ lý AI).
 */
export default function PlatformLayout() {
  return (
    <AppShell
      navigation={platformNavigationItems}
      renderHeaderExtra={() => (
        <span className="hidden max-w-64 truncate text-sm font-medium text-slate-600 md:inline">{PLATFORM_LABEL}</span>
      )}
    />
  )
}
