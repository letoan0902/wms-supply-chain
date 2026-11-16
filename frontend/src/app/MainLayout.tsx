import NotificationBell from '../features/realtime/components/NotificationBell'
import { useWebSocket } from '../features/realtime/useWebSocket'
import { navigationItems } from './navigation'
import SubscriptionLayer from '../features/subscription/components/SubscriptionLayer'
import { useSubscriptionStore } from '../features/subscription/subscriptionStore'
import AiChatWidget from '../features/ai/components/AiChatWidget'
import AppShell from './AppShell'

const resetSubscription = () => useSubscriptionStore.getState().reset()

/**
 * Bố cục khu vực nghiệp vụ của doanh nghiệp: khung chung (AppShell) cộng các thành phần gọi API của doanh nghiệp:
 * tên doanh nghiệp và chuông cảnh báo tồn kho trên thanh trên, WebSocket thông báo, lớp thuê bao, trợ lý AI.
 * Quản trị nền tảng không bao giờ thấy bố cục này (xem AuthenticatedLayout và PlatformLayout).
 */
export default function MainLayout() {
  // Nối WebSocket nhận thông báo thời gian thực (MainLayout chỉ hiển thị sau khi đăng nhập)
  useWebSocket()

  return (
    <AppShell
      navigation={navigationItems}
      renderHeaderExtra={(user) => (
        <>
          {user?.company?.name && (
            <span className="hidden max-w-64 truncate text-sm font-medium text-slate-600 md:inline" title={user.company.name}>
              {user.company.name}
            </span>
          )}
          <NotificationBell />
        </>
      )}
      // Dải nhắc dùng thử, dải hết hạn, chế độ chỉ đọc và hộp thoại gói dịch vụ dùng chung
      beforeContent={<SubscriptionLayer />}
      // Nút và khung chat trợ lý AI nổi ở góc phải dưới của mọi trang
      floating={<AiChatWidget />}
      onLoggedOut={resetSubscription}
    />
  )
}
