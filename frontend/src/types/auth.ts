export interface LoginRequest {
  username: string
  password: string
}

/** Thông tin người dùng hiện tại, lưu ở localStorage để hiển thị trên giao diện. */
export interface AuthUser {
  username: string
  roles: string[]
  /** Chưa có trong response đăng nhập ở Sprint 1, sẽ bổ sung cùng hồ sơ người dùng (FR02) */
  fullName?: string
}

/** Dữ liệu trả về trong `data` của POST /api/auth/login (vn.wms.auth.dto.LoginResponse). */
export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  /** Thời gian sống của token, tính bằng giây */
  expiresIn: number
  username: string
  roles: string[]
}

/** Payload yêu cầu làm mới Access Token. */
export interface RefreshTokenRequest {
  refreshToken: string
}

/** Dữ liệu trả về trong `data` của POST /api/auth/refresh (vn.wms.auth.dto.RefreshTokenResponse). */
export interface RefreshTokenResponse {
  accessToken: string
  tokenType: string
  /** Thời gian sống của token mới, tính bằng giây */
  expiresIn: number
  refreshToken: string
}
