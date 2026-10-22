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
  tokenType: string
  /** Thời gian sống của token, tính bằng giây */
  expiresIn: number
  username: string
  roles: string[]
}
