/**
 * Khu vực quản trị nền tảng: nhận biết tài khoản quản trị nền tảng và quyết định điều hướng giữa khu vực nền tảng
 * (`/platform/**`) và khu vực nghiệp vụ của doanh nghiệp.
 *
 * Tệp không phụ thuộc React hay localStorage (cùng cách với permissions.ts); hook đọc phiên đăng nhập ở
 * usePlatformAdmin.ts. Giao diện chỉ dùng các hàm này để chọn bố cục và chuyển trang; backend vẫn là nơi quyết định
 * và trả 403 khi vai trò không được gọi API.
 */
import type { PlatformRole } from './permissions'

/** Vai trò quản trị nền tảng trong claim `roles` của access token và trong kết quả đăng nhập. */
export const PLATFORM_ADMIN_ROLE: PlatformRole = 'SUPER_ADMIN'

/** Trang chủ của khu vực nền tảng: quản trị nền tảng luôn vào đây sau khi đăng nhập. */
export const PLATFORM_HOME = '/platform'

/** Danh sách vai trò có vai trò quản trị nền tảng hay không (chấp nhận cả dạng có tiền tố `ROLE_`). */
export function isPlatformAdmin(roles: readonly string[] | null | undefined): boolean {
  return (roles ?? []).some((role) => role.replace(/^ROLE_/, '') === PLATFORM_ADMIN_ROLE)
}

/** Bỏ query string và hash: `/platform/companies?page=2` thành `/platform/companies`. */
function pathOf(target: string): string {
  return target.split(/[?#]/)[0] || '/'
}

/** Đường dẫn thuộc khu vực nền tảng (`/platform` và mọi trang con). */
export function isPlatformPath(target: string): boolean {
  const path = pathOf(target)
  return path === PLATFORM_HOME || path.startsWith(`${PLATFORM_HOME}/`)
}

/**
 * Trang mở sau khi đăng nhập.
 * - Quản trị nền tảng: trang nền tảng đã yêu cầu (ví dụ phiên hết hạn khi đang ở `/platform/payments`), còn lại luôn
 *   là {@link PLATFORM_HOME}; tham số `redirect` trỏ tới trang nghiệp vụ của doanh nghiệp bị bỏ qua.
 * - Người dùng doanh nghiệp: giữ nguyên cách cũ, trang đã yêu cầu hoặc trang chủ.
 *
 * `requested` phải là đường dẫn nội bộ đã kiểm tra bằng `safeRedirectPath` (hoặc `null`).
 */
export function postLoginPath(roles: readonly string[] | null | undefined, requested: string | null): string {
  if (isPlatformAdmin(roles)) {
    return requested && isPlatformPath(requested) ? requested : PLATFORM_HOME
  }
  return requested ?? '/'
}
