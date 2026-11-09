export interface LoginRequest {
  username: string
  password: string
}

/** Thông tin người dùng hiện tại, lưu ở localStorage để hiển thị trên giao diện. */
export interface AuthUser {
  username: string
  roles: string[]
  fullName?: string
  email?: string
  warehouseIds?: number[]
}

/** Doanh nghiệp của người dùng, trả trong `company` của kết quả đăng nhập và của GET /api/auth/me. */
export interface CompanySummary {
  id: number
  code: string
  name: string
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
  company?: CompanySummary
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
