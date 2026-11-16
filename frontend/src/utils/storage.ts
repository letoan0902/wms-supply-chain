import type { AuthUser } from '../types/auth'

const TOKEN_KEY = 'wms.accessToken'
const USER_KEY = 'wms.user'
const LEGACY_REFRESH_TOKEN_KEY = 'wms.refreshToken'

// Mọi thao tác localStorage đều bọc try/catch vì trình duyệt có thể chặn truy cập
// (chế độ riêng tư, tắt dữ liệu trang web...). Khi đó ứng dụng coi như chưa đăng nhập.

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function saveToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    // Bỏ qua
  }
}

export function getStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

export function updateStoredUser(updates: Partial<AuthUser>): void {
  try {
    const current = getStoredUser()
    if (current) {
      const merged = { ...current, ...updates }
      localStorage.setItem(USER_KEY, JSON.stringify(merged))
      window.dispatchEvent(new Event('wms:user-updated'))
    }
  } catch {
    // Bỏ qua
  }
}

/**
 * Lưu Access Token và thông tin người dùng vào localStorage.
 * Theo SRS Mục UC-01: Refresh Token được lưu trong HttpOnly Cookie do backend cấp và trình duyệt tự quản lý,
 * tuyệt đối không lưu Refresh Token trong localStorage để chống tấn công XSS.
 */
export function saveAuth(token: string, user: AuthUser): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    // Xóa khóa refreshToken cũ nếu còn sót lại từ các phiên bản trước
    localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
  } catch {
    // Bỏ qua: không lưu được phiên đăng nhập
  }
}

export function clearAuth(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
  } catch {
    // Bỏ qua
  }
}
