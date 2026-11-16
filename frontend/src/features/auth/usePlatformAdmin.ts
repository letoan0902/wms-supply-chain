import { isPlatformAdmin } from './platformAccess'
import { getPermissionSubject, usePermissionSubject } from './useCan'

/**
 * Người đang đăng nhập có phải quản trị nền tảng hay không (ngoài component, ví dụ trong hàm tiện ích).
 * Đọc claim `roles` của access token trước, không có thì lấy vai trò đã lưu lúc đăng nhập (như useCan.ts).
 */
export function isCurrentUserPlatformAdmin(): boolean {
  return isPlatformAdmin(getPermissionSubject().roles)
}

/** Hook: người đang đăng nhập có phải quản trị nền tảng hay không; tính lại khi token hoặc người dùng đổi. */
export function usePlatformAdmin(): boolean {
  return isPlatformAdmin(usePermissionSubject().roles)
}
