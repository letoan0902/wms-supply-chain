import type { AuthUser } from '../types/auth'

const TOKEN_KEY = 'wms.accessToken'
const REFRESH_TOKEN_KEY = 'wms.refreshToken'
const USER_KEY = 'wms.user'

// Mọi thao tác localStorage đều bọc try/catch vì trình duyệt có thể chặn truy cập
// (chế độ riêng tư, tắt dữ liệu trang web...). Khi đó ứng dụng coi như chưa đăng nhập.

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
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

export function saveRefreshToken(refreshToken: string): void {
  try {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
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

export function saveAuth(token: string, user: AuthUser, refreshToken?: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    if (refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    }
  } catch {
    // Bỏ qua: không lưu được phiên đăng nhập
  }
}

export function clearAuth(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  } catch {
    // Bỏ qua
  }
}
